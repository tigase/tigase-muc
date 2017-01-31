/*
 * PresenceModule.java
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

import java.util.ArrayList;
import java.util.Collection;

import tigase.component.modules.Module;
import tigase.muc.Affiliation;
import tigase.muc.Role;
import tigase.muc.Room;
import tigase.muc.RoomConfig.Anonymity;
import tigase.server.Packet;
import tigase.server.Priority;
import tigase.util.TigaseStringprepException;
import tigase.xml.Element;
import tigase.xmpp.BareJID;
import tigase.xmpp.JID;

/**
 * @author bmalkow
 * 
 */
public interface PresenceModule extends Module {

	/**
	 * Class description
	 * 
	 * 
	 * @version Enter version here..., 13/02/20
	 * @author Enter your name here...
	 */
	public static class PresenceWrapper {

		/**
		 * Method description
		 * 
		 * 
		 * @param room
		 * @param destinationJID
		 * @param presence
		 * @param occupantBareJID
		 * @param occupantNickname
		 * @param occupantAffiliation
		 * @param occupantRole
		 * 
		 * @return
		 * 
		 * @throws TigaseStringprepException
		 */
		public static PresenceWrapper preparePresenceW(Room room, JID destinationJID, final Element presence,
				BareJID occupantBareJID, Collection<JID> occupantJIDs, String occupantNickname,
				Affiliation occupantAffiliation, Role occupantRole) throws TigaseStringprepException {
			Anonymity anonymity = room.getConfig().getRoomAnonymity();
			final Affiliation destinationAffiliation = room.getAffiliation(destinationJID.getBareJID());

			try {
				presence.setAttribute("from", JID.jidInstance(room.getRoomJID(), occupantNickname).toString());
			} catch (TigaseStringprepException e) {
				presence.setAttribute("from", room.getRoomJID() + "/" + occupantNickname);
			}
			presence.setAttribute("to", destinationJID.toString());

			Element x = new Element("x", new String[] { "xmlns" }, new String[] { "http://jabber.org/protocol/muc#user" });

			final ArrayList<Element> items = new ArrayList<Element>();
			for (JID jid : occupantJIDs) {
				Element item = new Element("item", new String[] { "affiliation", "role", "nick" }, new String[] {
						occupantAffiliation.name(), occupantRole.name(), occupantNickname });
				x.addChild(item);
				items.add(item);

				if ((anonymity == Anonymity.nonanonymous)
						|| ((anonymity == Anonymity.semianonymous) && destinationAffiliation.isViewOccupantsJid())) {
					item.setAttribute("jid", jid.toString());
				} else
					break;

			}

			presence.addChild(x);

			Packet packet = Packet.packetInstance(presence);
			packet.setXMLNS(Packet.CLIENT_XMLNS);
			PresenceWrapper wrapper = new PresenceWrapper(packet, x, items.toArray(new Element[] {}));

			if (occupantBareJID != null && occupantBareJID.equals(destinationJID.getBareJID())) {
				wrapper.packet.setPriority(Priority.HIGH);
				wrapper.addStatusCode(110);
				if (anonymity == Anonymity.nonanonymous) {
					wrapper.addStatusCode(100);
				}
				if (room.getConfig().isLoggingEnabled()) {
					wrapper.addStatusCode(170);
				}
			}

			return wrapper;
		}

		static PresenceWrapper preparePresenceW(Room room, JID destinationJID, final Element presence, BareJID occupantJID,
				String occupantNickname, Affiliation occupantAffiliation, Role occupantRole) throws TigaseStringprepException {
			final Collection<JID> occupantJIDs = room.getOccupantsJidsByNickname(occupantNickname);
			return preparePresenceW(room, destinationJID, presence, occupantJID, occupantJIDs, occupantNickname,
					occupantAffiliation, occupantRole);
		}

		/**
		 * Method description
		 * 
		 * 
		 * @param room
		 * @param destinationJID
		 * @param presence
		 * @param occupantJID
		 * 
		 * @return
		 * 
		 * @throws TigaseStringprepException
		 */
		static PresenceWrapper preparePresenceW(Room room, JID destinationJID, final Element presence, JID occupantJID)
				throws TigaseStringprepException {
			final String occupantNickname = room.getOccupantsNickname(occupantJID);

			return preparePresenceW(room, destinationJID, presence, occupantNickname);
		}

		/**
		 * Method description
		 * 
		 * 
		 * @param room
		 * @param destinationJID
		 * @param presence
		 * @param occupantNickname
		 * 
		 * @return
		 * 
		 * @throws TigaseStringprepException
		 */
		static PresenceWrapper preparePresenceW(Room room, JID destinationJID, final Element presence, String occupantNickname)
				throws TigaseStringprepException {
			final BareJID occupantJID = room.getOccupantsJidByNickname(occupantNickname);
			final Affiliation occupantAffiliation = room.getAffiliation(occupantJID);
			final Role occupantRole = room.getRole(occupantNickname);

			return preparePresenceW(room, destinationJID, presence, occupantJID, occupantNickname, occupantAffiliation,
					occupantRole);
		}

		final Element[] items;
		final Packet packet;
		final Element x;

		PresenceWrapper(Packet packet, Element x, Element[] items) {
			this.packet = packet;
			this.x = x;
			this.items = items;
		}

		/**
		 * Method description
		 * 
		 * 
		 * @param code
		 */
		void addStatusCode(int code) {
			x.addChild(new Element("status", new String[] { "code" }, new String[] { "" + code }));
		}

		public Packet getPacket() {
			return packet;
		}

		public Element getX() {
			return x;
		}
	}

	public static final String ID = "presences";

	/**
	 * @param r
	 * @param source
	 */
	void doQuit(final Room room, final JID senderJID) throws TigaseStringprepException;

	/**
	 * @param room
	 * @param occupantJID
	 */
	public void sendPresencesToNewOccupant(Room room, JID senderJID) throws TigaseStringprepException;

}