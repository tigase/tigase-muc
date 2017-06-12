/*
 * DiscoveryModule.java
 *
 * Tigase Multi User Chat Component
 * Copyright (C) 2004-2016 "Tigase, Inc." <office@tigase.com>
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
 */
package tigase.muc.modules;

import tigase.component.exceptions.ComponentException;
import tigase.component.exceptions.RepositoryException;
import tigase.muc.*;
import tigase.muc.exceptions.MUCException;
import tigase.server.Packet;
import tigase.util.DateTimeFormatter;
import tigase.xml.Element;
import tigase.xmpp.Authorization;
import tigase.xmpp.BareJID;
import tigase.xmpp.JID;

import java.util.Map;
import java.util.logging.Level;

/**
 * @author bmalkow
 */
public class DiscoveryModule extends tigase.component.modules.impl.DiscoveryModule<MucContext> {

	private final DateTimeFormatter dtf = new DateTimeFormatter();
	private DiscoItemsFilter filter = null;//new DefaultDiscoItemsFilter();

	private static void addFeature(Element query, String feature) {
		query.addChild(new Element("feature", new String[] { "var" }, new String[] { feature }));
	}

	private void addField(Element form, String var, String type, String label, Object... value) {
		if (value == null)
			return;
		Element f = new Element("field");
		if (type != null)
			f.setAttribute("type", type);
		if (var != null)
			f.setAttribute("var", var);
		if (label != null)
			f.setAttribute("label", label);

		for (Object o : value) {
			Element v = new Element("value");
			if (o instanceof Boolean) {
				v.setCData(((Boolean) o ? "1" : "0"));
			} else if (o != null) {
				v.setCData(o.toString());
			}
			f.addChild(v);
		}
		form.addChild(f);
	}

	private void addRoomInfoForm(final Element resultQuery, final Room room, final JID senderJID) {
		final RoomConfig config = room.getConfig();
		final Affiliation senderAffiliation = room.getAffiliation(senderJID.getBareJID());

		final boolean allowedToViewAll;
		if (!room.getOccupantsNicknames(senderJID.getBareJID()).isEmpty()) {
			allowedToViewAll = true;
		} else if (senderAffiliation.isEnterMembersOnlyRoom()) {
			allowedToViewAll = true;
		} else
			allowedToViewAll = false;

		if (!config.isRoomconfigPublicroom() && !allowedToViewAll)
			return;

		if (config.isRoomMembersOnly() && !allowedToViewAll)
			return;

		final Element form = new Element("x", new String[] { "xmlns", "type" }, new String[] { "jabber:x:data", "result" });
		addField(form, "FORM_TYPE", "hidden", null, "http://jabber.org/protocol/muc#roominfo");

		// text-single Room creation date
		addField(form, "muc#roominfo_creationdate", null, "Room creation date", dtf.formatDateTime(room.getCreationDate()));
		// text-single Current Discussion Topic
		addField(form, "muc#roominfo_occupants", null, "Number of occupants", room.getOccupantsCount());
		// text-single Current Discussion Topic
		addField(form, "muc#roominfo_subject", null, "Current discussion topic", room.getSubject());
		// boolean Whether to Allow Occupants to Invite Others
		addField(form, "muc#roomconfig_allowinvites", null, "Whether occupants allowed to invite others", config.isInvitingAllowed());
		// boolean Whether to Allow Occupants to Change Subject
		addField(form, "muc#roomconfig_changesubject", null, "Whether occupants may change the subject",
				 config.isChangeSubject());
		// boolean Whether to Enable Logging of Room Conversations
		addField(form, "muc#roomconfig_enablelogging", null, "Whether logging is enabled", config.isLoggingEnabled());
		// text-single Natural Language for Room Discussions
		addField(form, "muc#roomconfig_lang", null, "Natural language room name", config.getRoomName());
		// list-single Maximum Number of Room Occupants
		addField(form, "muc#roomconfig_maxusers", null, "Maximum number of room occupants", config.getMaxUsers());
		// boolean Whether an Make Room Members-Only
		addField(form, "muc#roomconfig_membersonly", null, "Whether room is members-only", config.isRoomMembersOnly());
		// boolean Whether to Make Room Moderated
		addField(form, "muc#roomconfig_moderatedroom", null, "Whether room is moderated", config.isRoomModerated());
		// boolean Whether a Password is Required to Enter
		addField(form, "muc#roomconfig_passwordprotectedroom", null, "Whether a password is required to enter",
				 config.isPasswordProtectedRoom());
		// boolean Whether to Make Room Persistent
		addField(form, "muc#roomconfig_persistentroom", null, "Whether room is persistent", config.isPersistentRoom());
		// list-multi Roles for which Presence is Broadcast
		addField(form, "muc#roomconfig_presencebroadcast", null, "Roles for which presence is broadcast", Role.moderator.name(),
				 Role.participant.name(), Role.visitor.name());
		// boolean Whether to Allow Public Searching for Room
		addField(form, "muc#roomconfig_publicroom", null, "Whether room is publicly searchable",
				 config.isRoomconfigPublicroom());
		// jid-multi Full List of Room Admins
		addField(form, "muc#roomconfig_roomadmins", null, "Full list of room admins",
				 room.getAffiliations().stream().filter(jid -> room.getAffiliation(jid) == Affiliation.admin).toArray());
		// text-single Short Description of Room
		addField(form, "muc#roomconfig_roomdesc", null, "Short description of room", config.getRoomDesc());
		// text-single Natural-Language Room Name
		addField(form, "muc#roomconfig_roomname", null, "Natural language room name", config.getRoomName());
		// jid-multi Full List of Room Owners
		addField(form, "muc#roomconfig_roomowners", null, "Full list of room owners",
				 room.getAffiliations().stream().filter(jid -> room.getAffiliation(jid) == Affiliation.owner).toArray());
		// text-private The Room Password
		if (allowedToViewAll && config.isPasswordProtectedRoom())
			addField(form, "muc#roomconfig_roomsecret", null, "The room password", config.getPassword());

		// list-single Affiliations that May Discover Real JIDs of Occupants
		RoomConfig.Anonymity anonymity = config.getRoomAnonymity();
		Object[] whois;
		switch (anonymity) {
			case nonanonymous:
				whois = new String[] { Affiliation.owner.name(), Affiliation.admin.name(), Affiliation.member.name(),
									   Affiliation.none.name() };
			case semianonymous:
				whois = new String[] { Affiliation.owner.name(), Affiliation.admin.name() };
			default:
				whois = null;
		}
		addField(form, "muc#roomconfig_whois", null, "Affiliations that may discover real jIDs of occupants", whois);

		resultQuery.addChild(form);
	}

