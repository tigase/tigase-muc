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

//~--- non-JDK imports --------------------------------------------------------

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import tigase.component.ElementWriter;
import tigase.component.exceptions.RepositoryException;
import tigase.criteria.Criteria;
import tigase.criteria.ElementCriteria;
import tigase.muc.Affiliation;
import tigase.muc.MucConfig;
import tigase.muc.Role;
import tigase.muc.Room;
import tigase.muc.RoomConfig.Anonymity;
import tigase.muc.exceptions.MUCException;
import tigase.muc.repository.IMucRepository;
import tigase.server.Message;
import tigase.server.Packet;
import tigase.util.TigaseStringprepException;
import tigase.xml.Element;
import tigase.xmpp.Authorization;
import tigase.xmpp.BareJID;
import tigase.xmpp.JID;
import tigase.xmpp.StanzaType;

//~--- JDK imports ------------------------------------------------------------

/**
 * @author bmalkow
 * 
 */
public class ModeratorModule extends AbstractModule {
	private static final Criteria CRIT = ElementCriteria.name("iq").add(
			ElementCriteria.name("query", "http://jabber.org/protocol/muc#admin"));

	// ~--- constructors
	// ---------------------------------------------------------

	private static Affiliation getAffiliation(Element item) throws MUCException {
		String tmp = item.getAttributeStaticStr("affiliation");

		try {
			return (tmp == null) ? null : Affiliation.valueOf(tmp);
		} catch (IllegalArgumentException e) {
			throw new MUCException(Authorization.BAD_REQUEST, "Unknown affiliation value: " + tmp);
		}
	}

	// ~--- get methods
	// ----------------------------------------------------------

	private static String getReason(Element item) {
		Element r = item.getChild("reason");

		return (r == null) ? null : r.getCData();
	}

	private static Role getRole(Element item) {
		String tmp = item.getAttributeStaticStr("role");

		return (tmp == null) ? null : Role.valueOf(tmp);
	}

	/**
	 * Constructs ...
	 * 
	 * 
	 * @param config
	 * @param writer
	 * @param mucRepository
	 */
	public ModeratorModule(MucConfig config, ElementWriter writer, IMucRepository mucRepository) {
		super(config, writer, mucRepository);
	}

	// ~--- methods
	// --------------------------------------------------------------

	private void checkItem(final Room room, final Element item, final Affiliation senderaAffiliation, final Role senderRole)
			throws MUCException, TigaseStringprepException {
		final Role newRole = getRole(item);
		final Affiliation newAffiliation = getAffiliation(item);
		HashSet<String> occupantNicknames = new HashSet<String>();

		if (item.getAttributeStaticStr("nick") != null) {
			occupantNicknames.add(item.getAttributeStaticStr("nick"));
		}
		if (item.getAttributeStaticStr("jid") != null) {
			occupantNicknames.addAll(room.getOccupantsNicknames(BareJID.bareJIDInstance(item.getAttributeStaticStr("jid"))));
		}
		for (String occupantNickname : occupantNicknames) {
			final Affiliation occupantAffiliation = room.getAffiliation(occupantNickname);

			if ((newRole != null) && (newAffiliation == null)) {
				if ((newRole == Role.none) && !senderRole.isKickParticipantsAndVisitors()) {
					throw new MUCException(Authorization.NOT_ALLOWED, "You cannot kick");
				} else if ((newRole == Role.none) && (occupantAffiliation.getWeight() > senderaAffiliation.getWeight())) {
					throw new MUCException(Authorization.NOT_ALLOWED, "You cannot kick occupant with higher affiliation");
				}
				if ((newRole == Role.participant) && !senderRole.isGrantVoice()) {
					throw new MUCException(Authorization.NOT_ALLOWED, "You cannot grant voice");
				}
				if ((newRole == Role.visitor) && !senderRole.isRevokeVoice()) {
					throw new MUCException(Authorization.NOT_ALLOWED, "You cannot revoke voice");
				} else if ((newRole == Role.visitor) && (occupantAffiliation.getWeight() >= senderaAffiliation.getWeight())) {
					throw new MUCException(Authorization.NOT_ALLOWED, "You revoke voice occupant with higher affiliation");
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
				} else if ((newAffiliation == Affiliation.outcast)
						&& (occupantAffiliation.getWeight() >= senderaAffiliation.getWeight())) {
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
				if ((newAffiliation == Affiliation.none) && (occupantAffiliation.getWeight() > senderaAffiliation.getWeight())) {
					throw new MUCException(Authorization.NOT_ALLOWED,
							"You cannot remove affiliation occupant with higher affiliation");
				}
			} else {
				throw new MUCException(Authorization.BAD_REQUEST);
			}
		}
	}

	// ~--- get methods
	// ----------------------------------------------------------

	/**
	 * Method description
	 * 
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
	 * 
	 * @return
	 */
	@Override
	public Criteria getModuleCriteria() {
		return CRIT;
	}

	// ~--- methods
	// --------------------------------------------------------------

	private Element makePresence(final JID destinationJid, final BareJID roomJID, final Room room, final BareJID occupantJid,
			boolean unavailable, Affiliation affiliation, Role role, String nick, String reason, String actor, String... codes) {
		Element presence = unavailable ? new Element("presence", new String[] { "type" }, new String[] { "unavailable" })
				: room.getLastPresenceCopyByJid(occupantJid);

		try {
			presence.setAttribute("from", JID.jidInstance(roomJID, nick).toString());
		} catch (TigaseStringprepException e) {
			presence.setAttribute("from", roomJID.toString() + "/" + nick);
		}
		presence.setAttribute("to", destinationJid.toString());

		Element x = new Element("x", new String[] { "xmlns" }, new String[] { "http://jabber.org/protocol/muc#user" });

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
			x.addChild(new Element("actor", new String[] { "jid" }, new String[] { actor }));
		}
		if (reason != null) {
			x.addChild(new Element("reason", reason));
		}
		if (codes != null) {
			for (String code : codes) {
				if (code != null) {
					x.addChild(new Element("status", new String[] { "code" }, new String[] { code }));
				}
			}
		}

		return presence;
	}

