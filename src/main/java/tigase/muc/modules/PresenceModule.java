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

import tigase.component.modules.Module;
import tigase.muc.Affiliation;
import tigase.muc.Role;
import tigase.muc.Room;
import tigase.muc.RoomConfig.Anonymity;
import tigase.server.Packet;
import tigase.server.Priority;
import tigase.util.stringprep.TigaseStringprepException;
import tigase.xml.Element;
import tigase.xmpp.jid.BareJID;
import tigase.xmpp.jid.JID;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

/**
 * @author bmalkow
 */
public interface PresenceModule
		extends Module {

	String ID = "presences";

	void doQuit(final Room room, final JID senderJID) throws TigaseStringprepException;

	void sendPresencesToNewOccupant(Room room, JID senderJID) throws TigaseStringprepException;

	class PresenceWrapper {

		/**
		 * Inform user that any occupant is allowed to see the user's full JID.
		 */
		public static final int STATUS_CODE_OCCUPANT_IS_ALLOWED_TO_SEE_JID = 100;
		/**
		 * Inform user that presence refers to itself.
		 */
		public static final int STATUS_CODE_SELF_PRESENCE = 110;
		/**
		 * Inform occupants that room logging is now enabled.
		 */
		public static final int STATUS_CODE_ROOM_LOGGING_IS_ENABLED = 170;
		/**
		 * Inform occupants that room logging is now disabled.
		 */
		public static final int STATUS_CODE_ROOM_LOGGING_IS_DISABLED = 171;
		/**
		 * Inform user that a new room has been created.
		 */
		public static final int STATUS_CODE_NEW_ROOM = 201;
		/**
		 * Inform all occupants of new room nickname.
		 */
		public static final int STATUS_CODE_NEW_NICKNAME = 303;

		final Element[] items;
		final Packet packet;
		final Element x;

		public static PresenceWrapper preparePresenceW(Room room, JID destinationJID, final Element presence,
													   BareJID occupantBareJID, Collection<JID> occupantJIDs,
													   String occupantNickname, Affiliation occupantAffiliation,
													   Role occupantRole) throws TigaseStringprepException {
			Anonymity anonymity = room.getConfig().getRoomAnonymity();
			final Affiliation destinationAffiliation = room.getAffiliation(destinationJID.getBareJID())
					.getAffiliation();

			try {
				presence.setAttribute("from", JID.jidInstance(room.getRoomJID(), occupantNickname).toString());
			} catch (TigaseStringprepException e) {
				presence.setAttribute("from", room.getRoomJID() + "/" + occupantNickname);
			}
			presence.setAttribute("to", destinationJID.toString());

			Element x = new Element("x", new String[]{"xmlns"}, new String[]{"http://jabber.org/protocol/muc#user"});

			final ArrayList<Element> items = new ArrayList<Element>();

			if ((anonymity == Anonymity.nonanonymous) ||
					((anonymity == Anonymity.semianonymous) && destinationAffiliation.isViewOccupantsJid())) {
				for (JID jid : occupantJIDs) {
					Element item = new Element("item", new String[]{"affiliation", "role", "nick", "jid"},
											   new String[]{occupantAffiliation.name(), occupantRole.name(),
															occupantNickname, jid.toString()});
					x.addChild(item);
					items.add(item);
				}
			} else {
				Element item = new Element("item", new String[]{"affiliation", "role", "nick"},
										   new String[]{occupantAffiliation.name(), occupantRole.name(),
														occupantNickname});
				x.addChild(item);
				items.add(item);
			}

			presence.addChild(x);

			Packet packet = Packet.packetInstance(presence);
			packet.setXMLNS(Packet.CLIENT_XMLNS);
			PresenceWrapper wrapper = new PresenceWrapper(packet, x, items.toArray(new Element[]{}));

			if (occupantBareJID != null && occupantBareJID.equals(destinationJID.getBareJID())) {
				wrapper.packet.setPriority(Priority.HIGH);
				wrapper.addStatusCode(PresenceWrapper.STATUS_CODE_SELF_PRESENCE);
				if (anonymity == Anonymity.nonanonymous) {
					wrapper.addStatusCode(PresenceWrapper.STATUS_CODE_OCCUPANT_IS_ALLOWED_TO_SEE_JID);
				}
				if (room.getConfig().isLoggingEnabled()) {
					wrapper.addStatusCode(PresenceWrapper.STATUS_CODE_ROOM_LOGGING_IS_ENABLED);
				}
			}

			return wrapper;
		}

		static PresenceWrapper preparePresenceW(Room room, JID destinationJID, final Element presence,
												BareJID occupantJID, String occupantNickname,
												Affiliation occupantAffiliation, Role occupantRole)
				throws TigaseStringprepException {
			final Collection<JID> occupantJIDs = room.getOccupantsJidsByNickname(occupantNickname);
			return preparePresenceW(room, destinationJID, presence, occupantJID, occupantJIDs, occupantNickname,
									occupantAffiliation, occupantRole);
		}

		static PresenceWrapper preparePresenceW(Room room, JID destinationJID, final Element presence, JID occupantJID)
				throws TigaseStringprepException {
			final String occupantNickname = room.getOccupantsNickname(occupantJID);
			if (occupantNickname == null) {
				final Affiliation occupantAffiliation = room.getAffiliation(occupantJID.getBareJID()).getAffiliation();

				return preparePresenceW(room, destinationJID, presence, occupantJID.getBareJID(),
										Collections.singleton(occupantJID), occupantJID.getBareJID().toString(),
										occupantAffiliation, Role.none);
			} else {
				return preparePresenceW(room, destinationJID, presence, occupantNickname);
			}
		}

		static PresenceWrapper preparePresenceW(Room room, JID destinationJID, final Element presence,
												String occupantNickname) throws TigaseStringprepException {
			final BareJID occupantJID = room.getOccupantsJidByNickname(occupantNickname);
			final Affiliation occupantAffiliation = room.getAffiliation(occupantJID).getAffiliation();
			final Role occupantRole = room.getRole(occupantNickname);

			return preparePresenceW(room, destinationJID, presence, occupantJID, occupantNickname, occupantAffiliation,
									occupantRole);
		}

		PresenceWrapper(Packet packet, Element x, Element[] items) {
			this.packet = packet;
			this.x = x;
			this.items = items;
		}

		public Packet getPacket() {
			return packet;
		}

		public Element getX() {
			return x;
		}

		public void addStatusCode(final int code) {
			final String codeToSet = String.valueOf(code).intern();
			boolean alreadySet = x.getChildren(
					element -> "status" == element.getName() && element.getAttributeStaticStr("code") == codeToSet) !=
					null;

			if (!alreadySet) {
				x.addChild(new Element("status", new String[]{"code"}, new String[]{codeToSet}));
			}
		}
	}

}