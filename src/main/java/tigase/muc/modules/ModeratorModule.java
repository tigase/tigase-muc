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
import java.util.List;

import tigase.criteria.Criteria;
import tigase.criteria.ElementCriteria;
import tigase.muc.Affiliation;
import tigase.muc.MucConfig;
import tigase.muc.Role;
import tigase.muc.Room;
import tigase.muc.RoomConfig.Anonymity;
import tigase.muc.exceptions.MUCException;
import tigase.muc.repository.IMucRepository;
import tigase.muc.repository.RepositoryException;
import tigase.util.JIDUtils;
import tigase.xml.Element;
import tigase.xmpp.Authorization;

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

	private static String getOccupantJidFromItem(Room room, Element item) {
		final String nick = item.getAttribute("nick");
		final String jid = item.getAttribute("jid");

		String resultJid = null;
		if (nick != null) {
			resultJid = room.getOccupantsJidByNickname(nick);
		} else if (jid != null) {
			String tmpNick = room.getOccupantsNicknameByBareJid(jid);
			resultJid = room.getOccupantsJidByNickname(nick);
		}
		return resultJid;
	}

	private static String getReason(Element item) {
		Element r = item.getChild("reason");
		return r == null ? null : r.getCData();
	}

	private static Role getRole(Element item) {
		String tmp = item.getAttribute("role");
		return tmp == null ? null : Role.valueOf(tmp);
	}

	public ModeratorModule(MucConfig config, IMucRepository mucRepository) {
		super(config, mucRepository);
	}

	private void checkItem(final Room room, final Element item, final Affiliation senderaAffiliation, final Role senderRole)
			throws MUCException {
		final Role newRole = getRole(item);
		final Affiliation newAffiliation = getAffiliation(item);
		final String occupantJid = getOccupantJidFromItem(room, item);
		final Affiliation occupantAffiliation = room.getAffiliation(occupantJid);
		final Role occupantRole = room.getRoleByJid(occupantJid);

		if (newRole != null && newAffiliation == null) {
			if (newRole == Role.none && !senderRole.isKickParticipantsAndVisitors()) {
				throw new MUCException(Authorization.NOT_ALLOWED, "You cannot kick");
			} else if (newRole == Role.none && occupantAffiliation.getWeight() >= senderaAffiliation.getWeight()) {
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
			} else if (newAffiliation == Affiliation.outcast && occupantAffiliation.getWeight() >= senderaAffiliation.getWeight()) {
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

			if (newAffiliation == Affiliation.none && occupantAffiliation.getWeight() >= senderaAffiliation.getWeight()) {
				throw new MUCException(Authorization.NOT_ALLOWED, "You remove affiliation occupant with higher affiliation");
			}

		} else {
			throw new MUCException(Authorization.BAD_REQUEST);
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

	private Element makePresence(final String destinationJid, final String roomURL, final Room room, final String occupantJid,
			boolean unavailable, Affiliation affiliation, Role role, String nick, String reason, String actor, String... codes) {
		Element presence = unavailable ? new Element("presence", new String[] { "type" }, new String[] { "unavailable" })
				: room.getLastPresenceCopyByJid(occupantJid);

		presence.setAttribute("from", roomURL + "/" + nick);
		presence.setAttribute("to", destinationJid);

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
	public List<Element> process(Element element) throws MUCException {
		try {
			final String type = element.getAttribute("type");
			if ("set".equals(type)) {
				return processSet(element);
			} else if ("get".equals(type)) {
				return processGet(element);
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

	private List<Element> processGet(Element element) throws RepositoryException, MUCException {
		final String roomId = getRoomId(element.getAttribute("to"));

		Room room = repository.getRoom(roomId);
		if (room == null) {
			throw new MUCException(Authorization.ITEM_NOT_FOUND);
		}

		final Element query = element.getChild("query");
		final Element item = query.getChild("item");
		final String senderJid = element.getAttribute("from");

		final Role senderRole = room.getRoleByJid(senderJid);
		final Affiliation senderAffiliation = room.getAffiliation(senderJid);

		if (senderAffiliation != Affiliation.admin && senderAffiliation != Affiliation.owner) {
			throw new MUCException(Authorization.FORBIDDEN);
		}

		final Role filterRole = getRole(item);
		final Affiliation filterAffiliation = getAffiliation(item);

		Element response = createResultIQ(element);
		Element responseQuery = new Element("query", new String[] { "xmlns" },
				new String[] { "http://jabber.org/protocol/muc#admin" });
		response.addChild(responseQuery);

		if (filterAffiliation != null && filterRole == null) {
			for (String jid : room.getAffiliations()) {
				final Affiliation affiliation = room.getAffiliation(jid);
				if (affiliation == filterAffiliation) {
					final String nickName = room.getOccupantsNicknameByBareJid(jid);
					final String fullJid = room.getOccupantsJidByNickname(nickName);
					final Role role = room.getRoleByJid(fullJid);
					Element ir = new Element("item", new String[] { "affiliation", "jid" },
							new String[] { affiliation.name(), jid });
					if (nickName != null) {
						ir.setAttribute("nick", nickName);
						ir.setAttribute("role", role.name());
					}
					responseQuery.addChild(ir);
				}
			}
		} else if (filterAffiliation == null && filterRole != null) {
			for (String jid : room.getOccupantsJids()) {
				final Role role = room.getRoleByJid(jid);
				if (role == filterRole) {
					final Affiliation affiliation = room.getAffiliation(jid);
					final String nick = room.getOccupantsNickname(jid);
					Element ir = new Element("item", new String[] { "affiliation", "nick", "role" }, new String[] {
							affiliation.name(), nick, role.name() });
					if (room.getConfig().getRoomAnonymity() != Anonymity.fullanonymous) {
						ir.setAttribute("jid", jid);
					}
					responseQuery.addChild(ir);
				}
			}
		} else {
			throw new MUCException(Authorization.BAD_REQUEST);
		}

		return makeArray(response);
	}

	private List<Element> processSet(Element element) throws RepositoryException, MUCException {
		final String roomId = getRoomId(element.getAttribute("to"));
		final Room room = repository.getRoom(roomId);
		if (room == null) {
			throw new MUCException(Authorization.ITEM_NOT_FOUND);
		}

		final String senderJid = element.getAttribute("from");
		final Affiliation senderAffiliation = room.getAffiliation(senderJid);
		final Role senderRole = room.getRoleByJid(senderJid);

		final Element query = element.getChild("query");
		final List<Element> items = query.getChildren();

		for (Element item : items) {
			checkItem(room, item, senderAffiliation, senderRole);
		}

		final List<Element> result = makeArray(createResultIQ(element));

		for (Element item : items) {
			final Role newRole = getRole(item);
			final Affiliation newAffiliation = getAffiliation(item);
			final String reason = getReason(item);

			final String actor = JIDUtils.getNodeID(senderJid);

			if (newAffiliation != null) {
				final String occupantBareJid = item.getAttribute("jid");
				final Affiliation previuosAffiliation = room.getAffiliation(occupantBareJid);

				room.addAffiliationByJid(occupantBareJid, newAffiliation);
				String[] realOccupantJids = room.getRealJidsByBareJid(occupantBareJid);

				for (String realJid : realOccupantJids) {
					final String occupantNick = room.getOccupantsNickname(realJid);
					final Role currentRole = room.getRoleByJid(realJid);
					List<String> codes = new ArrayList<String>();
					boolean isUnavailable = false;
					if (newAffiliation == Affiliation.outcast) {
						codes.add("301");
						isUnavailable = true;
						Element occupantKickPresence = makePresence(realJid, roomId, room, realJid, isUnavailable, newAffiliation,
								newRole, occupantNick, reason, actor, codes.toArray(new String[] {}));
						room.removeOccupantByJid(realJid);
						result.add(occupantKickPresence);
					}

					if (newAffiliation.isViewOccupantsJid() != previuosAffiliation.isViewOccupantsJid()) {
						// TODO send all occupant presences to this occupant?
					}

					// sending presence to all occupants
					for (String jid : room.getOccupantsJids()) {
						Element occupantPresence = makePresence(jid, roomId, room, realJid, isUnavailable, newAffiliation,
								currentRole, occupantNick, reason, null, codes.toArray(new String[] {}));
						result.add(occupantPresence);
					}

				}

			}

			if (newRole != null) {
				final String occupantJid = getOccupantJidFromItem(room, item);
				final String occupantNick = room.getOccupantsNickname(occupantJid);
				final Affiliation occupantAffiliation = room.getAffiliation(occupantJid);
				boolean isUnavailable = false;

				List<String> codes = new ArrayList<String>();

				if (newRole == Role.none) {
					codes.add("307");
					isUnavailable = true;

					Element occupantKickPresence = makePresence(occupantJid, roomId, room, occupantJid, isUnavailable,
							occupantAffiliation, newRole, occupantNick, reason, actor, codes.toArray(new String[] {}));
					room.removeOccupantByJid(occupantJid);
					result.add(occupantKickPresence);
				} else {
					room.setNewRole(occupantJid, newRole);
				}

				// sending presence to all occupants
				for (String jid : room.getOccupantsJids()) {
					Element occupantPresence = makePresence(jid, roomId, room, occupantJid, isUnavailable, occupantAffiliation,
							newRole, occupantNick, reason, null, codes.toArray(new String[] {}));
					result.add(occupantPresence);
				}

			}

		}

		return result;
	}

}
