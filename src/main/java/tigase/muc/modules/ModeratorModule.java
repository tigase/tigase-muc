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
import java.util.List;

import tigase.criteria.Criteria;
import tigase.criteria.ElementCriteria;
import tigase.muc.Affiliation;
import tigase.muc.ElementWriter;
import tigase.muc.MucConfig;
import tigase.muc.Role;
import tigase.muc.Room;
import tigase.muc.RoomConfig.Anonymity;
import tigase.muc.exceptions.MUCException;
import tigase.muc.repository.IMucRepository;
import tigase.muc.repository.RepositoryException;
import tigase.server.Packet;
import tigase.util.TigaseStringprepException;
import tigase.xml.Element;
import tigase.xmpp.Authorization;
import tigase.xmpp.BareJID;
import tigase.xmpp.JID;
import tigase.xmpp.StanzaType;

/**
 * @author bmalkow
 * 
 */
public class ModeratorModule extends AbstractModule {

	private static final Criteria CRIT = ElementCriteria.name("iq").add(
			ElementCriteria.name("query", "http://jabber.org/protocol/muc#admin"));

	private static Affiliation getAffiliation(Element item) {
		String tmp = item.getAttribute("affiliation");
		return tmp == null ? null : Affiliation.valueOf(tmp);
	}

	private static Collection<JID> getOccupantJidsFromItem(Room room, Element item) {
		final String nick = item.getAttribute("nick");
		try {
			String $jidTmp = item.getAttribute("jid");
			final JID jid = $jidTmp == null ? null : JID.jidInstance($jidTmp);

			Collection<JID> resultJids = new ArrayList<JID>();
			if (nick != null) {
				JID j = room.getOccupantsJidsByNickname(nick);
				resultJids.add(j);
			} else if (jid != null) {
				resultJids = room.getOccupantsJids(jid.getBareJID());
			}
			return resultJids;
		} catch (TigaseStringprepException e) {
			return new ArrayList<JID>();
		}
	}

	private static String getReason(Element item) {
		Element r = item.getChild("reason");
		return r == null ? null : r.getCData();
	}

	private static Role getRole(Element item) {
		String tmp = item.getAttribute("role");
		return tmp == null ? null : Role.valueOf(tmp);
	}

	public ModeratorModule(MucConfig config, ElementWriter writer, IMucRepository mucRepository) {
		super(config, writer, mucRepository);
	}

	private void checkItem(final Room room, final Element item, final Affiliation senderaAffiliation, final Role senderRole)
			throws MUCException {

		// FIXME: check barejid only...

		final Role newRole = getRole(item);
		final Affiliation newAffiliation = getAffiliation(item);
		final Collection<JID> occupantJids = getOccupantJidsFromItem(room, item);

		if (occupantJids != null)
			for (JID occupantJid : occupantJids) {
				final Affiliation occupantAffiliation = room.getAffiliation(occupantJid.getBareJID());

				if (newRole != null && newAffiliation == null) {
					if (newRole == Role.none && !senderRole.isKickParticipantsAndVisitors()) {
						throw new MUCException(Authorization.NOT_ALLOWED, "You cannot kick");
					} else if (newRole == Role.none && occupantAffiliation.getWeight() > senderaAffiliation.getWeight()) {
						throw new MUCException(Authorization.NOT_ALLOWED, "You cannot kick occupant with higher affiliation");
					}

					if (newRole == Role.participant && !senderRole.isGrantVoice()) {
						throw new MUCException(Authorization.NOT_ALLOWED, "You cannot grant voice");
					}

					if (newRole == Role.visitor && !senderRole.isRevokeVoice()) {
						throw new MUCException(Authorization.NOT_ALLOWED, "You cannot revoke voice");
					} else if (newRole == Role.visitor && occupantAffiliation.getWeight() >= senderaAffiliation.getWeight()) {
						throw new MUCException(Authorization.NOT_ALLOWED, "You revoke voice occupant with higher affiliation");
					}

					if (newRole == Role.moderator && !senderaAffiliation.isEditModeratorList()) {
						throw new MUCException(Authorization.NOT_ALLOWED, "You cannot grant moderator provileges");
					}

				} else if (newRole == null && newAffiliation != null) {
					if (item.getAttribute("jid") == null) {
						throw new MUCException(Authorization.BAD_REQUEST);
					}

					if (newAffiliation == Affiliation.outcast && !senderaAffiliation.isBanMembersAndUnaffiliatedUsers()) {
						throw new MUCException(Authorization.NOT_ALLOWED, "You cannot ban");
					} else if (newAffiliation == Affiliation.outcast
							&& occupantAffiliation.getWeight() >= senderaAffiliation.getWeight()) {
						throw new MUCException(Authorization.NOT_ALLOWED, "You ban occupant with higher affiliation");
					}

					if (newAffiliation == Affiliation.member && !senderaAffiliation.isEditMemberList()) {
						throw new MUCException(Authorization.NOT_ALLOWED, "You cannot grant membership");
					}

					if (newAffiliation == Affiliation.admin && !senderaAffiliation.isEditAdminList()) {
						throw new MUCException(Authorization.NOT_ALLOWED, "You cannot grant admin provileges");
					}

					if (newAffiliation == Affiliation.owner && !senderaAffiliation.isEditOwnerList()) {
						throw new MUCException(Authorization.NOT_ALLOWED, "You cannot grant owner provileges");
					}

					if (newAffiliation == Affiliation.none && occupantAffiliation.getWeight() > senderaAffiliation.getWeight()) {
						throw new MUCException(Authorization.NOT_ALLOWED,
								"You cannot remove affiliation occupant with higher affiliation");
					}

				} else {
					throw new MUCException(Authorization.BAD_REQUEST);
				}
			}
	}

