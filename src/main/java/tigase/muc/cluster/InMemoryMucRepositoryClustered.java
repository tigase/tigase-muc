/*
 * InMemoryMucRepositoryClustered.java
 *
 * Tigase Jabber/XMPP Server
 * Copyright (C) 2004-2013 "Tigase, Inc." <office@tigase.com>
 *
 */
package tigase.muc.cluster;

import tigase.component.exceptions.RepositoryException;
import tigase.muc.MucContext;
import tigase.muc.Room;
import tigase.muc.exceptions.MUCException;
import tigase.muc.repository.MucDAO;
import tigase.muc.repository.inmemory.InMemoryMucRepository;
import tigase.xml.Element;
import tigase.xmpp.BareJID;
import tigase.xmpp.JID;

import java.util.logging.Level;

/**
 *
 * @author andrzej
 */
public class InMemoryMucRepositoryClustered extends InMemoryMucRepository {
	
	public static interface RoomListener {
		void onRoomCreated(Room room);
		
		void onRoomDestroyed(Room room, Element destroyElement);

		void onLeaveRoom(Room room);
	}
	
	private RoomListener roomListener;
	private Room.RoomOccupantListener roomOccupantListener;

	public InMemoryMucRepositoryClustered(final MucContext mucConfig, final MucDAO dao) throws RepositoryException {
		super(mucConfig, dao);
	}
		
	@Override
	public Room createNewRoom(BareJID roomJID, JID senderJid) throws RepositoryException {
		Room room = super.createNewRoom(roomJID, senderJid);		
		addListenersToNewRoom(room);
		if (roomListener != null) {
			if (log.isLoggable(Level.FINEST)) {
				log.log(Level.FINEST, "notifing listener that room {0} was created", roomJID);
			}			
			roomListener.onRoomCreated(room);
		}
		return room;
	}

	public Room createNewRoomWithoutListener(BareJID roomJID, JID senderJid) throws RepositoryException {
		Room room = super.createNewRoom(roomJID, senderJid);
		addListenersToNewRoom(room);
		return room;
	}	
	
	@Override
	public void destroyRoom(Room room, Element destroyElement) throws RepositoryException {
		super.destroyRoom(room, destroyElement);
		
		if (roomListener != null) {
			if (log.isLoggable(Level.FINEST)) {
				log.log(Level.FINEST, "notifing listener that room {0} was destroyed", room.getRoomJID());
			}			
			roomListener.onRoomDestroyed(room, destroyElement);
		}
	}

	public void destroyRoomWithoutListener(Room room, Element destroyElement) throws RepositoryException {
		super.destroyRoom(room, destroyElement);
	}
	
	@Override
	public Room getRoom(BareJID roomJID) throws RepositoryException, MUCException {
		boolean isNewInstance = !this.getActiveRooms().containsKey(roomJID);
		Room room = super.getRoom(roomJID); 
		if (isNewInstance && room != null)
			addListenersToNewRoom(room);
		return room;
	}

	@Override
	public void leaveRoom(Room room) {
		super.leaveRoom(room);

		if (roomListener != null) {
			roomListener.onLeaveRoom(room);
		}
	}

	public void leaveRoomWithoutListener(Room room) {
		super.leaveRoom(room);
	}

	public void setRoomListener(RoomListener roomListener) {
		if (log.isLoggable(Level.FINEST)) {
			log.log(Level.FINEST, "setting room listener to {0}", roomListener);
		}
		this.roomListener = roomListener;
	}
	
	public void setRoomOccupantListener(Room.RoomOccupantListener roomOccupantListener) {
		if (log.isLoggable(Level.FINEST)) {
			log.log(Level.FINEST, "setting room occupants listener to " + roomListener);
		}
		this.roomOccupantListener = roomOccupantListener;
	}
	
	private void addListenersToNewRoom(Room room) {
		if (roomOccupantListener != null) {
			if (log.isLoggable(Level.FINEST)) {
				log.log(Level.FINEST, "adding room occupant listener for room {0}", room.getRoomJID());
			}
			room.addOccupantListener(roomOccupantListener);
		}
		if (roomListener != null && roomListener instanceof Room.RoomListener) {
			room.addListener((Room.RoomListener) roomListener);
		}		
	}
}
