/*
 * Tigase MUC - Multi User Chat component for Tigase
 * Copyright (C) 2007 Tigase, Inc. (office@tigase.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. Look for COPYING file in the top folder.
 * If not, see http://www.gnu.org/licenses/.
 */
package tigase.muc.repository;

import tigase.db.util.importexport.*;
import tigase.kernel.core.Kernel;
import tigase.muc.*;
import tigase.muc.history.AbstractHistoryProvider;
import tigase.muc.history.ExtendedMAMRepository;
import tigase.server.Message;
import tigase.util.Algorithms;
import tigase.util.Base64;
import tigase.util.datetime.TimestampHelper;
import tigase.util.ui.console.CommandlineParameter;
import tigase.xml.Element;
import tigase.xml.XMLUtils;
import tigase.xmpp.jid.BareJID;
import tigase.xmpp.jid.JID;
import tigase.xmpp.mam.MAMRepository;
import tigase.xmpp.mam.util.MAMRepositoryManagerExtensionHelper;

import java.io.Writer;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static tigase.db.util.importexport.Exporter.EXPORT_MAM_BATCH_SIZE;
import static tigase.db.util.importexport.Exporter.EXPORT_MAM_SINCE;
import static tigase.db.util.importexport.RepositoryManager.isSet;

public class MUCRepositoryManagerExtension extends RepositoryManagerExtensionBase {

	private static TimestampHelper TIMESTAMP_FORMATTER = new TimestampHelper();
	private static final Logger log = Logger.getLogger(MUCRepositoryManagerExtension.class.getSimpleName());

	private final CommandlineParameter INCLUDE_MUC = new CommandlineParameter.Builder(null, "include-muc").type(Boolean.class)
			.description("Include MUC component data")
			.defaultValue("false")
			.requireArguments(false)
			.build();

	@Override
	public Stream<CommandlineParameter> getExportParameters() {
		return Stream.concat(super.getExportParameters(), Stream.of(INCLUDE_MUC, EXPORT_MAM_SINCE, EXPORT_MAM_BATCH_SIZE));
	}

	@Override
	public Stream<CommandlineParameter> getImportParameters() {
		return Stream.concat(super.getImportParameters(), Stream.of(INCLUDE_MUC));
	}

	@Override
	public void initialize(Kernel kernel, DataSourceHelper dataSourceHelper,
						   RepositoryHolder repositoryHolder, Path rootPath) {
		super.initialize(kernel, dataSourceHelper, repositoryHolder, rootPath);
		repositoryHolder.registerPrepFn(JDBCMucDAO.class, mucDAO -> {
			try {
				Field f = JDBCMucDAO.class.getDeclaredField("mucConfig");
				f.setAccessible(true);
				f.set(mucDAO, new MUCConfig());
				f = JDBCMucDAO.class.getDeclaredField("roomFactory");
				f.setAccessible(true);
				f.set(mucDAO, new Room.RoomFactoryImpl());
			} catch (Throwable ex) {
				throw new RuntimeException(ex);
			}
			return mucDAO;
		});
	}

	@Override
	public void exportDomainData(String domain, Writer domainWriter) throws Exception {
		if (isSet(INCLUDE_MUC)) {
			log.info("exporting MUC component data...");
			Path mucPath = getRootPath().resolve("muc." + domain + ".xml");
			exportInclude(domainWriter, mucPath, writer -> {
				writer.append("<muc xmlns='tigase:xep-0227:muc:0'>\n");
				IMucDAO mucDAO = getRepository(IMucDAO.class, domain);
				AbstractHistoryProvider historyProvider = getRepository(AbstractHistoryProvider.class, domain);
				for (BareJID roomJid : (List<BareJID>) mucDAO.getRoomsJIDList()) {
					if (!roomJid.getDomain().endsWith(domain)) {
						continue;
					}
					log.fine("exporting " + roomJid + " data...");
					RoomWithId room = mucDAO.getRoom(roomJid);
					if (room != null && room.getId() != null) {
						Path roomPath = mucPath.resolveSibling(roomJid.getDomain())
								.resolve(roomJid.getLocalpart() + ".xml");
						exportInclude(writer, roomPath, roomWriter -> {
							exportRoom(mucDAO, historyProvider, roomJid, room, roomWriter);
						});
					}
				}
				writer.append("</muc>");
			});
		}
	}

