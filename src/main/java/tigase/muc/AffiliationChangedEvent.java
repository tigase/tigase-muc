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

import tigase.xmpp.jid.BareJID;

public class AffiliationChangedEvent {

	private BareJID jid;
	private RoomAffiliation newAffiliation;
	private RoomAffiliation oldAffiliation;
	private Room room;

	public AffiliationChangedEvent() {
	}
	public AffiliationChangedEvent(Room room, BareJID jid, RoomAffiliation oldAffiliation,
								   RoomAffiliation newAffiliation) {
		this.room = room;
		this.jid = jid;
		this.newAffiliation = newAffiliation;
		this.oldAffiliation = oldAffiliation;
	}

	public RoomAffiliation getNewAffiliation() {
		return newAffiliation;
	}

	public void setNewAffiliation(RoomAffiliation newAffiliation) {
		this.newAffiliation = newAffiliation;
	}

	public RoomAffiliation getOldAffiliation() {
		return oldAffiliation;
	}

	public void setOldAffiliation(RoomAffiliation oldAffiliation) {
		this.oldAffiliation = oldAffiliation;
	}

	public BareJID getJid() {
		return jid;
	}

	public void setJid(BareJID jid) {
		this.jid = jid;
	}

	public Room getRoom() {
		return room;
	}

	public void setRoom(Room room) {
		this.room = room;
	}
}
