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

import tigase.component.exceptions.ComponentException;
import tigase.component.exceptions.RepositoryException;
import tigase.muc.Affiliation;
import tigase.muc.MucContext;
import tigase.muc.Room;
import tigase.muc.exceptions.MUCException;
import tigase.server.Packet;
import tigase.xml.Element;
import tigase.xmpp.Authorization;
import tigase.xmpp.BareJID;
import tigase.xmpp.JID;

/**
 * @author bmalkow
 * 
 */
public class DiscoveryModule extends tigase.component.modules.impl.DiscoveryModule<MucContext> {

	private static void addFeature(Element query, String feature) {
		query.addChild(new Element("feature", new String[] { "var" }, new String[] { feature }));
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * tigase.component.modules.impl.DiscoveryModule#processDiscoInfo(tigase
	 * .server.Packet, tigase.xmpp.JID, java.lang.String, tigase.xmpp.JID)
	 */
	@Override
	protected void processDiscoInfo(Packet packet, JID requestedJID, String node, JID senderJID) throws ComponentException,
			RepositoryException {
		if ((node == null) && (requestedJID.getLocalpart() == null) && (requestedJID.getResource() == null)) {
			super.processDiscoInfo(packet, requestedJID, node, senderJID);
		} else if ((node == null) && (requestedJID.getLocalpart() != null) && (requestedJID.getResource() == null)) {
			Element resultQuery = new Element("query", new String[] { "xmlns" },
					new String[] { "http://jabber.org/protocol/disco#info" });

			Room room = context.getMucRepository().getRoom(requestedJID.getBareJID());

			if (room == null) {
				throw new MUCException(Authorization.ITEM_NOT_FOUND);
			}

			String roomName = room.getConfig().getRoomName();
			Element resultIdentity = new Element("identity", new String[] { "category", "name", "type" }, new String[] {
					"conference", (roomName == null) ? "" : roomName, "text" });

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
			write(packet.okResult(resultQuery, 0));
		} else if ((node == null) && (requestedJID.getLocalpart() != null) && (requestedJID.getResource() != null)) {
			write(packet.okResult((Element) null, 0));
		} else {
			throw new MUCException(Authorization.BAD_REQUEST);
		}
	}

	@Override
	protected void processDiscoItems(Packet packet, JID requestedJID, String node, JID senderJID) throws ComponentException,
			RepositoryException {
		Element resultQuery = new Element("query", new String[] { Packet.XMLNS_ATT }, new String[] { DISCO_ITEMS_XMLNS });
		Packet result = packet.okResult(resultQuery, 0);

		if ((node == null) && (requestedJID.getLocalpart() == null) && (requestedJID.getResource() == null)) {
			// discovering rooms
			// (http://xmpp.org/extensions/xep-0045.html#disco-rooms)
			BareJID[] roomsId = context.getMucRepository().getPublicVisibleRoomsIdList();

			for (final BareJID jid : roomsId) {
				if (jid.getDomain().equals(requestedJID.getDomain())) {
					final String name = context.getMucRepository().getRoomName(jid.toString());

					final Room room = context.getMucRepository().getRoom(jid);
					if (room == null) {
						log.warning("Room " + jid + " is not available!");
						continue;
					} else if (room.getConfig() == null) {
						log.warning("Room " + jid + " hasn't configuration!");
						continue;
					} else if (!room.getConfig().isRoomconfigPublicroom()) {
						Affiliation senderAff = room.getAffiliation(senderJID.getBareJID());
						if (!room.isOccupantInRoom(senderJID)
								&& (senderAff == Affiliation.none || senderAff == Affiliation.outcast))
							continue;
					}

					resultQuery.addChild(new Element("item", new String[] { "jid", "name" }, new String[] { jid.toString(),
							(name != null) ? name : jid.getLocalpart() }));
				}
			}
		} else if ((node == null) && (requestedJID.getLocalpart() != null) && (requestedJID.getResource() == null)) {
			// querying for Room Items
			// (http://xmpp.org/extensions/xep-0045.html#disco-roomitems)
			Room room = context.getMucRepository().getRoom(requestedJID.getBareJID());

			if (room == null) {
				throw new MUCException(Authorization.ITEM_NOT_FOUND);
			}

			String nickname = room.getOccupantsNickname(packet.getStanzaFrom());

			if (nickname == null) {
				throw new MUCException(Authorization.FORBIDDEN);
			}
			for (String nick : room.getOccupantsNicknames()) {
				resultQuery.addChild(new Element("item", new String[] { "jid", "name" }, new String[] {
						room.getRoomJID() + "/" + nick, nick }));
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