	public DiscoItemsFilter getFilter() {
		return filter;
	}

	public void setFilter(DiscoItemsFilter filter) {
		this.filter = filter;
		if (log.isLoggable(Level.FINER))
			log.finer("New discoItems filter is set: " + filter);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see
	 * tigase.component.modules.impl.DiscoveryModule#processDiscoInfo(tigase
	 * .server.Packet, tigase.xmpp.JID, java.lang.String, tigase.xmpp.JID)
	 */
	@Override
	protected void processDiscoInfo(Packet packet, JID requestedJID, String node, JID senderJID)
			throws ComponentException, RepositoryException {
		if ((node == null) && (requestedJID.getLocalpart() == null) && (requestedJID.getResource() == null)) {
			if (log.isLoggable(Level.FINER))
				log.finer("Requested component info");

			super.processDiscoInfo(packet, requestedJID, node, senderJID);
		} else if ((node == null) && (requestedJID.getLocalpart() != null) && (requestedJID.getResource() == null)) {
			if (log.isLoggable(Level.FINER))
				log.finer("Requested room " + requestedJID.getBareJID() + " info");

			Element resultQuery = new Element("query", new String[] { "xmlns" },
											  new String[] { "http://jabber.org/protocol/disco#info" });

			Room room = context.getMucRepository().getRoom(requestedJID.getBareJID());

			if (room == null) {
				throw new MUCException(Authorization.ITEM_NOT_FOUND);
			}

			String roomName = room.getConfig().getRoomName();
			Element resultIdentity = new Element("identity", new String[] { "category", "name", "type" },
												 new String[] { "conference", (roomName == null) ? "" : roomName, "text" });

			resultQuery.addChild(resultIdentity);
			addFeature(resultQuery, "http://jabber.org/protocol/muc");
			switch (room.getConfig().getRoomAnonymity()) {
				case fullanonymous:
					addFeature(resultQuery, "muc_fullyanonymous");

					break;

				case semianonymous:
					addFeature(resultQuery, "muc_semianonymous");

					break;

				case nonanonymous:
					addFeature(resultQuery, "muc_nonanonymous");

					break;
			}
			if (room.getConfig().isRoomModerated()) {
				addFeature(resultQuery, "muc_moderated");
			} else {
				addFeature(resultQuery, "muc_unmoderated");
			}
			if (room.getConfig().isRoomMembersOnly()) {
				addFeature(resultQuery, "muc_membersonly");
			} else {
				addFeature(resultQuery, "muc_open");
			}
			if (room.getConfig().isPersistentRoom()) {
				addFeature(resultQuery, "muc_persistent");
			} else {
				addFeature(resultQuery, "muc_temporary");
			}
			if (!room.getConfig().isRoomconfigPublicroom()) {
				addFeature(resultQuery, "muc_hidden");
			} else {
				addFeature(resultQuery, "muc_public");
			}
			if (room.getConfig().isPasswordProtectedRoom()) {
				addFeature(resultQuery, "muc_passwordprotected");
			} else {
				addFeature(resultQuery, "muc_unsecured");
			}

			addRoomInfoForm(resultQuery, room, senderJID);

			write(packet.okResult(resultQuery, 0));
		} else if ((node == null) && (requestedJID.getLocalpart() != null) && (requestedJID.getResource() != null)) {
			write(packet.okResult((Element) null, 0));
		} else {
			throw new MUCException(Authorization.BAD_REQUEST);
		}
	}

