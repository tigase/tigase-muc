/*
 * InMemoryMucRepositoryClustered.java
 *
 * Tigase Jabber/XMPP Server
 * Copyright (C) 2004-2013 "Tigase, Inc." <office@tigase.com>
 *
 */
package tigase.muc.cluster;

import tigase.component.exceptions.RepositoryException;
import tigase.kernel.beans.Bean;
import tigase.muc.Room;
import tigase.muc.RoomConfig;
import tigase.muc.repository.IMucRepository;
import tigase.muc.repository.inmemory.InMemoryMucRepository;
import tigase.xml.Element;
import tigase.xmpp.BareJID;
import tigase.xmpp.JID;

import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.logging.Level;

/**
 *
 * @author andrzej
 */
@Bean(name = IMucRepository.ID, parent = MUCComponentClustered.class)
public class InMemoryMucRepositoryClustered extends InMemoryMucRepository {

	private RoomListener roomListener;
	private Room.RoomOccupantListener roomOccupantListener;

	@Override
	protected void addToAllRooms(BareJID roomJid, InternalRoom internalRoom) {
		super.addToAllRooms(roomJid, internalRoom);
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
	public Room getRoom(BareJID roomJID) throws RepositoryException {
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

	protected void removeFromAllRooms(BareJID roomJid, Predicate<InternalRoom> predicate) {
		InternalRoom ir = allRooms.get(roomJid);
		if (ir != null) {
			if (predicate.test(ir)) {
				allRooms.remove(roomJid, ir);
			}
		}
	}

	@Override
	protected void removeFromAllRooms(BareJID roomJid) {
		super.removeFromAllRooms(roomJid);
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

	protected void roomConfigChanged(RoomConfig roomConfig, Set<String> modifiedVars) {
		super.roomConfigChanged(roomConfig, modifiedVars);
		this.roomListener.onRoomChanged(roomConfig, modifiedVars);
	}

	protected void roomConfigChanged(BareJID roomJid, Map<String, String> values) {
		InternalRoom ir = allRooms.get(roomJid);
		if (ir != null) {
			if (values.containsKey(RoomConfig.MUC_ROOMCONFIG_PUBLICROOM_KEY)) {
				String val = values.get(RoomConfig.MUC_ROOMCONFIG_PUBLICROOM_KEY);
				ir.isPublic = "1".equals(val) || "true".equals(val);
			}
			if (values.containsKey(RoomConfig.MUC_ROOMCONFIG_ROOMNAME_KEY)) {
				ir.name = values.get(RoomConfig.MUC_ROOMCONFIG_ROOMNAME_KEY);
			}
			if (values.containsKey(RoomConfig.MUC_ROOMCONFIG_PERSISTENTROOM_KEY)) {
				String val = values.get(RoomConfig.MUC_ROOMCONFIG_PERSISTENTROOM_KEY);
				ir.isPersistent = "1".equals(val) || "true".equals(val);
			}
		}

		Room room = rooms.get(roomJid);
		if (room != null) {
			for (Map.Entry<String,String> e : values.entrySet()) {
				String[] val = null;
				if (e.getValue() != null) {
					val = e.getValue().split("\\|");
				}
				room.getConfig().setValues(e.getKey(), val);
			}
		}
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

	public interface RoomListener {
		void onLeaveRoom(Room room);

		void onRoomChanged(RoomConfig roomConfig, Set<String> modifiedVars);

		void onRoomCreated(Room room);

		void onRoomDestroyed(Room room, Element destroyElement);
	}
}