	private void exportRoom(IMucDAO mucDAO, AbstractHistoryProvider historyProvider, BareJID roomJid, RoomWithId room, Writer writer) throws Exception {
		writer.append("<room jid=\"").append(roomJid.toString()).append("\"");
		if (room.getCreationDate() != null) {
			writer.append(" createdAt=\"").append(TIMESTAMP_FORMATTER.format(room.getCreationDate())).append("\"");
		}
		if (room.getCreatorJid() != null) {
			writer.append(" createdBy=\"").append(room.getCreatorJid().toString()).append("\"");
		}
		writer.append(">");
		writer.append("<config>");
		RoomConfig config = room.getConfig();
		writer.append(config.getAsElement().toString());
		writer.append("</config>");
		Map<BareJID,RoomAffiliation> affs = mucDAO.getAffiliations(room);
		if (affs != null && !affs.isEmpty()) {
			writer.append("<affiliations>");
			for (Map.Entry<BareJID, RoomAffiliation> e : affs.entrySet()) {
				RoomAffiliation aff = e.getValue();
				writer.append("<affiliation jid=\"").append(e.getKey().toString()).append("\" affiliation=\"").append(aff.getAffiliation().name()).append("\"");
				if (aff.getRegisteredNickname() != null) {
					writer.append(" nick=\"").append(XMLUtils.escape(aff.getRegisteredNickname())).append("\"");
				}
				writer.append(" persistent=\"").append(String.valueOf(aff.isPersistentOccupant())).append("\"/>");
			}
			writer.append("</affiliations>");
		}
		String encodedAvatar = mucDAO.getRoomAvatar(room);
		if (encodedAvatar != null && encodedAvatar.length() > 0) {
			final Element vCard = new Element("vCard", new String[]{"xmlns"}, new String[]{"vcard-temp"});
			String[] items = encodedAvatar.split(";");
			Element photo = new Element("PHOTO");
			photo.addChild(new Element("TYPE", items[0]));
			photo.addChild(new Element("BINVAL", items[1]));
			vCard.addChild(photo);
			writer.append(vCard.toString());
		}
		if (historyProvider instanceof MAMRepository<?,?> mamRepository) {
			log.info("exporting room " + roomJid + " history...");
			MAMRepositoryManagerExtensionHelper.exportDataFromRepository(mamRepository, roomJid, roomJid, (mamItem, result) -> {
				ExtendedMAMRepository.Item item = (ExtendedMAMRepository.Item) mamItem;
				JID sender = item.getSenderJID();
				if (sender != null) {
					result.addAttribute("sender", sender.toString());
				}
			}, writer);
		}
		writer.append("</room>");
	}

	@Override
	public void exportUserData(Path userDirPath, BareJID user, Writer writer) throws Exception {
	}

	@Override
	public tigase.db.util.importexport.ImporterExtension startImportDomainData(String domain, String name,
																			   Map<String, String> attrs) throws Exception {
		if (!"muc".equals(name)) {
			return null;
		}
		if (!"tigase:xep-0227:muc:0".equals(attrs.get("xmlns"))) {
			return null;
		}
		IMucDAO mucDAO = getRepository(IMucDAO.class, domain);
		AbstractHistoryProvider historyProvider = getRepository(AbstractHistoryProvider.class, domain);
		return new ImporterExtension(mucDAO, historyProvider, isSet(INCLUDE_MUC));
	}

	private static class ImporterExtension extends AbstractImporterExtension {

		private final AbstractHistoryProvider historyProvider;
		private final boolean includeMUC;
		private final Room.RoomFactory roomFactory = new Room.RoomFactoryImpl();

		private final IMucDAO mucDAO;

		enum State {
			root,
			room,
			affiliations,
			archive
		}

		private State state = State.root;
		private BareJID room;
		private Date createdAt;
		private BareJID createdBy;

		private tigase.db.util.importexport.ImporterExtension activeExtension;

		private ImporterExtension(IMucDAO mucDAO, AbstractHistoryProvider historyProvider, boolean includeMUC) {
			this.mucDAO = mucDAO;
			this.includeMUC = includeMUC;
			this.historyProvider = historyProvider;
			if (includeMUC) {
				log.info("importing MUC component data...");
			}
		}

		private int depth = 0;

		@Override
		public boolean startElement(String name, Map<String, String> attrs) throws Exception {
			if (!includeMUC) {
				depth++;
				return true;
			}
			if (activeExtension != null) {
				return activeExtension.startElement(name, attrs);
			}
			return switch (state) {
				case root -> {
					if ("room".equals(name)) {
						room = BareJID.bareJIDInstance(attrs.get("jid"));
						createdAt = Optional.ofNullable(attrs.get("createdAt")).map(this::parseTimestamp).orElseThrow();
						createdBy = Optional.ofNullable(attrs.get("createdBy")).map(BareJID::bareJIDInstanceNS).orElseThrow();
						state = State.room;
						yield true;
					}
					yield false;
				}
				case room -> switch (name) {
					case "archive" -> {
						RoomWithId r = mucDAO.getRoom(room);
						activeExtension = new HistoryImporterExtension(historyProvider, r);
						state = State.archive;
						yield true;
					}
					case "affiliations" -> {
						state = State.affiliations;
						yield true;
					}
					default -> false;
				};
				case affiliations -> {
					if ("affiliation".equals(name)) {
						RoomWithId r = mucDAO.getRoom(room);
						BareJID jid = BareJID.bareJIDInstance(attrs.get("jid"));
						Affiliation affiliation = Affiliation.valueOf(attrs.get("affiliation"));
						String registeredNickname = XMLUtils.unescape(attrs.get("nick"));
						boolean persistent = Boolean.parseBoolean(attrs.get("persistent"));
						mucDAO.setAffiliation(r, jid, RoomAffiliation.from(affiliation, persistent, registeredNickname));
						yield true;
					}
					yield false;
				}
				default -> false;
			};
		}

