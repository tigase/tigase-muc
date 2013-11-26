/*
 * InMemoryMucRepositoryClustered.java
 *
 * Tigase Jabber/XMPP Server
 * Copyright (C) 2004-2013 "Tigase, Inc." <office@tigase.com>
 *
 */
package tigase.muc.cluster;

import tigase.component.exceptions.RepositoryException;
import tigase.muc.MucConfig;
import tigase.muc.Room;
import tigase.muc.repository.MucDAO;
import tigase.muc.repository.inmemory.InMemoryMucRepository;
import tigase.xmpp.BareJID;
import tigase.xmpp.JID;

/**
 *
 * @author andrzej
 */
public class InMemoryMucRepositoryClustered extends InMemoryMucRepository {
	
	public static interface RoomListener {
		void onRoomCreated(Room room);
		
		void onRoomDestroyed(Room room);
	}
	
	private RoomListener roomListener;
	private Room.RoomOccupantListener roomOccupantListener;

	public InMemoryMucRepositoryClustered(final MucConfig mucConfig, final MucDAO dao) throws RepositoryException {
		super(mucConfig, dao);
	}
		
	@Override
	public Room createNewRoom(BareJID roomJID, JID senderJid) throws RepositoryException {
		Room room = super.createNewRoom(roomJID, senderJid);
		if (roomOccupantListener != null) {
			room.addOccupantListener(roomOccupantListener);
		}
		if (roomListener != null) {
			roomListener.onRoomCreated(room);
		}
		return room;
	}
	
	@Override
	public void destroyRoom(Room room) throws RepositoryException {
		super.destroyRoom(room);
		
		if (roomListener != null) {
			roomListener.onRoomDestroyed(room);
		}
	}

	public void setRoomListener(RoomListener roomListener) {
		this.roomListener = roomListener;
	}
	
	public void setRoomOccupantListener(Room.RoomOccupantListener roomOccupantListener) {
		this.roomOccupantListener = roomOccupantListener;
	}
}
