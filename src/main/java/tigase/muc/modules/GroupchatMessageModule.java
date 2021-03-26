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
package tigase.muc.modules;

import tigase.criteria.Criteria;
import tigase.criteria.ElementCriteria;
import tigase.kernel.beans.Bean;
import tigase.kernel.beans.Inject;
import tigase.muc.Affiliation;
import tigase.muc.MUCConfig;
import tigase.muc.Role;
import tigase.muc.Room;
import tigase.muc.exceptions.MUCException;
import tigase.muc.history.HistoryProvider;
import tigase.muc.logger.MucLogger;
import tigase.muc.repository.IMucRepository;
import tigase.server.Packet;
import tigase.util.datetime.TimestampHelper;
import tigase.util.stringprep.TigaseStringprepException;
import tigase.xml.Element;
import tigase.xmpp.Authorization;
import tigase.xmpp.jid.BareJID;
import tigase.xmpp.jid.JID;

import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.util.*;
import java.util.logging.Level;

/**
 * @author bmalkow
 */
@Bean(name = GroupchatMessageModule.ID, active = true)
public class GroupchatMessageModule
		extends AbstractMucModule {

	public static final String ID = "groupchat";
	private static final Criteria CRIT = ElementCriteria.nameType("message", "groupchat");
	private static final Criteria CRIT_CHAT_STAT = ElementCriteria.xmlns("http://jabber.org/protocol/chatstates");
	private static final Charset UTF8 = Charset.forName("UTF-8");
	private final Set<Criteria> allowedElements = new HashSet<Criteria>();

	@Inject
	private MUCConfig config;

	@Inject
	private HistoryProvider historyProvider;

	@Inject(nullAllowed = true)
	private MucLogger mucLogger;

	@Inject
	private IMucRepository repository;

	private final TimestampHelper timestampHelper = new TimestampHelper();

	public static String generateSubjectId(Date ts, String subject) {
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-1");
			md.update(String.valueOf(ts.getTime() / 100).getBytes());
			if (subject != null) {
				md.update(subject.getBytes());
			}
			StringBuilder sb = new StringBuilder();
			for (byte b : md.digest()) {
				sb.append(Character.forDigit((b & 0xF0) >> 4, 16));
				sb.append(Character.forDigit(b & 0xF, 16));
			}
			return sb.toString();
		} catch (NoSuchAlgorithmException ex) {
			return null;
		}
	}

	public GroupchatMessageModule() {
		allowedElements.add(ElementCriteria.name("oob", "jabber:x:oob"));
	    allowedElements.add(ElementCriteria.name("encrypted", "eu.siacs.conversations.axolotl"));
	}

	@Override
	public String[] getFeatures() {
		ArrayList<String> f = new ArrayList<String>();

		f.add("http://jabber.org/protocol/muc");
		f.add("http://jabber.org/protocol/muc#stable_id");

		if (isChatStateAllowed()) {
			f.add("http://jabber.org/protocol/chatstates");
		}

		return f.toArray(new String[]{});
	}

	@Override
	public Criteria getModuleCriteria() {
		return CRIT;
	}

	public boolean isChatStateAllowed() {
		return allowedElements.contains(CRIT_CHAT_STAT);
	}

	@Override
	public void process(Packet packet) throws MUCException {
		try {
			final JID senderJID = JID.jidInstance(packet.getAttributeStaticStr(Packet.FROM_ATT));
			final BareJID roomJID = BareJID.bareJIDInstance(packet.getAttributeStaticStr(Packet.TO_ATT));

			if (getNicknameFromJid(JID.jidInstance(packet.getAttributeStaticStr(Packet.TO_ATT))) != null) {
				throw new MUCException(Authorization.BAD_REQUEST, "Groupchat message can't be addressed to occupant.");
			}

			final Room room = repository.getRoom(roomJID);

			if (room == null) {
				throw new MUCException(Authorization.ITEM_NOT_FOUND, "There is no such room.");
			}

			final String nickName = room.getOccupantsNickname(senderJID);
			final Role role = room.getRole(nickName);
			final Affiliation affiliation = room.getAffiliation(senderJID.getBareJID()).getAffiliation();

			if (log.isLoggable(Level.FINEST)) {
				log.finest("Processing groupchat message. room=" + roomJID + "; senderJID=" + senderJID +
								   "; senderNickname=" + nickName + "; role=" + role + "; affiliation=" + affiliation +
								   ";");
			}

			if (!role.isSendMessagesToAll() || (room.getConfig().isRoomModerated() && (role == Role.visitor))) {
				if (log.isLoggable(Level.FINE)) {
					log.fine("Insufficient privileges to send grouchat message: role=" + role + "; roomModerated=" +
									 room.getConfig().isRoomModerated() + "; stanza=" +
									 packet.getElement().toStringNoChildren());
				}
				throw new MUCException(Authorization.FORBIDDEN, "Insufficient privileges to send groupchat message.");
			}

			String xmlLang = packet.getElement().getAttributeStaticStr("xml:lang");
			Element body = null;
			Element subject = null;
			Element delay = null;
			String id = packet.getAttributeStaticStr(Packet.ID_ATT);
			ArrayList<Element> content = new ArrayList<Element>();
			List<Element> ccs = packet.getElement().getChildren();

			if (ccs != null) {
				for (Element c : ccs) {
					if ("delay".equals(c.getName())) {
						delay = c;
					} else if ("body".equals(c.getName())) {
						body = c;
						content.add(c);
					} else if ("subject".equals(c.getName())) {
						subject = c;
						content.add(c);
					} else if (!config.isMessageFilterEnabled()) {
						content.add(c);
					} else if (config.isChatStateAllowed() && CRIT_CHAT_STAT.match(c)) {
						content.add(c);
					} else {
						for (Criteria crit : allowedElements) {
							if (crit.match(c)) {
								content.add(c);

								break;
							}
						}
					}
				}
			}

			final JID senderRoomJID = JID.jidInstance(roomJID, nickName);

			Date sendDate;

			if ((delay != null) && (affiliation == Affiliation.owner)) {
				try {
					sendDate = timestampHelper.parseTimestamp(delay.getAttributeStaticStr("stamp"));
				} catch (ParseException ex) {
					throw new MUCException(Authorization.BAD_REQUEST, "Invalid format of attribute stamp");
				}
			} else {
				sendDate = new Date();
			}

			if (subject != null) {
				if (!(room.getConfig().isChangeSubject() && (role == Role.participant)) && !role.isModifySubject()) {
					if (log.isLoggable(Level.FINE)) {
						log.fine("Insufficient privileges to change subject: role=" + role + "; allowToChangeSubject=" +
										 room.getConfig().isChangeSubject() + "; stanza=" +
										 packet.getElement().toStringNoChildren());
					}
					throw new MUCException(Authorization.FORBIDDEN, "Insufficient privileges to change subject.");
				}

				String msg = subject.getCData();

				room.setNewSubject(msg, nickName);
				room.setSubjectChangeDate(sendDate);
			}

			if (id == null && config.isAddMessageIdIfMissing()) {
				if (subject != null) {
					id = generateSubjectId(sendDate, subject == null ? "" : subject.getCData());
				} else {
					id = UUID.randomUUID().toString();
				}
			}

			Packet msg = preparePacket(id, xmlLang, content.toArray(new Element[]{}));

			if (body != null) {
				addMessageToHistory(room, msg.getElement(), body.getCData(), senderJID, nickName, sendDate);
			}
			if (subject != null) {
				addSubjectChangeToHistory(room, msg.getElement(), subject.getCData(), senderJID, nickName, sendDate);
			}

			sendMessagesToAllOccupants(room, senderRoomJID, msg);
		} catch (MUCException e1) {
			throw e1;
		} catch (TigaseStringprepException e) {
			throw new MUCException(Authorization.BAD_REQUEST);
		} catch (Exception e) {
			log.log(Level.FINEST, "Error during processing groupchat message", e);
			throw new RuntimeException(e);
		}
	}

	public void sendMessagesToAllOccupants(final Room room, final JID fromJID, final Element... content)
			throws TigaseStringprepException {
		Packet msg = preparePacket(null, null, content);
		sendMessagesToAllOccupants(room, fromJID, msg);
	}

	public void sendMessagesToAllOccupants(final Room room, final JID fromJID, String xmlLang, final Element... content)
			throws TigaseStringprepException {
		Packet msg = preparePacket(null, xmlLang, content);
		sendMessagesToAllOccupants(room, fromJID, msg);
	}

	public void sendMessagesToAllOccupants(final Room room, final JID fromJID, final Packet msg)
			throws TigaseStringprepException {
		sendMessagesToAllOccupantsJids(room, fromJID, msg);

		room.fireOnMessageToOccupants(fromJID, msg);
	}

	public void sendMessagesToAllOccupantsJids(final Room room, final JID fromJID, final Packet msg)
			throws TigaseStringprepException {

		room.getAllJidsForMessageDelivery().forEach(jid -> {
			Packet message = msg.copyElementOnly();
			message.initVars(fromJID, jid);
			message.setXMLNS(Packet.CLIENT_XMLNS);

			write(message);
		});
	}

	protected void addMessageToHistory(Room room, final Element message, String body, JID senderJid,
									   String senderNickname, Date time) {
		try {
			if (historyProvider != null) {
				historyProvider.addMessage(room, message, body, senderJid, senderNickname, time);
			}
		} catch (Exception e) {
			if (log.isLoggable(Level.WARNING)) {
				log.log(Level.WARNING, "Can't add message to history!", e);
			}
		}
		try {
			if ((mucLogger != null) && room.getConfig().isLoggingEnabled()) {
				mucLogger.addMessage(room, body, senderJid, senderNickname, time);
			}
		} catch (Exception e) {
			if (log.isLoggable(Level.WARNING)) {
				log.log(Level.WARNING, "Can't add message to log!", e);
			}
		}
	}

	protected void addSubjectChangeToHistory(Room room, Element message, final String subject, JID senderJid,
											 String senderNickname, Date time) {
		try {
			if (historyProvider != null) {
				historyProvider.addSubjectChange(room, message, subject, senderJid, senderNickname, time);
			}
		} catch (Exception e) {
			if (log.isLoggable(Level.WARNING)) {
				log.log(Level.WARNING, "Can't add subject change to history!", e);
			}
		}

		try {
			if ((mucLogger != null) && room.getConfig().isLoggingEnabled()) {
				mucLogger.addSubjectChange(room, subject, senderJid, senderNickname, time);
			}
		} catch (Exception e) {
			if (log.isLoggable(Level.WARNING)) {
				log.log(Level.WARNING, "Can't add subject change to log!", e);
			}
		}

	}

	protected Packet preparePacket(String messageId, String xmlLang, Element... content)
			throws TigaseStringprepException {
		Element e = new Element("message", new String[]{"type"}, new String[]{"groupchat"});
		if (messageId != null) {
			e.setAttribute("id", messageId);
		}
		if (xmlLang != null) {
			e.setAttribute("xml:lang", xmlLang);
		}
		if (content != null) {
			e.addChildren(Arrays.asList(content));
		}
		Packet message = Packet.packetInstance(e);
		message.setXMLNS(Packet.CLIENT_XMLNS);
		return message;
	}
}
