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
