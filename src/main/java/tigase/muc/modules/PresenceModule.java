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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import tigase.criteria.Criteria;
import tigase.criteria.ElementCriteria;
import tigase.muc.Affiliation;
import tigase.muc.DateUtil;
import tigase.muc.ElementWriter;
import tigase.muc.MucConfig;
import tigase.muc.Role;
import tigase.muc.Room;
import tigase.muc.RoomConfig;
import tigase.muc.RoomConfig.Anonymity;
import tigase.muc.exceptions.MUCException;
import tigase.muc.history.HistoryProvider;
import tigase.muc.logger.MucLogger;
import tigase.muc.modules.PresenceModule.DelayDeliveryThread.DelDeliverySend;
import tigase.muc.repository.IMucRepository;
import tigase.muc.repository.RepositoryException;
import tigase.server.Packet;
import tigase.util.TigaseStringprepException;
import tigase.xml.Element;
import tigase.xml.XMLNodeIfc;
import tigase.xmpp.Authorization;
import tigase.xmpp.BareJID;
import tigase.xmpp.JID;

/**
 * @author bmalkow
 * 
 */
public class PresenceModule extends AbstractModule {

	public static class DelayDeliveryThread extends Thread {

		public static interface DelDeliverySend {
			void sendDelayedPacket(Packet packet);
		}

		private final LinkedList<Element[]> items = new LinkedList<Element[]>();

		private final DelDeliverySend sender;

		public DelayDeliveryThread(DelDeliverySend component) {
			this.sender = component;
		}

		/**
		 * @param elements
		 */
		public void put(Collection<Element> elements) {
			if (elements != null && elements.size() > 0) {
				items.push(elements.toArray(new Element[] {}));
			}

		}