		@Override
		public boolean handleElement(Element element) throws Exception {
			if (activeExtension != null && activeExtension.handleElement(element)) {
				return true;
			}
			return switch (element.getName()) {
				case "config" -> {
					if (state != State.room) {
						yield false;
					}
					log.fine("creating room " + room + "...");
					Element x = Optional.ofNullable(element.findChild(
							el -> "x".equals(el.getName()) && "jabber:x:data".equals(el.getXMLNS()))).orElseThrow();
					if (mucDAO.getRoom(room) == null){
						RoomConfig rc = new RoomConfig(room);
						rc.readFromElement(x);
						RoomWithId<?> r = roomFactory.newInstance(null, rc, createdAt, createdBy);
						log.info("creating room " + room + "...");
						mucDAO.createRoom(r);
					} else {
						log.info("room " + room + " already exist, updating room configuration...");
						RoomConfig rc = new RoomConfig(room);
						rc.readFromElement(x);
						mucDAO.updateRoomConfig(rc);
					}
					yield true;
				}
				case "vCard" -> {
					if (!"vcard-temp".equals(element.getXMLNS())) {
						yield false;
					}
					log.finest("setting room " + room + " vcard...");
					Element photoEl = element.getChild("PHOTO");
					if (photoEl != null) {
						Optional<String> typeOpt = Optional.ofNullable(photoEl.getChild("TYPE")).map(Element::getCData);
						Optional<String> binvalOpt = Optional.ofNullable(photoEl.getChild("BINVAL")).map(Element::getCData);
						typeOpt.ifPresent(type -> {
							binvalOpt.ifPresent(binval -> {
								try {
									RoomWithId<?> r = mucDAO.getRoom(room);
									byte[] data = Base64.decode(binval);
									MessageDigest sha = MessageDigest.getInstance("SHA-1");
									String hash = Algorithms.bytesToHex(sha.digest(data));
									mucDAO.updateRoomAvatar(r, type + ";" + binval, hash);
								} catch (Exception ex) {
									throw new RuntimeException(ex);
								}
							});
						});
					}
					yield true;
				}
				default -> false;
			};
		}

		@Override
		public boolean endElement(String name) throws Exception {
			if (!includeMUC) {
				boolean inside = depth > 0;
				depth--;
				return inside;
			}
			if (activeExtension != null && activeExtension.endElement(name)) {
				return true;
			}
			return switch (state) {
				case root -> {
					yield false;
				}
				case room -> switch (name) {
					case "room" -> {
						state = State.root;
						log.finest("finished importing room " + room);
						yield true;
					}
					default -> false;
				};
				case archive -> switch (name) {
					case "archive" -> {
						log.finest("finished importing room " + room + " history");
						activeExtension.close();
						activeExtension = null;
						state = State.room;
						yield true;
					}
					default -> false;
				};
				case affiliations -> switch (name) {
					case "affiliations" -> {
						state = State.room;
						yield true;
					}
					case "affiliation" -> true;
					default -> false;
				};
			};
		}
	}

	private static class HistoryImporterExtension extends MAMRepositoryManagerExtensionHelper.AbstractImporterExtension {

		private final AbstractHistoryProvider historyProvider;
		private final Room room;

		private HistoryImporterExtension(AbstractHistoryProvider historyProvider, Room room) {
			this.historyProvider = historyProvider;
			this.room = room;
			log.info("importing room " + room.getRoomJID() + " history...");
		}

		@Override
		protected boolean handleMessage(Message message, String stableId, Date timestamp, Element source) throws Exception {
			String senderNickname = message.getStanzaFrom().getResource();
			JID senderJid = JID.jidInstanceNS(source.getAttributeStaticStr("sender"));
			message.initVars(null,null);
			Element body = message.getElemChild("body");
			if (historyProvider instanceof ExtendedMAMRepository<?,?> extendedMAMRepository) {
				if (extendedMAMRepository.getItem(room.getRoomJID(), stableId) != null) {
					log.finest("found existing message for room " + room.getRoomJID()  + " with id = " + stableId + ", skipping insert");
					return true;
				}
			}
			historyProvider.addMessage(room, message.getElement(), body == null ? null : body.getCData(), senderJid, senderNickname, timestamp, stableId);
			return true;
		}
	}
}