	@Override
	protected void processDiscoItems(Packet packet, JID requestedJID, String node, JID senderJID)
			throws ComponentException, RepositoryException {
		Element resultQuery = new Element("query", new String[] { Packet.XMLNS_ATT }, new String[] { DISCO_ITEMS_XMLNS });
		Packet result = packet.okResult(resultQuery, 0);

		if ((node == null) && (requestedJID.getLocalpart() == null) && (requestedJID.getResource() == null)) {

			if (log.isLoggable(Level.FINER))
				log.finer("Requested  list of rooms");

			// discovering rooms
			// (http://xmpp.org/extensions/xep-0045.html#disco-rooms)
			Map<BareJID, String> publicRooms = context.getMucRepository().getPublicVisibleRooms(requestedJID.getDomain());

			for (Map.Entry<BareJID, String> e : publicRooms.entrySet()) {
				BareJID jid = e.getKey();
				String name = e.getValue();
				if (filter != null) {
					final Room room = context.getMucRepository().getRoom(jid);

					if (room == null) {
						log.warning("Room " + jid + " is not available!");
						continue;
					} else if (room.getConfig() == null) {
						log.warning("Room " + jid + " hasn't configuration!");
						continue;
					} else {
						boolean fa = filter.allowed(senderJID, room);
						log.finest("Using filter " + filter + "; result(" + senderJID + ", " + room.getRoomJID() + ")=" +
										   fa);
						if (!fa) {
							log.fine("Room " + jid + " is filtered off");
							continue;
						}
					}
				}

				resultQuery.addChild(new Element("item", new String[] { "jid", "name" },
												 new String[] { jid.toString(), (name != null) ? name : jid.getLocalpart() }));
			}

//			BareJID[] roomsId = context.getMucRepository().getPublicVisibleRoomsIdList();
//
//			for (final BareJID jid : roomsId) {
//				if (jid.getDomain().equals(requestedJID.getDomain())) {
//					final String name = context.getMucRepository().getRoomName(jid.toString());
//
//					final Room room = context.getMucRepository().getRoom(jid);
//
//					if (log.isLoggable(Level.FINEST) && filter != null) {
//						boolean fa = filter.allowed(senderJID, room);
//						log.finest("Using filter " + filter + "; result(" + senderJID + ", " + room.getRoomJID() + ")=" + fa);
//					}
//
//					if (room == null) {
//						log.warning("Room " + jid + " is not available!");
//						continue;
//					} else if (room.getConfig() == null) {
//						log.warning("Room " + jid + " hasn't configuration!");
//						continue;
//					} else if (filter != null && !filter.allowed(senderJID, room)) {
//						log.fine("Room " + jid + " is filtered off");
//						continue;
//					}
//
//					if (log.isLoggable(Level.FINER))
//						log.finer("Room " + jid + " is added to response.");
//					resultQuery.addChild(new Element("item", new String[] { "jid", "name" },
//							new String[] { jid.toString(), (name != null) ? name : jid.getLocalpart() }));
//				}
//			}
		} else if ((node == null) && (requestedJID.getLocalpart() != null) && (requestedJID.getResource() == null)) {
			// querying for Room Items
			// (http://xmpp.org/extensions/xep-0045.html#disco-roomitems)

			if (log.isLoggable(Level.FINER))
				log.finer("Requested items list of room " + requestedJID.getBareJID());

			Room room = context.getMucRepository().getRoom(requestedJID.getBareJID());

			if (room == null) {
				throw new MUCException(Authorization.ITEM_NOT_FOUND);
			}

			String nickname = room.getOccupantsNickname(packet.getStanzaFrom());

			if (nickname == null) {
				throw new MUCException(Authorization.FORBIDDEN);
			}
			for (String nick : room.getOccupantsNicknames()) {
				resultQuery.addChild(new Element("item", new String[] { "jid", "name" },
												 new String[] { room.getRoomJID() + "/" + nick, nick }));
			}
		} else if ((node == null) && (requestedJID.getLocalpart() != null) && (requestedJID.getResource() != null)) {
			// Querying a Room Occupant
			write(packet.okResult((Element) null, 0));
		} else {
			throw new MUCException(Authorization.BAD_REQUEST);
		}
		write(result);
	}
}