	@Override
	public String[] getFeatures() {
		return null;
	}

	@Override
	public Criteria getModuleCriteria() {
		return CRIT;
	}

	private Element makePresence(final JID destinationJid, final BareJID roomJID, final Room room, final JID occupantJid,
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
				if (code != null)
					x.addChild(new Element("status", new String[] { "code" }, new String[] { code }));
			}
		}

		return presence;
	}

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
			final BareJID roomJID = BareJID.bareJIDInstance(element.getAttribute("to"));

			Room room = repository.getRoom(roomJID);
			if (room == null) {
				throw new MUCException(Authorization.ITEM_NOT_FOUND);
			}

			final Element query = element.getElement().getChild("query");
			final Element item = query.getChild("item");

			JID senderJID = JID.jidInstance(element.getAttribute("from"));

			final Affiliation senderAffiliation = room.getAffiliation(senderJID.getBareJID());

			if (senderAffiliation != Affiliation.admin && senderAffiliation != Affiliation.owner) {
				throw new MUCException(Authorization.FORBIDDEN);
			}

			final Role filterRole = getRole(item);
			final Affiliation filterAffiliation = getAffiliation(item);

			if (filterAffiliation != null && filterRole == null)
				processGetAffiliations(element, room, filterAffiliation);
			else if (filterAffiliation == null && filterRole != null)
				processGetRoles(element, room, filterRole);
			else
				throw new MUCException(Authorization.BAD_REQUEST);

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
		for (JID jid : room.getOccupantsJids()) {
			final Role role = room.getRoleByJid(jid);
			if (role == filterRole) {
				final Affiliation affiliation = room.getAffiliation(jid.getBareJID());
				final String nick = room.getOccupantsNickname(jid);
				Element ir = new Element("item", new String[] { "affiliation", "nick", "role" }, new String[] {
						affiliation.name(), nick, role.name() });
				if (room.getConfig().getRoomAnonymity() != Anonymity.fullanonymous) {
					ir.setAttribute("jid", jid.toString());
				}
				responseQuery.addChild(ir);
			}
		}
		writer.write(iq.okResult(responseQuery, 0));
	}

	private void processSet(Packet element) throws RepositoryException, MUCException {

		try {

			final BareJID roomJID = BareJID.bareJIDInstance(element.getAttribute("to"));
			final Room room = repository.getRoom(roomJID);
			if (room == null) {
				throw new MUCException(Authorization.ITEM_NOT_FOUND);
			}

			JID senderJid = JID.jidInstance(element.getAttribute("from"));

			final Affiliation senderAffiliation = room.getAffiliation(senderJid.getBareJID());
			final Role senderRole = room.getRoleByJid(senderJid);

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

		final BareJID occupantBareJid = JID.jidInstance(item.getAttribute("jid")).getBareJID();
		final Affiliation previousAffiliation = room.getAffiliation(occupantBareJid);

		room.addAffiliationByJid(occupantBareJid, newAffiliation);
		JID[] realOccupantJids = room.getRealJidsByBareJid(occupantBareJid);

		for (JID realJID : realOccupantJids) {
			final String occupantNick = room.getOccupantsNickname(realJID);
			final Role currentRole = room.getRoleByJid(realJID);
			List<String> codes = new ArrayList<String>();
			boolean isUnavailable = false;
			if (newAffiliation == Affiliation.outcast) {
				codes.add("301");
				isUnavailable = true;
				Element occupantKickPresence = makePresence(realJID, room.getRoomJID(), room, realJID, isUnavailable,
						newAffiliation, newRole, occupantNick, reason, actor, codes.toArray(new String[] {}));
				room.removeAllOccupantsByBareJid(realJID.getBareJID());
				writer.write(Packet.packetInstance(occupantKickPresence));
			}

			if (newAffiliation.isViewOccupantsJid() != previousAffiliation.isViewOccupantsJid()) {
				// TODO send all occupant presences to this
				// occupant?
			}

			// sending presence to all occupants
			for (JID jid : room.getOccupantsJids()) {
				Element occupantPresence = makePresence(jid, room.getRoomJID(), room, realJID, isUnavailable, newAffiliation,
						currentRole, occupantNick, reason, null, codes.toArray(new String[] {}));
				writer.write(Packet.packetInstance(occupantPresence));
			}

		}

	}

	private void processSetRole(Room room, Element item, Role newRole, String reason, String actor)
			throws TigaseStringprepException {
		final Collection<JID> occupantJids = getOccupantJidsFromItem(room, item);
		for (JID occupantJid : occupantJids) {

			final String occupantNick = room.getOccupantsNickname(occupantJid);
			final Affiliation occupantAffiliation = room.getAffiliation(occupantJid.getBareJID());
			boolean isUnavailable = false;

			List<String> codes = new ArrayList<String>();

			if (newRole == Role.none) {
				codes.add("307");
				isUnavailable = true;

				Element occupantKickPresence = makePresence(occupantJid, room.getRoomJID(), room, occupantJid, isUnavailable,
						occupantAffiliation, newRole, occupantNick, reason, actor, codes.toArray(new String[] {}));
				room.removeAllOccupantsByBareJid(occupantJid.getBareJID());
				writer.write(Packet.packetInstance(occupantKickPresence));
			} else {
				room.setNewRole(occupantJid, newRole);
			}

			// sending presence to all occupants
			for (JID jid : room.getOccupantsJids()) {
				Element occupantPresence = makePresence(jid, room.getRoomJID(), room, occupantJid, isUnavailable,
						occupantAffiliation, newRole, occupantNick, reason, null, codes.toArray(new String[] {}));
				writer.write(Packet.packetInstance(occupantPresence));
			}
		}
	}

}
