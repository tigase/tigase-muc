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
package tigase.muc;

import tigase.server.Packet;
import tigase.xml.Element;
import tigase.xmpp.jid.BareJID;
import tigase.xmpp.jid.JID;

import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Wojciech Kapcia
 */
public class PresenceFiltered
		implements Room.RoomOccupantListener, Room.RoomListener {

	protected static final Logger log = Logger.getLogger(PresenceFiltered.class.getName());
	private final Collection<BareJID> occupantsPresenceFiltered = new ConcurrentSkipListSet<>();
	private final Room room;

	public PresenceFiltered(Room room) {
		this.room = room;
	}

	public Collection<BareJID> getOccupantsPresenceFiltered() {
		return occupantsPresenceFiltered;
	}

	public Collection<JID> getOccupantsPresenceFilteredJIDs() {

		Collection<JID> occupantsJIDs = new ConcurrentSkipListSet<JID>();
		for (BareJID jid : occupantsPresenceFiltered) {
			for (String occupantsNickname : room.getOccupantsNicknames(jid)) {
				for (JID occuJID : room.getOccupantsJidsByNickname(occupantsNickname)) {
					occupantsJIDs.add(occuJID);
				}
			}
		}
		return occupantsJIDs;
	}

	public Collection<String> getOccupantsPresenceFilteredNicknames() {
		Collection<String> occupantsNicknames = new ConcurrentSkipListSet<String>();
		for (BareJID jid : occupantsPresenceFiltered) {
			for (String occupantsNickname : room.getOccupantsNicknames(jid)) {
				occupantsNicknames.add(occupantsNickname);
			}
		}
		return occupantsNicknames;
	}

	@Override
	public void onChangeSubject(Room room, String nick, String newSubject, Date changeDate) {
	}

	@Override
	public void onMessageToOccupants(Room room, JID from, Packet msg) {
	}

	@Override
	public void onOccupantAdded(Room room, JID occupantJid) {
		if (log.isLoggable(Level.FINEST)) {
			log.log(Level.FINEST, "Adding: " + occupantJid + " to occupantsPresenceFiltered: " +
					Arrays.asList(occupantsPresenceFiltered));
		}
		occupantsPresenceFiltered.add(occupantJid.getBareJID());
	}

	@Override
	public void onOccupantChangedPresence(Room room, JID occupantJid, String nickname, Element presence,
										  boolean newOccupant) {
		// we don't need to do anything here
	}

	@Override
	public void onOccupantRemoved(Room room, JID occupantJid) {
		if (log.isLoggable(Level.FINEST)) {
			log.log(Level.FINEST, "Removing: " + occupantJid + " to occupantsPresenceFiltered: " +
					Arrays.asList(occupantsPresenceFiltered));
		}
		occupantsPresenceFiltered.remove(occupantJid.getBareJID());
	}

	@Override
	public void onSetAffiliation(Room room, BareJID jid, Affiliation newAffiliation) {
		if (log.isLoggable(Level.FINEST)) {
			log.log(Level.FINEST, "Modifying affiliation of: " + jid + " on occupantsPresenceFiltered: " +
					Arrays.asList(occupantsPresenceFiltered));
		}
		Collection<Affiliation> presenceFilterFrom = room.getConfig().getPresenceFilteredAffiliations();
		if (presenceFilterFrom.contains(room.getAffiliation(jid))) {
			occupantsPresenceFiltered.add(jid);

		} else {
			occupantsPresenceFiltered.remove(jid);
		}
	}
}
