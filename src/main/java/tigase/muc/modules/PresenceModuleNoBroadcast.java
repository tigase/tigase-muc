/*
 * PresenceModuleNoBroadcast.java
 *
 * Tigase Jabber/XMPP Server
 * Copyright (C) 2004-2013 "Tigase, Inc." <office@tigase.com>
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
import java.util.logging.Level;
import java.util.logging.Logger;

import tigase.criteria.Criteria;
import tigase.criteria.ElementCriteria;
import tigase.muc.Affiliation;
import tigase.muc.Role;
import tigase.muc.Room;
import tigase.muc.exceptions.MUCException;
import tigase.muc.history.HistoryProvider;
import tigase.util.TigaseStringprepException;
import tigase.xml.Element;
import tigase.xmpp.JID;

/**
 * Class for MucPresenceModule that strips down generated presence stanzas to
 * bare minimum - only sends back presence to user that joined the room for
 * compatibility reasons.
 *
 * @author wojtek
 */
public class PresenceModuleNoBroadcast extends PresenceModuleImpl {

	protected static final Logger log = Logger.getLogger(PresenceModuleNoBroadcast.class.getName());
	private static final Criteria CRIT = ElementCriteria.name("presence");

	@Override
	public void doQuit(final Room room, final JID senderJID) throws TigaseStringprepException {
		final String leavingNickname = room.getOccupantsNickname(senderJID);
		final Affiliation leavingAffiliation = room.getAffiliation(leavingNickname);
		final Role leavingRole = room.getRole(leavingNickname);
		Element presenceElement = new Element("presence");

		presenceElement.setAttribute("type", "unavailable");

		Collection<JID> occupantJIDs = new ArrayList<JID>(room.getOccupantsJidsByNickname(leavingNickname));
		context.getGhostbuster().remove(senderJID, room);

		room.updatePresenceByJid(senderJID, leavingNickname, null);

		if (context.isMultiItemMode()) {
			final PresenceWrapper selfPresence = PresenceWrapper.preparePresenceW(room, senderJID, presenceElement,
					senderJID.getBareJID(), occupantJIDs, leavingNickname, leavingAffiliation, leavingRole);
			write(selfPresence.getPacket());
		} else {
			Collection<JID> z = new ArrayList<JID>(1);
			z.add(senderJID);

			final PresenceWrapper selfPresence = PresenceWrapper.preparePresenceW(room, senderJID, presenceElement,
					senderJID.getBareJID(), z, leavingNickname, leavingAffiliation, leavingRole);
			write(selfPresence.getPacket());
		}

		if (room.getOccupantsCount() == 0) {
			HistoryProvider historyProvider = context.getHistoryProvider();
			if ((historyProvider != null)) {
				if (log.isLoggable(Level.FINE))
					log.fine("Removing history of room " + room.getRoomJID());
				historyProvider.removeHistory(room);
			} else if (log.isLoggable(Level.FINE))
				log.fine("Cannot remove history of room " + room.getRoomJID() + " because history provider is not available.");
			context.getMucRepository().leaveRoom(room);

			Element emptyRoomEvent = new Element("EmptyRoom", new String[]{"xmlns"}, new String[]{"tigase:events:muc"});
			emptyRoomEvent.addChild(new Element("room", room.getRoomJID().toString()));
			fireEvent(emptyRoomEvent);
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

	@Override
	protected void processExit(Room room, Element presenceElement, JID senderJID)
			throws MUCException, TigaseStringprepException {
		super.processExit(room, presenceElement, senderJID);

	}

	@Override
	protected void sendPresenceToAllOccupants(final Element $presence, Room room, JID senderJID, boolean newRoomCreated,
			String newNickName) throws TigaseStringprepException {

		// send presence only back to the joining user
		PresenceWrapper presence = super.preparePresence(senderJID, $presence.clone(), room, senderJID, newRoomCreated,
				newNickName);
		write(presence.getPacket());

	}

	@Override
	public void sendPresencesToNewOccupant(Room room, JID senderJID) throws TigaseStringprepException {
		// do nothing
	}

}