		public void put(Element element) {
			items.add(new Element[] { element });
		}

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
									sender.sendDelayedPacket(Packet.packetInstance(element));
								} catch (TigaseStringprepException ex) {
									log.info("Packet addressing problem, stringprep failed: " + element);
								}
							}
						}
					}
				} while (true);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	private static final Criteria CRIT = ElementCriteria.name("presence");

	protected static final Logger log = Logger.getLogger(PresenceModule.class.getName());

	private static Role getDefaultRole(final RoomConfig config, final Affiliation affiliation) {
		Role newRole;
		if (config.isRoomModerated() && affiliation == Affiliation.none) {
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
		if (v == null)
			return defaultValue;
		try {
			return Integer.parseInt(v);
		} catch (Exception e) {
			return defaultValue;
		}
	}

	private final Set<Criteria> allowedElements = new HashSet<Criteria>();

	private final Set<Criteria> disallowedElements = new HashSet<Criteria>();

	private boolean filterEnabled = true;

	private final HistoryProvider historyProvider;

	private boolean lockNewRoom = true;

	private final MucLogger mucLogger;

	public PresenceModule(MucConfig config, ElementWriter writer, IMucRepository mucRepository,
			HistoryProvider historyProvider, DelDeliverySend sender, MucLogger mucLogger) {
		super(config, writer, mucRepository);
		this.historyProvider = historyProvider;
		this.mucLogger = mucLogger;
		this.filterEnabled = config.isPresenceFilterEnabled();

		allowedElements.add(ElementCriteria.name("show"));
		allowedElements.add(ElementCriteria.name("status"));
		allowedElements.add(ElementCriteria.name("priority"));
		allowedElements.add(ElementCriteria.xmlns("http://jabber.org/protocol/caps"));
		log.config("Filtering presence children is " + (filterEnabled ? "enabled" : "disabled"));
	}

	/**
	 * @param room
	 * @param date
	 * @param senderJID
	 * @param nickName
	 */
	private void addJoinToHistory(Room room, Date date, JID senderJID, String nickName) {
		if (historyProvider != null)
			historyProvider.addJoinEvent(room, date, senderJID, nickName);
		if (mucLogger != null && room.getConfig().isLoggingEnabled()) {
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
		if (historyProvider != null)
			historyProvider.addLeaveEvent(room, date, senderJID, nickName);
		if (mucLogger != null && room.getConfig().isLoggingEnabled()) {
			mucLogger.addLeaveEvent(room, date, senderJID, nickName);
		}
	}

	protected Element clonePresence(Element element) {
		Element presence = new Element(element);

		if (filterEnabled) {
			List<Element> cc = element.getChildren();
			if (cc != null) {
				List<XMLNodeIfc> children = new ArrayList<XMLNodeIfc>();
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
		if (toRemove != null)
			presence.removeChild(toRemove);

		return presence;
	}

	@Override
	public String[] getFeatures() {
		return null;
	}

	@Override
	public Criteria getModuleCriteria() {
		return CRIT;
	}

	public boolean isLockNewRoom() {
		return lockNewRoom;
	}

	private Element preparePresence(JID occupantJid, final Element presence, Room room, BareJID roomJID, String nickName,
			Affiliation affiliation, Role role, JID senderJID, boolean newRoomCreated, String newNickName) {
		Anonymity anonymity = room.getConfig().getRoomAnonymity();

		final Affiliation occupantAffiliation = room.getAffiliation(occupantJid.getBareJID());

		try {
			presence.setAttribute("from", JID.jidInstance(roomJID, nickName).toString());
		} catch (TigaseStringprepException e) {
			presence.setAttribute("from", roomJID + "/" + nickName);
		}
		presence.setAttribute("to", occupantJid.toString());
		Element x = new Element("x", new String[] { "xmlns" }, new String[] { "http://jabber.org/protocol/muc#user" });

		Element item = new Element("item", new String[] { "affiliation", "role", "nick" }, new String[] { affiliation.name(),
				role.name(), nickName });

		if (senderJID.equals(occupantJid)) {
			x.addChild(new Element("status", new String[] { "code" }, new String[] { "110" }));
			if (anonymity == Anonymity.nonanonymous) {
				x.addChild(new Element("status", new String[] { "code" }, new String[] { "100" }));
			}
			if (room.getConfig().isLoggingEnabled()) {
				x.addChild(new Element("status", new String[] { "code" }, new String[] { "170" }));
			}
		}
		if (newRoomCreated) {
			x.addChild(new Element("status", new String[] { "code" }, new String[] { "201" }));
		}
		if (anonymity == Anonymity.nonanonymous
				|| (anonymity == Anonymity.semianonymous && occupantAffiliation.isViewOccupantsJid())) {
			item.setAttribute("jid", senderJID.toString());
		}
		if (newNickName != null) {
			x.addChild(new Element("status", new String[] { "code" }, new String[] { "303" }));
			item.setAttribute("nick", newNickName);
		}

		x.addChild(item);
		presence.addChild(x);

		return presence;

	}

	private void preparePresenceToAllOccupants(final Element $presence, Room room, BareJID roomJID, String nickName,
			Affiliation affiliation, Role role, JID senderJID, boolean newRoomCreated, String newNickName)
			throws TigaseStringprepException {
		for (String occupantNickname : room.getOccupantsNicknames()) {
			for (JID occupantJid : room.getOccupantsJidsByNickname(occupantNickname)) {
				Element presence = preparePresence(occupantJid, $presence.clone(), room, roomJID, nickName, affiliation, role,
						senderJID, newRoomCreated, newNickName);
				writer.write(Packet.packetInstance(presence));
			}
		}
	}

	private void preparePresenceToAllOccupants(Room room, BareJID roomJID, String nickName, Affiliation affiliation, Role role,
			JID senderJID, boolean newRoomCreated, String newNickName) throws TigaseStringprepException {

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

		preparePresenceToAllOccupants(presence, room, roomJID, nickName, affiliation, role, senderJID, newRoomCreated,
				newNickName);
	}

	@Override
	public void process(Packet element) throws MUCException, TigaseStringprepException {
		final JID senderJID = JID.jidInstance(element.getAttribute("from"));
		final BareJID roomJID = BareJID.bareJIDInstance(element.getAttribute("to"));
		final String nickName = getNicknameFromJid(JID.jidInstance(element.getAttribute("to")));
		final String presenceType = element.getAttribute("type");

		if (presenceType != null && "error".equals(presenceType)) {
			if (log.isLoggable(Level.FINER))
				log.finer("Ignoring presence with type='" + presenceType + "' from " + senderJID);
			return;
		}

		if (nickName == null) {
			throw new MUCException(Authorization.JID_MALFORMED);
		}

		try {
			Room room = repository.getRoom(roomJID);

			if (presenceType != null && "unavailable".equals(presenceType)) {
				processExit(room, element.getElement(), senderJID);
				return;
			}

			final String knownNickname;
			final boolean roomCreated;

			if (room == null) {
				log.info("Creating new room '" + roomJID + "' by user " + nickName + "' <" + senderJID.toString() + ">");
				room = repository.createNewRoom(roomJID, senderJID);
				room.addAffiliationByJid(senderJID.getBareJID(), Affiliation.owner);
				room.setRoomLocked(this.lockNewRoom);
				roomCreated = true;
				knownNickname = null;
			} else {
				roomCreated = false;
				knownNickname = room.getOccupantsNickname(senderJID);
			}

			if (knownNickname != null && knownNickname.equals(nickName)) {
				processChangeAvailabilityStatus(room, element.getElement(), senderJID, knownNickname);
			} else if (knownNickname != null && !knownNickname.equals(nickName)) {
				processChangeNickname(room, element.getElement(), senderJID, knownNickname, nickName);
			} else if (knownNickname == null) {
				processEntering(room, roomCreated, element.getElement(), senderJID, nickName);
			}

		} catch (MUCException e) {
			throw e;
		} catch (TigaseStringprepException e) {
			throw e;
		} catch (RepositoryException e) {
			throw new RuntimeException(e);
		}

	}

	protected void processChangeAvailabilityStatus(final Room room, final Element presenceElement, final JID senderJID,
			final String nickname) throws TigaseStringprepException {

		if (log.isLoggable(Level.FINEST))
			log.finest("Processing stanza " + presenceElement.toString());

		room.updatePresenceByJid(null, clonePresence(presenceElement));

		final Affiliation affiliation = room.getAffiliation(senderJID.getBareJID());
		final Role role = room.getRole(nickname);

		Element pe = room.getLastPresenceCopyByJid(senderJID.getBareJID());

		preparePresenceToAllOccupants(pe, room, room.getRoomJID(), nickname, affiliation, role, senderJID, false, null);
	}

	protected void processChangeNickname(final Room room, final Element element, final JID senderJID,
			final String senderNickname, final String newNickName) throws TigaseStringprepException, MUCException {
		if (log.isLoggable(Level.FINEST))
			log.finest("Processing stanza " + element.toString());

		throw new MUCException(Authorization.FEATURE_NOT_IMPLEMENTED, "Will me done soon");
		// TODO Example 23. Service Denies Room Join Because Roomnicks Are
		// Locked Down (???)

	}

	protected void processEntering(final Room room, final boolean roomCreated, final Element element, final JID senderJID,
			final String nickname) throws MUCException, TigaseStringprepException {
		if (log.isLoggable(Level.FINEST))
			log.finest("Processing stanza " + element.toString());

		final Affiliation affiliation = room.getAffiliation(senderJID.getBareJID());
		final Anonymity anonymity = room.getConfig().getRoomAnonymity();
		final Element xElement = element.getChild("x", "http://jabber.org/protocol/muc");
		final Element password = xElement == null ? null : xElement.getChild("password");

		if (room.getConfig().isPasswordProtectedRoom()) {
			final String psw = password == null ? null : password.getCData();
			final String roomPassword = room.getConfig().getPassword();
			if (psw == null || !psw.equals(roomPassword)) {
				// Service Denies Access Because No Password Provided
				log.finest("Password '" + psw + "' is not match to room password '" + roomPassword + "' ");
				throw new MUCException(Authorization.NOT_AUTHORIZED);
			}
		}

		if (room.isRoomLocked() && affiliation != Affiliation.owner) {
			// Service Denies Access Because Room Does Not (Yet) Exist
			throw new MUCException(Authorization.ITEM_NOT_FOUND);
		}

		if (!affiliation.isEnterOpenRoom()) {
			// Service Denies Access Because User is Banned
			log.info("User " + nickname + "' <" + senderJID.toString() + "> is on rooms '" + room.getRoomJID() + "' blacklist");
			throw new MUCException(Authorization.FORBIDDEN);
		} else if (room.getConfig().isRoomMembersOnly() && !affiliation.isEnterMembersOnlyRoom()) {
			// Service Denies Access Because User Is Not on Member List
			log.info("User " + nickname + "' <" + senderJID.toString() + "> is NOT on rooms '" + room.getRoomJID()
					+ "' member list.");
			throw new MUCException(Authorization.REGISTRATION_REQUIRED);
		}

		final BareJID currentOccupantJid = room.getOccupantsJidByNickname(nickname);
		if (currentOccupantJid != null && !currentOccupantJid.equals(senderJID.getBareJID())) {
			// Service Denies Access Because of Nick Conflict
			throw new MUCException(Authorization.CONFLICT);
		}

		// TODO Service Informs User that Room Occupant Limit Has Been Reached

		// Service Sends Presence from Existing Occupants to New Occupant
		for (String occupantNickname : room.getOccupantsNicknames()) {
			final Affiliation occupantAffiliation = room.getAffiliation(occupantNickname);
			final Role occupantRole = room.getRole(occupantNickname);
			final BareJID occupantJid = room.getOccupantsJidByNickname(occupantNickname);

			Element presence = room.getLastPresenceCopyByJid(occupantJid);
			presence.setAttribute("from", room.getRoomJID() + "/" + occupantNickname);
			presence.setAttribute("to", senderJID.toString());

			Element x = new Element("x", new String[] { "xmlns" }, new String[] { "http://jabber.org/protocol/muc#user" });

			Element item = new Element("item", new String[] { "affiliation", "role", "nick" }, new String[] {
					occupantAffiliation.name(), occupantRole.name(), occupantNickname });

			if (anonymity == Anonymity.nonanonymous
					|| (anonymity == Anonymity.semianonymous && (affiliation == Affiliation.admin || affiliation == Affiliation.owner))) {
				item.setAttribute("jid", occupantJid.toString());
			}

			x.addChild(item);
			presence.addChild(x);

			writer.write(Packet.packetInstance(presence));
		}

		final Role newRole = getDefaultRole(room.getConfig(), affiliation);
		log.finest("Occupant '" + nickname + "' <" + senderJID.toString() + "> is entering room " + room.getRoomJID()
				+ " as role=" + newRole.name() + ", affiliation=" + affiliation.name());
		room.addOccupantByJid(senderJID, nickname, newRole);
		Element pe = clonePresence(element);
		room.updatePresenceByJid(null, pe);

		if (currentOccupantJid == null) {
			// Service Sends New Occupant's Presence to All Occupants
			// Service Sends New Occupant's Presence to New Occupant
			preparePresenceToAllOccupants(room, room.getRoomJID(), nickname, affiliation, newRole, senderJID, roomCreated, null);
		} else {
			// Service Sends New Occupant's Presence to New Occupant
			Element p = preparePresence(senderJID, pe, room, room.getRoomJID(), nickname, affiliation, newRole, senderJID,
					roomCreated, null);
			writer.writeElement(p);
		}

		Integer maxchars = null;
		Integer maxstanzas = null;
		Integer seconds = null;
		Date since = null;
		Element hist = xElement.getChild("history");
		if (hist != null) {
			maxchars = toInteger(hist.getAttribute("maxchars"), null);
			maxstanzas = toInteger(hist.getAttribute("maxstanzas"), null);
			seconds = toInteger(hist.getAttribute("seconds"), null);
			since = DateUtil.parse(hist.getAttribute("since"));
		}
		sendHistoryToUser(room, senderJID, maxchars, maxstanzas, seconds, since, writer);

		if (room.getSubject() != null && room.getSubjectChangerNick() != null && room.getSubjectChangeDate() != null) {
			Element message = new Element("message", new String[] { "type", "from", "to" }, new String[] { "groupchat",
					room.getRoomJID() + "/" + room.getSubjectChangerNick(), senderJID.toString() });
			message.addChild(new Element("subject", room.getSubject()));

			String stamp = DateUtil.formatDatetime(room.getSubjectChangeDate());
			Element delay = new Element("delay", new String[] { "xmlns", "stamp" }, new String[] { "urn:xmpp:delay", stamp });
			delay.setAttribute("jid", room.getRoomJID() + "/" + room.getSubjectChangerNick());

			Element x = new Element("x", new String[] { "xmlns", "stamp" }, new String[] { "jabber:x:delay",
					DateUtil.formatOld(room.getSubjectChangeDate()) });

			message.addChild(delay);
			message.addChild(x);

			writer.writeElement(message);
		}

		if (room.isRoomLocked()) {
			sendMucMessage(room, room.getOccupantsNickname(senderJID), "Room is locked. Please configure.");
		}

		if (roomCreated) {
			StringBuilder sb = new StringBuilder();
			sb.append("Welcome! You created new Multi User Chat Room.");
			if (room.isRoomLocked())
				sb.append(" Room is locked now. Configure it please!");
			else
				sb.append(" Room is unlocked and ready for occupants!");

			sendMucMessage(room, room.getOccupantsNickname(senderJID), sb.toString());
		}

		if (room.getConfig().isLoggingEnabled()) {
			addJoinToHistory(room, new Date(), senderJID, nickname);
		}

	}

	protected void processExit(final Room room, final Element presenceElement, final JID senderJID) throws MUCException,
			TigaseStringprepException {
		if (log.isLoggable(Level.FINEST))
			log.finest("Processing stanza " + presenceElement.toString());

		if (room == null)
			throw new MUCException(Authorization.ITEM_NOT_FOUND, "Unkown room");

		final String nickname = room.getOccupantsNickname(senderJID);

		if (nickname == null)
			throw new MUCException(Authorization.ITEM_NOT_FOUND, "Unkown occupant");

		final Element pe = clonePresence(presenceElement);
		final Affiliation affiliation = room.getAffiliation(senderJID.getBareJID());
		final Role role = room.getRole(nickname);

		final Element selfPresence = preparePresence(senderJID, pe, room, room.getRoomJID(), nickname, affiliation, role,
				senderJID, false, null);

		boolean nicknameGone = room.removeOccupant(senderJID);
		room.updatePresenceByJid(senderJID, pe);

		writer.writeElement(selfPresence);

		// TODO if highest priority is gone, then send current highest priority
		// to occupants

		if (nicknameGone) {
			preparePresenceToAllOccupants(pe, room, room.getRoomJID(), nickname, affiliation, role, senderJID, false, null);
			if (room.getConfig().isLoggingEnabled()) {
				addLeaveToHistory(room, new Date(), senderJID, nickname);
			}
		}
	}

	/**
	 * @param room
	 * @param senderJID
	 * @param maxchars
	 * @param maxstanzas
	 * @param seconds
	 * @param since
	 */
	private void sendHistoryToUser(final Room room, final JID senderJID, final Integer maxchars, final Integer maxstanzas,
			final Integer seconds, final Date since, final ElementWriter writer) {
		if (historyProvider != null)
			historyProvider.getHistoryMessages(room, senderJID, maxchars, maxstanzas, seconds, since, writer);
	}

	public void setLockNewRoom(boolean lockNewRoom) {
		this.lockNewRoom = lockNewRoom;
	}

}
