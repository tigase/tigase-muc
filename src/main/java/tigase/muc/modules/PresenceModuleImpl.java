/*
 * Tigase Jabber/XMPP Multi-User Chat Component
 * Copyright (C) 2008 "Bartosz M. Małkowski" <bartosz.malkowski@tigase.org>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. Look for COPYING file in the top folder.
 * If not, see http://www.gnu.org/licenses/.
 *
 * $Rev$
 * Last modified by $Author$
 * $Date$
 */
package tigase.muc.modules;

import tigase.component.exceptions.RepositoryException;
import tigase.criteria.Criteria;
import tigase.criteria.ElementCriteria;
import tigase.muc.*;
import tigase.muc.exceptions.MUCException;
import tigase.muc.history.HistoryProvider;
import tigase.muc.logger.MucLogger;
import tigase.server.Message;
import tigase.server.Packet;
import tigase.util.TigaseStringprepException;
import tigase.xml.Element;
import tigase.xml.XMLNodeIfc;
import tigase.xmpp.Authorization;
import tigase.xmpp.BareJID;
import tigase.xmpp.JID;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author bmalkow
 */
public class PresenceModuleImpl
		extends AbstractMucModule
		implements PresenceModule {

	/**
	 * Field description
	 */
	protected static final Logger log = Logger.getLogger(PresenceModule.class.getName());
	private static final Criteria CRIT = ElementCriteria.name("presence");
	private final Set<Criteria> allowedElements = new HashSet<Criteria>();

	public static void addCodes(PresenceWrapper wrapper, boolean newRoomCreated, String newNickName) {
		if (newRoomCreated) {
			wrapper.addStatusCode(201);
		}
		if (newNickName != null) {
			wrapper.addStatusCode(303);

			for (Element item : wrapper.items) {
				item.setAttribute("nick", newNickName);
			}
		}
	}

	public static Role getDefaultRole(final RoomConfig config, final Affiliation affiliation) {
		Role newRole;

		if (config.isRoomModerated() && (affiliation == Affiliation.none)) {
			newRole = Role.visitor;
		} else {
			switch (affiliation) {
				case admin:
					newRole = Role.moderator;

					break;

				case member:
					newRole = Role.participant;

					break;

				case none:
					newRole = Role.participant;

					break;

				case outcast:
					newRole = Role.none;

					break;

				case owner:
					newRole = Role.moderator;

					break;

				default:
					newRole = Role.none;

					break;
			}
		}

		return newRole;
	}

	private static Integer toInteger(String v, Integer defaultValue) {
		if (v == null) {
			return defaultValue;
		}
		try {
			return Integer.parseInt(v);
		} catch (Exception e) {
			return defaultValue;
		}
	}

	/**
	 * Constructs ...
	 *
	 * @param config
	 * @param writer
	 * @param mucRepository
	 * @param historyProvider
	 * @param sender
	 * @param mucLogger
	 */
	public PresenceModuleImpl() {
		allowedElements.add(ElementCriteria.name("show"));
		allowedElements.add(ElementCriteria.name("status"));
		allowedElements.add(ElementCriteria.name("priority"));
		allowedElements.add(ElementCriteria.xmlns("http://jabber.org/protocol/caps"));
	}

	/**
	 * @param room
	 * @param date
	 * @param senderJID
	 * @param nickName
	 */
	private void addJoinToHistory(Room room, Date date, JID senderJID, String nickName) {
		HistoryProvider historyProvider = context.getHistoryProvider();
		if (historyProvider != null) {
			historyProvider.addJoinEvent(room, date, senderJID, nickName);
		}
		MucLogger mucLogger = context.getMucLogger();
		if ((mucLogger != null) && room.getConfig().isLoggingEnabled()) {
			mucLogger.addJoinEvent(room, date, senderJID, nickName);
		}
	}

	/**
	 * @param room
	 * @param date
	 * @param senderJID
	 * @param nickName
	 */
	private void addLeaveToHistory(Room room, Date date, JID senderJID, String nickName) {
		HistoryProvider historyProvider = context.getHistoryProvider();
		if (historyProvider != null) {
			historyProvider.addLeaveEvent(room, date, senderJID, nickName);
		}
		MucLogger mucLogger = context.getMucLogger();
		if ((mucLogger != null) && room.getConfig().isLoggingEnabled()) {
			mucLogger.addLeaveEvent(room, date, senderJID, nickName);
		}
	}

	/**
	 * Method description
	 *
	 * @param element
	 *
	 * @return
	 */
	protected Element clonePresence(Element element) {
		Element presence = new Element(element);

		if (context.isPresenceFilterEnabled()) {
			List<Element> cc = element.getChildren();

			if (cc != null) {
				@SuppressWarnings("rawtypes") List<XMLNodeIfc> children = new ArrayList<XMLNodeIfc>();

				for (Element c : cc) {
					for (Criteria crit : allowedElements) {
						if (crit.match(c)) {
							children.add(c);

							break;
						}
					}
				}
				presence.setChildren(children);
			}
		}

		Element toRemove = presence.getChild("x", "http://jabber.org/protocol/muc");

		if (toRemove != null) {
			presence.removeChild(toRemove);
		}

		return presence;
	}

	/**
	 * @param room
	 * @param senderJID
	 *
	 * @throws TigaseStringprepException
	 */
	@Override
	public void doQuit(final Room room, final JID senderJID) throws TigaseStringprepException {
		final String leavingNickname = room.getOccupantsNickname(senderJID);
		if (leavingNickname == null) {
			if (log.isLoggable(Level.FINE)) {
				log.fine("JID " + senderJID + " has no name. It is not occupant of room " + room.getRoomJID());
			}
			return;
		}
		final Affiliation leavingAffiliation = room.getAffiliation(leavingNickname);
		Element presenceElement = new Element("presence");

		if (log.isLoggable(Level.FINER)) {
			log.finer(
					"Occupant " + senderJID + " known as " + leavingNickname + " is leaving room " + room.getRoomJID());
		}

		presenceElement.setAttribute("type", "unavailable");

		Collection<JID> occupantJIDs = new ArrayList<JID>(room.getOccupantsJidsByNickname(leavingNickname));
		boolean nicknameGone = room.removeOccupant(senderJID);
		context.getGhostbuster().remove(senderJID, room);

		room.updatePresenceByJid(senderJID, leavingNickname, null);

		if (context.isMultiItemMode()) {
			final PresenceWrapper selfPresence = PresenceWrapper.preparePresenceW(room, senderJID, presenceElement,
																				  senderJID.getBareJID(), occupantJIDs,
																				  leavingNickname, leavingAffiliation,
																				  Role.none);
			write(selfPresence.packet);
		} else {
			Collection<JID> z = new ArrayList<JID>(1);
			z.add(senderJID);

			final PresenceWrapper selfPresence = PresenceWrapper.preparePresenceW(room, senderJID, presenceElement,
																				  senderJID.getBareJID(), z,
																				  leavingNickname, leavingAffiliation,
																				  Role.none);
			write(selfPresence.packet);
		}

		// TODO if highest priority is gone, then send current highest priority
		// to occupants
		if (nicknameGone) {
			for (String occupantNickname : room.getOccupantsNicknames()) {
				for (JID occupantJid : room.getOccupantsJidsByNickname(occupantNickname)) {
					presenceElement = new Element("presence");
					presenceElement.setAttribute("type", "unavailable");

					PresenceWrapper presence = PresenceWrapper.preparePresenceW(room, occupantJid, presenceElement,
																				senderJID.getBareJID(), occupantJIDs,
																				leavingNickname, leavingAffiliation,
																				Role.none);

					write(presence.packet);
				}
			}
			if (room.getConfig().isLoggingEnabled()) {
				addLeaveToHistory(room, new Date(), senderJID, leavingNickname);
			}
		} else {
			occupantJIDs = new ArrayList<JID>(room.getOccupantsJidsByNickname(leavingNickname));

			Element pe = room.getLastPresenceCopy(senderJID.getBareJID(), leavingNickname);
			if (pe == null) {
				pe = new Element("presence", new String[]{"type"}, new String[]{"unavailable"});
			}
			for (String occupantNickname : room.getOccupantsNicknames()) {
				for (JID occupantJid : room.getOccupantsJidsByNickname(occupantNickname)) {
					if (context.isMultiItemMode()) {
						PresenceWrapper presence = PresenceWrapper.preparePresenceW(room, occupantJid, pe.clone(),
																					senderJID.getBareJID(),
																					occupantJIDs, leavingNickname,
																					leavingAffiliation, Role.none);
						write(presence.packet);
					} else {
						for (JID jid : occupantJIDs) {
							Collection<JID> z = new ArrayList<JID>(1);
							z.add(jid);
							PresenceWrapper presence = PresenceWrapper.preparePresenceW(room, occupantJid, pe.clone(),
																						senderJID.getBareJID(), z,
																						leavingNickname,
																						leavingAffiliation, Role.none);
							write(presence.packet);
						}
					}
				}
			}
		}

		Element event = new Element("RoomLeave", new String[]{"xmlns"}, new String[]{"tigase:events:muc"});
		event.addChild(new Element("room", room.getRoomJID().toString()));
		event.addChild(new Element("nickname", leavingNickname));
		event.addChild(new Element("jid", senderJID.toString()));
		fireEvent(event);

		if (room.getOccupantsCount() == 0) {
			if (!room.getConfig().isPersistentRoom()) {
				HistoryProvider historyProvider = context.getHistoryProvider();
				if ((historyProvider != null)) {
					if (log.isLoggable(Level.FINE)) {
						log.fine("Removing history of room " + room.getRoomJID());
					}
					historyProvider.removeHistory(room);
				} else if (log.isLoggable(Level.FINE)) {
					log.fine("Cannot remove history of room " + room.getRoomJID() +
									 " because history provider is not available.");
				}
			} else if (log.isLoggable(Level.FINE)) {
				log.fine("Room persistent. History will not be removed.");
			}
			context.getMucRepository().leaveRoom(room);

			Element emptyRoomEvent = new Element("EmptyRoom", new String[]{"xmlns"}, new String[]{"tigase:events:muc"});
			emptyRoomEvent.addChild(new Element("room", room.getRoomJID().toString()));
			fireEvent(emptyRoomEvent);
		}
	}

	/**
	 * Method description
	 *
	 * @return
	 */
	@Override
	public String[] getFeatures() {
		return null;
	}

	/**
	 * Method description
	 *
	 * @return
	 */
	@Override
	public Criteria getModuleCriteria() {
		return CRIT;
	}

	protected PresenceWrapper preparePresence(JID destinationJID, final Element presence, Room room, JID occupantJID,
											  boolean newRoomCreated, String newNickName)
			throws TigaseStringprepException {
		final PresenceWrapper wrapper = PresenceWrapper.preparePresenceW(room, destinationJID, presence, occupantJID);

		addCodes(wrapper, newRoomCreated, newNickName);

		return wrapper;
	}

	/**
	 * Method description
	 *
	 * @param element
	 *
	 * @throws MUCException
	 * @throws TigaseStringprepException
	 */
	@Override
	public void process(Packet element) throws MUCException, TigaseStringprepException {
		final JID senderJID = JID.jidInstance(element.getAttributeStaticStr(Packet.FROM_ATT));
		final BareJID roomJID = BareJID.bareJIDInstance(element.getAttributeStaticStr(Packet.TO_ATT));
		final String nickName = getNicknameFromJid(JID.jidInstance(element.getAttributeStaticStr(Packet.TO_ATT)));
		final String presenceType = element.getAttributeStaticStr(Packet.TYPE_ATT);

		// final String id = element.getAttribute("id");
		if ((presenceType != null) && "error".equals(presenceType)) {
			if (log.isLoggable(Level.FINER)) {
				log.finer("Ignoring presence with type='" + presenceType + "' from " + senderJID);
			}

			return;
		}
		if (nickName == null) {
			throw new MUCException(Authorization.JID_MALFORMED);
		}
		try {
			Room room = context.getMucRepository().getRoom(roomJID);

			if ((presenceType != null) && "unavailable".equals(presenceType)) {
				processExit(room, element.getElement(), senderJID);

				return;
			}

			final String knownNickname;
			final boolean roomCreated;

			if (room == null) {
				if (log.isLoggable(Level.INFO)) {
					log.info("Creating new room '" + roomJID + "' by user " + nickName + "' <" + senderJID.toString() +
									 ">");
				}
				room = context.getMucRepository().createNewRoom(roomJID, senderJID);
				room.addAffiliationByJid(senderJID.getBareJID(), Affiliation.owner);
				room.setRoomLocked(context.isNewRoomLocked());
				roomCreated = true;
				knownNickname = null;
				room.getConfig().notifyConfigUpdate(true);
				room.setNewSubject(null, nickName);
			} else {
				roomCreated = false;
				knownNickname = room.getOccupantsNickname(senderJID);
			}

			final boolean probablyReEnter =
					element.getElement().getChild("x", "http://jabber.org/protocol/muc") != null;

			if ((knownNickname != null) && !knownNickname.equals(nickName)) {
				processChangeNickname(room, element.getElement(), senderJID, knownNickname, nickName);
			} else if (probablyReEnter || (knownNickname == null)) {
				processEntering(room, roomCreated, element.getElement(), senderJID, nickName);
			} else if (knownNickname.equals(nickName)) {
				processChangeAvailabilityStatus(room, element.getElement(), senderJID, knownNickname);
			}
		} catch (MUCException e) {
			throw e;
		} catch (TigaseStringprepException e) {
			throw e;
		} catch (RepositoryException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Method description
	 *
	 * @param room
	 * @param presenceElement
	 * @param senderJID
	 * @param nickname
	 *
	 * @throws TigaseStringprepException
	 */
	protected void processChangeAvailabilityStatus(final Room room, final Element presenceElement, final JID senderJID,
												   final String nickname) throws TigaseStringprepException {
		if (log.isLoggable(Level.FINEST)) {
			log.finest("Processing stanza " + presenceElement.toString());
		}

		// we only update presence if the room is not filtered or user is on the list of desired affiliations
		if (!room.getConfig().isPresenceFilterEnabled() || (room.getConfig().isPresenceFilterEnabled() &&
				!room.getConfig().getPresenceFilteredAffiliations().isEmpty() && room.getConfig()
				.getPresenceFilteredAffiliations()
				.contains(room.getAffiliation(senderJID.getBareJID())))) {
			room.updatePresenceByJid(null, nickname, clonePresence(presenceElement));
		}

		Element pe = room.getLastPresenceCopyByJid(senderJID.getBareJID());

		sendPresenceToAllOccupants(pe, room, senderJID, false, null);
	}

	/**
	 * Method description
	 *
	 * @param room
	 * @param element
	 * @param senderJID
	 * @param senderNickname
	 * @param newNickName
	 *
	 * @throws MUCException
	 * @throws TigaseStringprepException
	 */
	protected void processChangeNickname(final Room room, final Element element, final JID senderJID,
										 final String senderNickname, final String newNickName)
			throws TigaseStringprepException, MUCException {
		if (log.isLoggable(Level.FINEST)) {
			log.finest("Processing stanza " + element.toString());
		}

		throw new MUCException(Authorization.FEATURE_NOT_IMPLEMENTED, "Changing nickname is not supported yet.");

		// TODO Example 23. Service Denies Room Join Because Roomnicks Are
		// Locked Down (???)
	}

	/**
	 * Method description
	 *
	 * @param room
	 * @param roomCreated
	 * @param element
	 * @param senderJID
	 * @param nickname
	 *
	 * @throws MUCException
	 * @throws TigaseStringprepException
	 */
	protected void processEntering(final Room room, final boolean roomCreated, final Element element,
								   final JID senderJID, final String nickname)
			throws MUCException, TigaseStringprepException {
		if (log.isLoggable(Level.FINEST)) {
			log.finest("Processing stanza " + element.toString());
		}

		final Affiliation affiliation = room.getAffiliation(senderJID.getBareJID());
		final Element xElement = element.getChild("x", "http://jabber.org/protocol/muc");
		final Element password = (xElement == null) ? null : xElement.getChild("password");

		if (room.getConfig().isPasswordProtectedRoom()) {
			final String psw = (password == null) ? null : password.getCData();
			final String roomPassword = room.getConfig().getPassword();

			if ((psw == null) || !psw.equals(roomPassword)) {

				// Service Denies Access Because No Password Provided
				if (log.isLoggable(Level.FINEST)) {
					log.finest("Password '" + psw + "' is not match to room password '" + roomPassword + "' ");
				}

				throw new MUCException(Authorization.NOT_AUTHORIZED);
			}
		}
		if (room.isRoomLocked() && (affiliation != Affiliation.owner)) {

			// Service Denies Access Because Room Does Not (Yet) Exist
			throw new MUCException(Authorization.ITEM_NOT_FOUND, null, "Room exists but is locked");
		}
		if (!affiliation.isEnterOpenRoom()) {

			// Service Denies Access Because User is Banned
			if (log.isLoggable(Level.INFO)) {
				log.info("User " + nickname + "' <" + senderJID.toString() + "> is on rooms '" + room.getRoomJID() +
								 "' blacklist");
			}

			throw new MUCException(Authorization.FORBIDDEN);
		} else if (room.getConfig().isRoomMembersOnly() && !affiliation.isEnterMembersOnlyRoom()) {

			// Service Denies Access Because User Is Not on Member List
			if (log.isLoggable(Level.INFO)) {
				log.info("User " + nickname + "' <" + senderJID.toString() + "> is NOT on rooms '" + room.getRoomJID() +
								 "' member list.");
			}

			throw new MUCException(Authorization.REGISTRATION_REQUIRED);
		}

		final BareJID currentOccupantJid = room.getOccupantsJidByNickname(nickname);

		if ((currentOccupantJid != null) &&
				(!context.isMultiItemMode() || !currentOccupantJid.equals(senderJID.getBareJID()))) {

			// Service Denies Access Because of Nick Conflict
			throw new MUCException(Authorization.CONFLICT);
		}

		final Integer roomMaxUsers = room.getConfig().getMaxUsers();
		if (roomMaxUsers != null && currentOccupantJid == null && room.getOccupantsCount() >= roomMaxUsers) {
			log.info("User " + nickname + "' <" + senderJID.toString() + "> cannot join to room '" + room.getRoomJID() +
							 "' because maximum number of occupants is reached.");
			throw new MUCException(Authorization.SERVICE_UNAVAILABLE, "Reached maximum number of occupants.");
		}

		// TODO Service Informs User that Room Occupant Limit Has Been Reached
		// Service Sends Presence from Existing Occupants to New Occupant
		sendPresencesToNewOccupant(room, senderJID);

		final Role newRole = getDefaultRole(room.getConfig(), affiliation);

		if (log.isLoggable(Level.FINEST)) {
			log.finest(
					"Occupant '" + nickname + "' <" + senderJID.toString() + "> is entering room " + room.getRoomJID() +
							" as role=" + newRole.name() + ", affiliation=" + affiliation.name());
		}

		Element pe = clonePresence(element);
		room.addOccupantByJid(senderJID, nickname, newRole, pe);

		context.getGhostbuster().add(senderJID, room);

		// if (currentOccupantJid == null) {

		// Service Sends New Occupant's Presence to All Occupants
		// Service Sends New Occupant's Presence to New Occupant
		sendPresenceToAllOccupants(room, senderJID, roomCreated, null);
		// }

		Element event = new Element("RoomJoin", new String[]{"xmlns"}, new String[]{"tigase:events:muc"});
		event.addChild(new Element("room", room.getRoomJID().toString()));
		event.addChild(new Element("nickname", nickname));
		event.addChild(new Element("jid", senderJID.toString()));
		fireEvent(event);

		Integer maxchars = null;
		Integer maxstanzas = null;
		Integer seconds = null;
		Date since = null;
		Element hist = (xElement == null) ? null : xElement.getChild("history");

		if (hist != null) {
			maxchars = toInteger(hist.getAttributeStaticStr("maxchars"), null);
			maxstanzas = toInteger(hist.getAttributeStaticStr("maxstanzas"), null);
			seconds = toInteger(hist.getAttributeStaticStr("seconds"), null);
			since = DateUtil.parse(hist.getAttributeStaticStr("since"));
		}
		sendHistoryToUser(room, senderJID, maxchars, maxstanzas, seconds, since);
		if ((room.getSubjectChangerNick() != null) && (room.getSubjectChangeDate() != null)) {
			Element message = new Element(Message.ELEM_NAME,
										  new String[]{Packet.TYPE_ATT, Packet.FROM_ATT, Packet.TO_ATT, Packet.ID_ATT},
										  new String[]{"groupchat",
													   room.getRoomJID() + "/" + room.getSubjectChangerNick(),
													   senderJID.toString(), GroupchatMessageModule.generateSubjectId(
												  room.getSubjectChangeDate(), room.getSubject())});

			message.addChild(new Element("subject", room.getSubject()));

			String stamp = DateUtil.formatDatetime(room.getSubjectChangeDate());
			Element delay = new Element("delay", new String[]{"xmlns", "stamp"}, new String[]{"urn:xmpp:delay", stamp});

			delay.setAttribute("jid", room.getRoomJID() + "/" + room.getSubjectChangerNick());

			Element x = new Element("x", new String[]{"xmlns", "stamp"},
									new String[]{"jabber:x:delay", DateUtil.formatOld(room.getSubjectChangeDate())});

			message.addChild(delay);
			message.addChild(x);

			Packet p = Packet.packetInstance(message);
			p.setXMLNS(Packet.CLIENT_XMLNS);

			write(p);
		}
		if (room.isRoomLocked() && context.isWelcomeMessagesEnabled() && room.getConfig().isWelcomeMessageEnabled()) {
			sendMucMessage(room, room.getOccupantsNickname(senderJID), "Room is locked. Please configure.");
		}
		if (roomCreated) {
			fireEvent(RoomConfigurationModule.createRoomCreatedEvent(room));

			if (context.isWelcomeMessagesEnabled() && room.getConfig().isWelcomeMessageEnabled()) {
				StringBuilder sb = new StringBuilder();
				sb.append("Welcome! You created new Multi User Chat Room.");
				if (room.isRoomLocked()) {
					sb.append(" Room is locked now. Configure it please!");
				} else if (context.isNewRoomLocked()) {
					sb.append(" Room is unlocked and ready for occupants!");
				}
				sendMucMessage(room, room.getOccupantsNickname(senderJID), sb.toString());
			}
		}
		if (room.getConfig().isLoggingEnabled()) {
			addJoinToHistory(room, new Date(), senderJID, nickname);
		}

		if (log.isLoggable(Level.FINEST)) {
			log.finest(room.getDebugInfoOccupants());
		}

	}

	/**
	 * Method description
	 *
	 * @param room
	 * @param presenceElement
	 * @param senderJID
	 *
	 * @throws MUCException
	 * @throws TigaseStringprepException
	 */
	protected void processExit(final Room room, final Element presenceElement, final JID senderJID)
			throws MUCException, TigaseStringprepException {
		if (log.isLoggable(Level.FINEST)) {
			log.finest("Processing stanza " + presenceElement.toString());
		}
		if (room == null) {
			throw new MUCException(Authorization.ITEM_NOT_FOUND, "Unknown room");
		}

		final String leavingNickname = room.getOccupantsNickname(senderJID);

		if (leavingNickname == null) {
			if (log.isLoggable(Level.FINE)) {
				log.fine("JID " + senderJID + " has no name. It is not occupant of room " + room.getRoomJID());
			}
			return;
		}
		doQuit(room, senderJID);
	}

	/**
	 * @param room
	 * @param senderJID
	 * @param maxchars
	 * @param maxstanzas
	 * @param seconds
	 * @param since
	 */
	private void sendHistoryToUser(final Room room, final JID senderJID, final Integer maxchars,
								   final Integer maxstanzas, final Integer seconds, final Date since) {
		HistoryProvider historyProvider = context.getHistoryProvider();
		if (log.isLoggable(Level.FINEST)) {
			log.finest("Sending history to user using: " + historyProvider + " history provider");
		}

		if (historyProvider != null) {
			historyProvider.getHistoryMessages(room, senderJID, maxchars, maxstanzas, seconds, since,
											   context.getWriter());
		}
	}

	protected void sendPresenceToAllOccupants(final Element $presence, Room room, JID senderJID, boolean newRoomCreated,
											  String newNickName) throws TigaseStringprepException {

		final String occupantNickname = room.getOccupantsNickname(senderJID);
		final BareJID occupantJID = room.getOccupantsJidByNickname(occupantNickname);
		final Affiliation occupantAffiliation = room.getAffiliation(occupantJID);
		final Role occupantRole = room.getRole(occupantNickname);

		Collection<String> occupantsNicknames;

		if (room.getConfig().isPresenceFilterEnabled()) {
			if (room.getConfig().getPresenceFilteredAffiliations().contains(occupantAffiliation)) {
				// we only want users with propper affiliation
				occupantsNicknames = room.getPresenceFiltered().getOccupantsPresenceFilteredNicknames();
			} else {
				// only send presence back to user that joined
				occupantsNicknames = Arrays.asList(occupantNickname);
			}
		} else {
			// no filtering, send presence to all users
			occupantsNicknames = room.getOccupantsNicknames();
		}

		if (log.isLoggable(Level.FINEST)) {
			log.log(Level.FINEST, "Sending presence to all occupants, filtering enabled: " +
					room.getConfig().isPresenceFilterEnabled() + ", occupantsNicknames: " +
					Arrays.asList(occupantsNicknames));
		}

		for (String destinationNickname : occupantsNicknames) {
			for (JID destinationJID : room.getOccupantsJidsByNickname(destinationNickname)) {

				if (context.isMultiItemMode()) {
					PresenceWrapper presence = preparePresence(destinationJID, $presence.clone(), room, senderJID,
															   newRoomCreated, newNickName);
					write(presence.packet);
				} else {
					for (JID jid : room.getOccupantsJidsByNickname(occupantNickname)) {
						Collection<JID> z = new ArrayList<JID>(1);
						z.add(jid);
						PresenceWrapper l = PresenceWrapper.preparePresenceW(room, destinationJID, $presence.clone(),
																			 occupantJID, z, occupantNickname,
																			 occupantAffiliation, occupantRole);
						addCodes(l, newRoomCreated, newNickName);

						write(l.packet);
					}
				}
			}
		}
	}

	protected void sendPresenceToAllOccupants(Room room, JID senderJID, boolean newRoomCreated, String newNickName)
			throws TigaseStringprepException {
		Element presence;

		if (newNickName != null) {
			presence = new Element("presence");
			presence.setAttribute("type", "unavailable");
		} else if (room.getOccupantsNickname(senderJID) == null) {
			presence = new Element("presence");
			presence.setAttribute("type", "unavailable");
		} else {
			presence = room.getLastPresenceCopyByJid(senderJID.getBareJID());
		}
		if (presence != null) {
			sendPresenceToAllOccupants(presence, room, senderJID, newRoomCreated, newNickName);
		}
	}

	@Override
	public void sendPresencesToNewOccupant(Room room, JID senderJID) throws TigaseStringprepException {
		BareJID currentOccupantJid = senderJID.getBareJID();
		Affiliation senderAffiliation = room.getAffiliation(currentOccupantJid);

		// in filtered room we skip sending occupants list to new occupants
		// witout propper affiliation
		if (room.getConfig().isPresenceFilterEnabled() &&
				!room.getConfig().getPresenceFilteredAffiliations().contains(senderAffiliation)) {
			if (log.isLoggable(Level.FINEST)) {
				log.log(Level.FINEST, "Filtering enabled: " + room.getConfig().isPresenceFilterEnabled() +
						"; new occupant doesn't have propper affiliation -  skip sending occupants list");
			}
			return;
		}

		for (String occupantNickname : room.getOccupantsNicknames()) {
			final BareJID occupantJid = room.getOccupantsJidByNickname(occupantNickname);

			if (occupantJid == null) {
				// why the hell occupantJid is null?
				continue;
			}

			// we don't include current user in occupants presence broadcast
			if (currentOccupantJid != null && currentOccupantJid.equals(occupantJid)) {
				continue;
			}

			Affiliation affiliation = room.getAffiliation(occupantJid);
			if (room.getConfig().isPresenceFilterEnabled() &&
					!room.getConfig().getPresenceFilteredAffiliations().contains(affiliation)) {
				if (log.isLoggable(Level.FINEST)) {
					log.log(Level.FINEST, "Filtering enabled: " + room.getConfig().isPresenceFilterEnabled() +
							"; target occupant doesn't have propper affiliation -  don't include him in the list");
				}
				continue;
			}

			Element op = room.getLastPresenceCopyByJid(occupantJid);

			if (op == null) {
				continue;
			}

			final Collection<JID> occupantJIDs = room.getOccupantsJidsByNickname(occupantNickname);
			final BareJID occupantBareJID = room.getOccupantsJidByNickname(occupantNickname);
			final Affiliation occupantAffiliation = room.getAffiliation(occupantBareJID);
			final Role occupantRole = room.getRole(occupantNickname);

			if (context.isMultiItemMode()) {
				PresenceWrapper l = PresenceWrapper.preparePresenceW(room, senderJID, op.clone(), occupantBareJID,
																	 occupantJIDs, occupantNickname,
																	 occupantAffiliation, occupantRole);
				write(l.packet);
			} else {
				for (JID jid : occupantJIDs) {
					Collection<JID> z = new ArrayList<JID>(1);
					z.add(jid);
					PresenceWrapper l = PresenceWrapper.preparePresenceW(room, senderJID, op.clone(), occupantBareJID,
																		 z, occupantNickname, occupantAffiliation,
																		 occupantRole);
					write(l.packet);
				}
			}
		}
	}

	/**
	 * Class description
	 *
	 * @author Enter your name here...
	 * @version Enter version here..., 13/02/20
	 */
	public static class DelayDeliveryThread
			extends Thread {

		private final LinkedList<Element[]> items = new LinkedList<Element[]>();
		private final DelDeliverySend sender;

		/**
		 * Constructs ...
		 *
		 * @param component
		 */
		public DelayDeliveryThread(DelDeliverySend component) {
			this.sender = component;
		}

		/**
		 * @param elements
		 */
		public void put(Collection<Element> elements) {
			if ((elements != null) && (elements.size() > 0)) {
				items.push(elements.toArray(new Element[]{}));
			}
		}

		/**
		 * Method description
		 *
		 * @param element
		 */
		public void put(Element element) {
			items.add(new Element[]{element});
		}

		/**
		 * Method description
		 */
		@Override
		public void run() {
			try {
				do {
					sleep(553);
					if (items.size() > 0) {
						Element[] toSend = items.poll();

						if (toSend != null) {
							for (Element element : toSend) {
								try {
									Packet p = Packet.packetInstance(element);
									p.setXMLNS(Packet.CLIENT_XMLNS);
									sender.sendDelayedPacket(p);
								} catch (TigaseStringprepException ex) {
									if (log.isLoggable(Level.INFO)) {
										log.info("Packet addressing problem, stringprep failed: " + element);
									}
								}
							}
						}
					}
				} while (true);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

		/**
		 * Interface description
		 *
		 * @author Enter your name here...
		 * @version Enter version here..., 13/02/20
		 */
		public static interface DelDeliverySend {

			/**
			 * Method description
			 *
			 * @param packet
			 */
			void sendDelayedPacket(Packet packet);
		}
	}

}