	/**
	 * Method description
	 * 
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

	private void processGet(Packet element) throws RepositoryException, MUCException {
		try {
			final BareJID roomJID = BareJID.bareJIDInstance(element.getAttributeStaticStr(Packet.TO_ATT));
			Room room = repository.getRoom(roomJID);

			if (room == null) {
				throw new MUCException(Authorization.ITEM_NOT_FOUND);
			}

			final Element query = element.getElement().getChild("query");
			final Element item = query.getChild("item");
			JID senderJID = JID.jidInstance(element.getAttributeStaticStr(Packet.FROM_ATT));
			final Affiliation senderAffiliation = room.getAffiliation(senderJID.getBareJID());

			if ((senderAffiliation != Affiliation.admin) && (senderAffiliation != Affiliation.owner)) {
				throw new MUCException(Authorization.FORBIDDEN);
			}

			final Role filterRole = getRole(item);
			final Affiliation filterAffiliation = getAffiliation(item);

			if ((filterAffiliation != null) && (filterRole == null)) {
				processGetAffiliations(element, room, filterAffiliation);
			} else if ((filterAffiliation == null) && (filterRole != null)) {
				processGetRoles(element, room, filterRole);
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

	private void processGetAffiliations(final Packet iq, final Room room, final Affiliation filter) throws RepositoryException,
			MUCException {
		Element responseQuery = new Element("query", new String[] { "xmlns" },
				new String[] { "http://jabber.org/protocol/muc#admin" });

		for (BareJID jid : room.getAffiliations()) {
			final Affiliation affiliation = room.getAffiliation(jid);

			if (affiliation == filter) {
				Element ir = new Element("item", new String[] { "affiliation", "jid" }, new String[] { affiliation.name(),
						jid.toString() });

				responseQuery.addChild(ir);
			}
		}
		writer.write(iq.okResult(responseQuery, 0));
	}

	private void processGetRoles(final Packet iq, final Room room, final Role filterRole) throws RepositoryException,
			MUCException {
		Element responseQuery = new Element("query", new String[] { "xmlns" },
				new String[] { "http://jabber.org/protocol/muc#admin" });

		for (String occupantNickname : room.getOccupantsNicknames()) {
			final Role role = room.getRole(occupantNickname);
			final BareJID occupantBareJid = room.getOccupantsJidByNickname(occupantNickname);

			if (role == filterRole) {
				final Affiliation affiliation = room.getAffiliation(occupantBareJid);
				Element ir = new Element("item", new String[] { "affiliation", "nick", "role" }, new String[] {
						affiliation.name(), occupantNickname, role.name() });

				if (room.getConfig().getRoomAnonymity() != Anonymity.fullanonymous) {
					ir.setAttribute("jid", occupantBareJid.toString());
				}
				responseQuery.addChild(ir);
			}
		}
		writer.write(iq.okResult(responseQuery, 0));
	}

	private void processSet(Packet element) throws RepositoryException, MUCException {
		try {
			final BareJID roomJID = BareJID.bareJIDInstance(element.getAttributeStaticStr(Packet.TO_ATT));
			final Room room = repository.getRoom(roomJID);

			if (room == null) {
				throw new MUCException(Authorization.ITEM_NOT_FOUND);
			}

			JID senderJid = JID.jidInstance(element.getAttributeStaticStr(Packet.FROM_ATT));
			final String nickName = room.getOccupantsNickname(senderJid);
			final Affiliation senderAffiliation = room.getAffiliation(senderJid.getBareJID());
			final Role senderRole = room.getRole(nickName);
			final Element query = element.getElement().getChild("query");
			final List<Element> items = query.getChildren();

			for (Element item : items) {
				checkItem(room, item, senderAffiliation, senderRole);
			}
			writer.write(element.okResult((Element) null, 0));
			for (Element item : items) {
				final Role newRole = getRole(item);
				final Affiliation newAffiliation = getAffiliation(item);
				final String reason = getReason(item);
				final String actor = senderJid.toString();

				if (newAffiliation != null) {
					processSetAffiliation(room, item, newAffiliation, newRole, reason, actor);
				}
				if (newRole != null) {
					processSetRole(room, item, newRole, reason, actor);
				}
			}
		} catch (MUCException e1) {
			throw e1;
		} catch (Exception e) {
			e.printStackTrace();

			throw new RuntimeException(e);
		}
	}

	private void processSetAffiliation(Room room, Element item, Affiliation newAffiliation, Role newRole, String reason,
			String actor) throws RepositoryException, TigaseStringprepException {
		final BareJID occupantBareJid = JID.jidInstance(item.getAttributeStaticStr("jid")).getBareJID();
		final Affiliation previousAffiliation = room.getAffiliation(occupantBareJid);

		if (room.getConfig().isRoomMembersOnly() && (previousAffiliation.getWeight() <= Affiliation.none.getWeight())
				&& (newAffiliation.getWeight() >= Affiliation.member.getWeight())) {
			sendInvitation(room, occupantBareJid, actor);
		}
		room.addAffiliationByJid(occupantBareJid, newAffiliation);

		boolean isUnavailable = false;
		Set<String> codes = new HashSet<String>();
		Collection<String> occupantsNicknames = room.getOccupantsNicknames(occupantBareJid);

		for (String occupantNick : occupantsNicknames) {
			if (newAffiliation == Affiliation.outcast) {
				codes.add("301");
				isUnavailable = true;

				final Collection<JID> occupantJids = room.getOccupantsJidsByNickname(occupantNick);

				for (JID jid : occupantJids) {
					Element occupantKickPresence = makePresence(jid, room.getRoomJID(), room, occupantBareJid, isUnavailable,
							newAffiliation, newRole, occupantNick, reason, actor, codes.toArray(new String[] {}));

					writer.write(Packet.packetInstance(occupantKickPresence));
				}
				room.removeOccupant(occupantNick);
			}
		}
		for (String nickname : room.getOccupantsNicknames()) {
			final Collection<JID> occupantJids = room.getOccupantsJidsByNickname(nickname);

			for (JID jid : occupantJids) {
				for (String removed : occupantsNicknames) {
					final Role currentRole = room.getRole(removed);
					Element occupantPresence = makePresence(jid, room.getRoomJID(), room, occupantBareJid, isUnavailable,
							newAffiliation, currentRole, removed, reason, null, codes.toArray(new String[] {}));

					writer.write(Packet.packetInstance(occupantPresence));
				}
			}
		}
	}

	private void processSetRole(Room room, Element item, Role newRole, String reason, String actor)
			throws TigaseStringprepException {
		final String occupantNick = item.getAttributeStaticStr("nick");
		final BareJID occupantJid = room.getOccupantsJidByNickname(occupantNick);
		final Affiliation occupantAffiliation = room.getAffiliation(occupantJid);
		boolean isUnavailable = false;
		List<String> codes = new ArrayList<String>();

		if (newRole == Role.none) {
			codes.add("307");
			isUnavailable = true;

			final Collection<JID> occupantJids = room.getOccupantsJidsByNickname(occupantNick);

			for (JID jid : occupantJids) {
				Element occupantKickPresence = makePresence(jid, room.getRoomJID(), room, occupantJid, isUnavailable,
						occupantAffiliation, newRole, occupantNick, reason, actor, codes.toArray(new String[] {}));

				writer.write(Packet.packetInstance(occupantKickPresence));
			}
			room.removeOccupant(occupantNick);
		} else {
			room.setNewRole(occupantNick, newRole);
		}

		// sending presence to all occupants
		for (String nickname : room.getOccupantsNicknames()) {
			final Collection<JID> occupantJids = room.getOccupantsJidsByNickname(nickname);

			for (JID jid : occupantJids) {
				Element occupantPresence = makePresence(jid, room.getRoomJID(), room, occupantJid, isUnavailable,
						occupantAffiliation, newRole, occupantNick, reason, null, codes.toArray(new String[] {}));

				writer.write(Packet.packetInstance(occupantPresence));
			}
		}
	}

	/**
	 * @param room
	 * @param occupantBareJid
	 */
	private void sendInvitation(Room room, BareJID occupantBareJid, String actor) {
		final Packet message = Message.getMessage(JID.jidInstance(room.getRoomJID()), JID.jidInstance(occupantBareJid),
				StanzaType.normal, null, null, null, null);

		final Element x = new Element("x", new String[] { "xmlns" }, new String[] { "http://jabber.org/protocol/muc#user" });

		message.getElement().addChild(x);

		final Element invite = new Element("invite", new String[] { "from" }, new String[] { actor });

		x.addChild(invite);
		if (room.getConfig().isPasswordProtectedRoom()) {
			x.addChild(new Element("password", room.getConfig().getPassword()));
		}
		writer.write(message);
	}
}

// ~ Formatted in Tigase Code Convention on 13/02/20
