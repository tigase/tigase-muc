/*
 * ModeratorModule.java
 *
 * Tigase Jabber/XMPP Server
 * Copyright (C) 2004-2012 "Artur Hefczyc" <artur.hefczyc@tigase.org>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. Look for COPYING file in the top folder.
 * If not, see http://www.gnu.org/licenses/.
 *
 */

package tigase.muc.modules;

import tigase.component.exceptions.RepositoryException;
import tigase.criteria.Criteria;
import tigase.criteria.ElementCriteria;
import tigase.muc.Affiliation;
import tigase.muc.Role;
import tigase.muc.Room;
import tigase.muc.RoomConfig.Anonymity;
import tigase.muc.exceptions.MUCException;
import tigase.server.Packet;
import tigase.util.TigaseStringprepException;
import tigase.xml.Element;
import tigase.xmpp.Authorization;
import tigase.xmpp.BareJID;
import tigase.xmpp.JID;
import tigase.xmpp.StanzaType;

import java.util.*;
import java.util.logging.Level;

/**
 * @author bmalkow
 */
public class ModeratorModule
		extends AbstractMucModule {

	public static final String ID = "admin";
	protected static final Criteria CRIT = ElementCriteria.name("iq")
			.add(ElementCriteria.name("query", "http://jabber.org/protocol/muc#admin"));

	protected static Affiliation getAffiliation(Element item) throws MUCException {
		String tmp = item.getAttributeStaticStr("affiliation");

		try {
			return (tmp == null) ? null : Affiliation.valueOf(tmp);
		} catch (IllegalArgumentException e) {
			throw new MUCException(Authorization.BAD_REQUEST, "Unknown affiliation value: " + tmp);
		}
	}

	protected static String getReason(Element item) {
		Element r = item.getChild("reason");

		return (r == null) ? null : r.getCData();
	}

	protected static Role getRole(Element item) {
		String tmp = item.getAttributeStaticStr("role");

		return (tmp == null) ? null : Role.valueOf(tmp);
	}

	protected void checkItem(final Room room, final Element item, JID senderJid, final Affiliation senderaAffiliation,
							 final Role senderRole) throws MUCException, TigaseStringprepException {
		final Role newRole = getRole(item);
		final Affiliation newAffiliation = getAffiliation(item);
		HashSet<String> occupantNicknames = new HashSet<String>();

//		 The moderator can then modify the voice list if desired. In order to do so, the moderator MUST send the
//		 changed items (i.e., only the "delta") back to the service; each item MUST include the 'nick' attribute
//		 and 'role' attribute (normally set to a value of "participant" or "visitor") but SHOULD NOT include
//		 the 'jid' attribute and MUST NOT include the 'affiliation' attribute (which is used to manage affiliations
//		 such as owner rather than the participant role):
		final String nick = item.getAttributeStaticStr("nick");
		final String jid = item.getAttributeStaticStr("jid");

		if (newRole != null && nick != null) {
			occupantNicknames.add(nick);
		} else if (newAffiliation != null && jid != null) {
			occupantNicknames.addAll(room.getOccupantsNicknames(BareJID.bareJIDInstance(jid)));
		} else {
			throw new MUCException(Authorization.BAD_REQUEST);
		}
		for (String occupantNickname : occupantNicknames) {
			final Affiliation occupantAffiliation = room.getAffiliation(occupantNickname);

			if ((newRole != null) && (newAffiliation == null)) {
				if ((newRole == Role.none) && !senderRole.isKickParticipantsAndVisitors()) {
					if (log.isLoggable(Level.FINEST)) {
						log.finest("User " + senderJid + " (a:" + senderaAffiliation + "; r:" + senderRole +
										   ") is not allowed to kick user " + occupantNickname + " (a:" +
										   occupantAffiliation + ")");
					}
					throw new MUCException(Authorization.NOT_ALLOWED, "You cannot kick");
				} else if ((newRole == Role.none) &&
						(occupantAffiliation.getWeight() > senderaAffiliation.getWeight())) {
					if (log.isLoggable(Level.FINEST)) {
						log.finest("User " + senderJid + " (a:" + senderaAffiliation + "; r:" + senderRole +
										   ") is not allowed to kick user " + occupantNickname + " (a:" +
										   occupantAffiliation + ") because of lower affiliation.");
					}
					throw new MUCException(Authorization.NOT_ALLOWED,
										   "You cannot kick occupant with higher affiliation");
				}
				if ((newRole == Role.participant) && !senderRole.isGrantVoice()) {
					if (log.isLoggable(Level.FINEST)) {
						log.finest("User " + senderJid + " (a:" + senderaAffiliation + "; r:" + senderRole +
										   ") is not allowed to grant voice to user " + occupantNickname + " (a:" +
										   occupantAffiliation + ") because of lower affiliation.");
					}
					throw new MUCException(Authorization.NOT_ALLOWED, "You cannot grant voice");
				}
				if ((newRole == Role.visitor) && !senderRole.isRevokeVoice()) {
					throw new MUCException(Authorization.NOT_ALLOWED, "You cannot revoke voice");
				} else if ((newRole == Role.visitor) &&
						(occupantAffiliation.getWeight() >= senderaAffiliation.getWeight())) {
					throw new MUCException(Authorization.NOT_ALLOWED,
										   "You revoke voice occupant with higher affiliation");
				}
				if ((newRole == Role.moderator) && !senderaAffiliation.isEditModeratorList()) {
					throw new MUCException(Authorization.NOT_ALLOWED, "You cannot grant moderator provileges");
				}
			} else if ((newRole == null) && (newAffiliation != null)) {
				if (item.getAttributeStaticStr("jid") == null) {
					throw new MUCException(Authorization.BAD_REQUEST);
				}
				if ((newAffiliation == Affiliation.outcast) && !senderaAffiliation.isBanMembersAndUnaffiliatedUsers()) {
					throw new MUCException(Authorization.NOT_ALLOWED, "You cannot ban");
				} else if ((newAffiliation == Affiliation.outcast) &&
						(occupantAffiliation.getWeight() >= senderaAffiliation.getWeight())) {
					throw new MUCException(Authorization.NOT_ALLOWED, "You ban occupant with higher affiliation");
				}
				if ((newAffiliation == Affiliation.member) && !senderaAffiliation.isEditMemberList()) {
					throw new MUCException(Authorization.NOT_ALLOWED, "You cannot grant membership");
				}
				if ((newAffiliation == Affiliation.admin) && !senderaAffiliation.isEditAdminList()) {
					throw new MUCException(Authorization.NOT_ALLOWED, "You cannot grant admin provileges");
				}
				if ((newAffiliation == Affiliation.owner) && !senderaAffiliation.isEditOwnerList()) {
					throw new MUCException(Authorization.NOT_ALLOWED, "You cannot grant owner provileges");
				}
				if ((newAffiliation == Affiliation.none) &&
						(occupantAffiliation.getWeight() > senderaAffiliation.getWeight())) {
					throw new MUCException(Authorization.NOT_ALLOWED,
										   "You cannot remove affiliation occupant with higher affiliation");
				}
			} else {
				throw new MUCException(Authorization.BAD_REQUEST);
			}
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

	/**
	 * Kicking user without sending presence to all other occupant. Used only to
	 * inform occupants that component is stopping.
	 */
	public void kickWithoutBroadcast(Room room, String occupantNick, String reason, String actor)
			throws TigaseStringprepException {
		List<String> codes = new ArrayList<String>();
		final BareJID occupantJid = room.getOccupantsJidByNickname(occupantNick);
		final Affiliation occupantAffiliation = room.getAffiliation(occupantJid);

		codes.add("307");
		boolean isUnavailable = true;

		final Collection<JID> occupantJids = room.getOccupantsJidsByNickname(occupantNick);

		for (JID jid : occupantJids) {
			Packet occupantKickPresence = makePresence(jid, room.getRoomJID(), room, occupantJid, isUnavailable,
													   occupantAffiliation, Role.none, occupantNick, reason, actor,
													   codes.toArray(new String[]{}));

			write(occupantKickPresence);
		}
		room.removeOccupant(occupantNick);
	}

	protected Packet makePresence(final JID destinationJid, final BareJID roomJID, final Room room,
								  final BareJID occupantJid, boolean unavailable, Affiliation affiliation, Role role,
								  String nick, String reason, String actor, String... codes)
			throws TigaseStringprepException {
		Element presence = unavailable
						   ? new Element("presence", new String[]{"type"}, new String[]{"unavailable"})
						   : room.getLastPresenceCopyByJid(occupantJid);

		try {
			presence.setAttribute("from", JID.jidInstance(roomJID, nick).toString());
		} catch (TigaseStringprepException e) {
			presence.setAttribute("from", roomJID.toString() + "/" + nick);
		}
		presence.setAttribute("to", destinationJid.toString());

		Element x = new Element("x", new String[]{"xmlns"}, new String[]{"http://jabber.org/protocol/muc#user"});

		presence.addChild(x);

		Element item = new Element("item");

		x.addChild(item);
		if (role != null) {
			item.setAttribute("role", role.name());
		}
		if (affiliation != null) {
			item.setAttribute("affiliation", affiliation.name());
		}
		if (nick != null) {
			item.setAttribute("nick", nick);
		}

		// TODO jid
		if (actor != null) {
			item.addChild(new Element("actor", new String[]{"jid"}, new String[]{actor}));
		}
		if (reason != null) {
			item.addChild(new Element("reason", reason));
		}
		if (codes != null) {
			for (String code : codes) {
				if (code != null) {
					x.addChild(new Element("status", new String[]{"code"}, new String[]{code}));
				}
			}
		}

		Packet result = Packet.packetInstance(presence);
		result.setXMLNS(Packet.CLIENT_XMLNS);

		return result;
	}

	/**
	 * Method description
	 *
	 * @param element
	 *
	 * @throws MUCException
	 */
	@Override
	public void process(Packet element) throws MUCException {
		try {
			final StanzaType type = element.getType();

			if (getNicknameFromJid(element.getTo()) != null) {
				throw new MUCException(Authorization.BAD_REQUEST);
			}
			if (type == StanzaType.set) {
				processSet(element);
			} else if (type == StanzaType.get) {
				processGet(element);
			} else {
				throw new MUCException(Authorization.BAD_REQUEST);
			}
		} catch (MUCException e1) {
			throw e1;
		} catch (Exception e) {
			e.printStackTrace();

			throw new RuntimeException(e);
		}
	}

	protected void processGet(Packet element) throws RepositoryException, MUCException {
		try {
			final BareJID roomJID = BareJID.bareJIDInstance(element.getAttributeStaticStr(Packet.TO_ATT));
			Room room = context.getMucRepository().getRoom(roomJID);

			if (room == null) {
				throw new MUCException(Authorization.ITEM_NOT_FOUND);
			}
			final List<Element> items = element.getElemChildrenStaticStr(new String[]{"iq", "query"});
			final JID senderJID = JID.jidInstance(element.getAttributeStaticStr(Packet.FROM_ATT));
			final String senderNickname = room.getOccupantsNickname(senderJID);
			final Affiliation senderAffiliation = room.getAffiliation(senderJID.getBareJID());
			final Role senderRole = room.getRole(senderNickname);

			final Collection<Item> resultItems = new HashSet<>();

			final HashSet<Object> filter = new HashSet<>();

			for (final Element item : items) {
				if (!item.getName().equals("item")) {
					throw new MUCException(Authorization.NOT_ACCEPTABLE);
				}
				String tmp = item.getAttributeStaticStr("role");
				if (tmp != null && tmp.equals("all")) {
					for (Role r : Role.values()) {
						filter.add(r);
					}
					continue;
				}
				tmp = item.getAttributeStaticStr("affiliation");
				if (tmp != null && tmp.equals("all")) {
					for (Affiliation a : Affiliation.values()) {
						filter.add(a);
					}
					continue;
				}

				final Role filterRole = getRole(item);
				final Affiliation filterAffiliation = getAffiliation(item);

				if (filterRole != null) {
					filter.add(filterRole);
				}
				if (filterAffiliation != null) {
					filter.add(filterAffiliation);
				}

			}

			for (final Object f : filter) {
				final Role filterRole = f instanceof Role ? (Role) f : null;
				final Affiliation filterAffiliation = f instanceof Affiliation ? (Affiliation) f : null;

				boolean allowed = false;
				allowed = allowed || senderAffiliation == Affiliation.admin;
				allowed = allowed || senderAffiliation == Affiliation.owner;
				allowed = allowed ||
						(room.getConfig().getRoomAnonymity() == Anonymity.nonanonymous && senderRole.isPresentInRoom());

				if (!allowed) {
					throw new MUCException(Authorization.FORBIDDEN);
				}

				processMatchingItems(element, room, filterAffiliation, filterRole, resultItems);
			}

			Element responseQuery = new Element("query", new String[]{"xmlns"},
												new String[]{"http://jabber.org/protocol/muc#admin"});
			for (Item resultItem : resultItems) {
				responseQuery.addChild(resultItem);
			}
			write(element.okResult(responseQuery, 0));

		} catch (MUCException e1) {
			throw e1;
		} catch (Exception e) {
			e.printStackTrace();

			throw new RuntimeException(e);
		}
	}

	protected void processMatchingItems(final Packet iq, final Room room, final Affiliation filterAffiliation,
										final Role filterRole, final Collection<Item> resultItems)
			throws RepositoryException, MUCException {

		for (BareJID jid : room.getAffiliations()) {
			final Affiliation affiliation = room.getAffiliation(jid);

			if (filterAffiliation != null && affiliation == filterAffiliation) {
				Item i = new Item(affiliation, jid, null, null);
				if (!resultItems.contains(i)) {
					resultItems.add(i);
				}
			}
		}

		for (String occupantNickname : room.getOccupantsNicknames()) {
			final Role role = room.getRole(occupantNickname);
			final BareJID occupantBareJid = room.getOccupantsJidByNickname(occupantNickname);
			final Affiliation affiliation = room.getAffiliation(occupantBareJid);

			if (filterRole != null && role == filterRole ||
					filterAffiliation != null && affiliation == filterAffiliation) {
				BareJID jid = room.getConfig().getRoomAnonymity() != Anonymity.fullanonymous ? occupantBareJid : null;
				Item i = new Item(affiliation, jid, occupantNickname, role);
				resultItems.remove(i);
				resultItems.add(i);
			}
		}
	}

	protected void processSet(Packet element) throws RepositoryException, MUCException {
		try {
			final BareJID roomJID = BareJID.bareJIDInstance(element.getAttributeStaticStr(Packet.TO_ATT));
			final Room room = context.getMucRepository().getRoom(roomJID);

			if (room == null) {
				throw new MUCException(Authorization.ITEM_NOT_FOUND);
			}

			JID senderJid = JID.jidInstance(element.getAttributeStaticStr(Packet.FROM_ATT));
			final String nickName = room.getOccupantsNickname(senderJid);
			final Affiliation senderAffiliation = room.getAffiliation(senderJid.getBareJID());

			final Role senderRole = nickName == null
									? PresenceModuleImpl.getDefaultRole(room.getConfig(), senderAffiliation)
									: room.getRole(nickName);
			final Element query = element.getElement().getChild("query");
			final List<Element> items = query.getChildren();

			if (items == null) {
				throw new MUCException(Authorization.NOT_ACCEPTABLE);
			}

			for (Element item : items) {
				checkItem(room, item, senderJid, senderAffiliation, senderRole);
			}
			for (Element item : items) {
				final Role newRole = getRole(item);
				final Affiliation newAffiliation = getAffiliation(item);
				final String reason = getReason(item);
				final String actor = senderJid.toString();

				if (newAffiliation != null) {
					processSetAffiliation(room, item, newAffiliation, newRole, reason, actor);
				}
				if (newRole != null) {
					String occupantNick = item.getAttributeStaticStr("nick");
					processSetRole(room, occupantNick, newRole, reason, actor);
				}
			}
			write(element.okResult((Element) null, 0));
		} catch (MUCException e1) {
			throw e1;
		} catch (Exception e) {
			e.printStackTrace();

			throw new RuntimeException(e);
		}
	}

	protected void processSetAffiliation(Room room, Element item, Affiliation newAffiliation, Role newRole,
										 String reason, String actor)
			throws RepositoryException, TigaseStringprepException {

		final BareJID occupantBareJid = JID.jidInstance(item.getAttributeStaticStr("jid")).getBareJID();
		final Affiliation previousAffiliation = room.getAffiliation(occupantBareJid);

		if (log.isLoggable(Level.FINE)) {
			log.fine("Set affiliation of " + occupantBareJid + " to " + newAffiliation + " (from: " +
							 previousAffiliation + ")");
		}

		if (room.getConfig().isRoomMembersOnly() && (previousAffiliation.getWeight() <= Affiliation.none.getWeight()) &&
				(newAffiliation.getWeight() >= Affiliation.member.getWeight())) {
			sendInvitation(room, occupantBareJid, actor);
		}
		room.addAffiliationByJid(occupantBareJid, newAffiliation);

		boolean isUnavailable = false;
		Set<String> codes = new HashSet<String>();
		Collection<String> occupantsNicknames = room.getOccupantsNicknames(occupantBareJid);

		boolean kick = newAffiliation == Affiliation.outcast;
		kick |= room.getConfig().isRoomMembersOnly() && newAffiliation == Affiliation.none;
		if (kick) {
			if (log.isLoggable(Level.FINE)) {
				log.fine("Kicking occupant " + occupantBareJid + " (" + occupantsNicknames + ")");
			}
			for (String occupantNick : occupantsNicknames) {
				codes.add("301");
				isUnavailable = true;

				final Collection<JID> occupantJids = room.getOccupantsJidsByNickname(occupantNick);

				for (JID jid : occupantJids) {
					Packet occupantKickPresence = makePresence(jid, room.getRoomJID(), room, occupantBareJid,
															   isUnavailable, newAffiliation, newRole, occupantNick,
															   reason, actor, codes.toArray(new String[]{}));

					write(occupantKickPresence);
				}
				room.removeOccupant(occupantNick);
				context.getGhostbuster().remove(occupantJids, room);

			}
		}

		if (log.isLoggable(Level.FINE)) {
			log.fine("Sending new affiliation of " + occupantBareJid + " to occupants " + room.getOccupantsNicknames());
		}
		for (String nickname : room.getOccupantsNicknames()) {
			final Collection<JID> occupantJids = room.getOccupantsJidsByNickname(nickname);

			if (log.isLoggable(Level.FINER)) {
				log.finer("Sending new affiliation of " + occupantBareJid + " to occupant " + nickname + " (" +
								  occupantJids + ")");
			}

			for (JID jid : occupantJids) {
				for (String changedNickname : occupantsNicknames) {
					final Role currentRole = room.getRole(changedNickname);
					Packet occupantPresence = makePresence(jid, room.getRoomJID(), room, occupantBareJid, isUnavailable,
														   newAffiliation, currentRole, changedNickname, reason, null,
														   codes.toArray(new String[]{}));
					write(occupantPresence);
				}
			}
		}
	}

	protected void processSetRole(Room room, String occupantNick, Role newRole, String reason, String actor)
			throws TigaseStringprepException {
		final BareJID occupantJid = room.getOccupantsJidByNickname(occupantNick);
		final Affiliation occupantAffiliation = room.getAffiliation(occupantJid);
		boolean isUnavailable = false;
		List<String> codes = new ArrayList<String>();

		if (newRole == Role.none) {
			codes.add("307");
			isUnavailable = true;

			final Collection<JID> occupantJids = room.getOccupantsJidsByNickname(occupantNick);

			for (JID jid : occupantJids) {
				Packet occupantKickPresence = makePresence(jid, room.getRoomJID(), room, occupantJid, isUnavailable,
														   occupantAffiliation, newRole, occupantNick, reason, actor,
														   codes.toArray(new String[]{}));

				write(occupantKickPresence);
			}
			room.removeOccupant(occupantNick);
			context.getGhostbuster().remove(occupantJids, room);
		} else {
			room.setNewRole(occupantNick, newRole);
		}

		// sending presence to all occupants
		for (String nickname : room.getOccupantsNicknames()) {
			final Collection<JID> occupantJids = room.getOccupantsJidsByNickname(nickname);

			for (JID jid : occupantJids) {
				Packet occupantPresence = makePresence(jid, room.getRoomJID(), room, occupantJid, isUnavailable,
													   occupantAffiliation, newRole, occupantNick, reason, null,
													   codes.toArray(new String[]{}));

				write(occupantPresence);
			}
		}
	}

	/**
	 * @param room
	 * @param occupantBareJid
	 *
	 * @throws TigaseStringprepException
	 */
	protected void sendInvitation(Room room, BareJID occupantBareJid, String actor) throws TigaseStringprepException {
		Packet message = Packet.packetInstance(new Element("message", new String[]{Packet.FROM_ATT, Packet.TO_ATT},
														   new String[]{room.getRoomJID().toString(),
																		occupantBareJid.toString()}));
		message.setXMLNS(Packet.CLIENT_XMLNS);

		final Element x = new Element("x", new String[]{"xmlns"}, new String[]{"http://jabber.org/protocol/muc#user"});

		message.getElement().addChild(x);

		final Element invite = new Element("invite", new String[]{"from"}, new String[]{actor});

		x.addChild(invite);
		if (room.getConfig().isPasswordProtectedRoom()) {
			x.addChild(new Element("password", room.getConfig().getPassword()));
		}
		write(message);
	}

	static class Item
			extends Element {

		private final Affiliation affiliation;
		private final BareJID jid;
		private final String nick;
		private final Role role;

		private Item(Affiliation affiliation, BareJID jid, String nick, Role role) {
			super("item");
			this.affiliation = affiliation;
			if (this.affiliation != null) {
				setAttribute("affiliation", affiliation.name());
			}
			this.jid = jid;
			if (this.jid != null) {
				setAttribute("jid", jid.toString());
			}
			this.nick = nick;
			if (this.nick != null) {
				setAttribute("nick", nick);
			}
			this.role = role;
			if (this.role != null) {
				setAttribute("role", role.name());
			}
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) {
				return true;
			}
			if (o == null || getClass() != o.getClass()) {
				return false;
			}

			Item item = (Item) o;

			if (this.jid != null && item.jid != null) {
				return this.jid.equals(item.jid);
			}
			return nick != null ? nick.equals(item.nick) : item.nick == null;
		}

		@Override
		public int hashCode() {
			if (jid != null) {
				return jid.hashCode();
			} else if (nick != null) {
				return nick.hashCode();
			} else {
				return super.hashCode();
			}
		}
	}
}