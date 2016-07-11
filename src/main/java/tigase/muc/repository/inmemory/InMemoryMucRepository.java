/*
 * InMemoryMucRepository.java
 *
 * Tigase Workgroup Queues Component
 * Copyright (C) 2011-2016 "Tigase, Inc." <office@tigase.com>
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
package tigase.muc.repository.inmemory;

import tigase.component.exceptions.RepositoryException;
import tigase.muc.*;
import tigase.muc.Room.RoomListener;
import tigase.muc.RoomConfig.RoomConfigListener;
import tigase.muc.exceptions.MUCException;
import tigase.muc.repository.IMucRepository;
import tigase.muc.repository.MucDAO;
import tigase.server.Packet;
import tigase.xml.Element;
import tigase.xmpp.BareJID;
import tigase.xmpp.JID;

import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author bmalkow
 */
public class InMemoryMucRepository implements IMucRepository {

	private final Map<BareJID, InternalRoom> allRooms = new ConcurrentHashMap<BareJID, InternalRoom>();
	private final MucDAO dao;
	private final RoomConfigListener roomConfigListener;
	private final RoomListener roomListener;
	private final Map<BareJID, Room> rooms = new ConcurrentHashMap<BareJID, Room>();
	protected Logger log = Logger.getLogger(this.getClass().getName());
	private RoomConfig defaultConfig;
	private MucContext mucConfig;

	public InMemoryMucRepository(final MucContext mucConfig, final MucDAO dao) throws RepositoryException {
		this.dao = dao;
		this.mucConfig = mucConfig;

		ArrayList<BareJID> roomJids = dao.getRoomsJIDList();
		if (roomJids != null) {
			for (BareJID jid : roomJids) {
				this.allRooms.put(jid, new InternalRoom());
			}
		}

		this.roomListener = new Room.RoomListener() {

			@Override
			public void onChangeSubject(Room room, String nick, String newSubject, Date changeDate) {
				try {
					if (room.getConfig().isPersistentRoom())
						dao.setSubject(room.getRoomJID(), newSubject, nick, changeDate);
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			}

			@Override
			public void onMessageToOccupants(Room room, JID from, Packet msg) {
				// nothing to do here
			}

			@Override
			public void onSetAffiliation(Room room, BareJID jid, Affiliation newAffiliation) {
				try {
					if (room.getConfig().isPersistentRoom())
						dao.setAffiliation(room.getRoomJID(), jid, newAffiliation);
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			}
		};

		this.roomConfigListener = new RoomConfig.RoomConfigListener() {

			@Override
			public void onConfigChanged(final RoomConfig roomConfig, final Set<String> modifiedVars) {
				try {
					if (modifiedVars.contains(RoomConfig.MUC_ROOMCONFIG_PUBLICROOM_KEY)) {
						InternalRoom ir = allRooms.get(roomConfig.getRoomJID());
						if (ir != null) {
							ir.listPublic = roomConfig.isRoomconfigPublicroom();
						}
					}

					if (modifiedVars.contains(RoomConfig.MUC_ROOMCONFIG_PERSISTENTROOM_KEY)) {
						if (roomConfig.isPersistentRoom()) {
							final Room room = getRoom(roomConfig.getRoomJID());
							dao.createRoom(room);
						} else {
							dao.destroyRoom(roomConfig.getRoomJID());
						}
					} else if (roomConfig.isPersistentRoom()) {
						dao.updateRoomConfig(roomConfig);
					}
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			}

			@Override
			public void onInitialRoomConfig(RoomConfig roomConfig) {
				try {
					if (roomConfig.isPersistentRoom()) {
						final Room room = getRoom(roomConfig.getRoomJID());
						dao.createRoom(room);
					}
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			}
		};
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see tigase.muc.repository.IMucRepository#createNewRoom(java.lang.String,
	 * java.lang.String, java.lang.String)
	 */
	@Override
	public Room createNewRoom(BareJID roomJID, JID senderJid) throws RepositoryException {
		if (log.isLoggable(Level.FINE))
			log.fine("Creating new room '" + roomJID + "'");

		RoomConfig rc = new RoomConfig(roomJID);

		rc.copyFrom(getDefaultRoomConfig(), false);

		Room room = Room.newInstance(rc, new Date(), senderJid.getBareJID());
		room.getConfig().addListener(roomConfigListener);
		room.addListener(roomListener);
		this.rooms.put(roomJID, room);
		this.allRooms.put(roomJID, new InternalRoom());

//		if (rc.isPersistentRoom()) {
//			dao.createRoom( room );
//		}

		return room;
	}

	@Override
	public void destroyRoom(Room room, Element destroyElement) throws RepositoryException {
		final BareJID roomJID = room.getRoomJID();
		if (log.isLoggable(Level.FINE))
			log.fine("Destroying room '" + roomJID);
		this.rooms.remove(roomJID);
		this.allRooms.remove(roomJID);
		dao.destroyRoom(roomJID);
		fireDestroyRoom(room);
	}

	private void fireDestroyRoom(Room room) {
		Element emptyRoomEvent = new Element("RoomDestroyed", new String[]{"xmlns"}, new String[]{"tigase:events:muc"});
		emptyRoomEvent.addChild(new Element("room", room.getRoomJID().toString()));
		mucConfig.getEventBus().fire(emptyRoomEvent);
	}

	@Override
	public Map<BareJID, Room> getActiveRooms() {
		return Collections.unmodifiableMap(rooms);
	}

	@Override
	public RoomConfig getDefaultRoomConfig() throws RepositoryException {
		if (defaultConfig == null) {
			defaultConfig = new RoomConfig(null);
			try {
				defaultConfig.read(dao.getRepository(), mucConfig, MucDAO.ROOMS_KEY + MUCComponent.DEFAULT_ROOM_CONFIG_KEY + "/config");
			} catch (Exception e) {
				e.printStackTrace();
			}
			// dao.updateRoomConfig(defaultConfig);
		}
		return defaultConfig;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see tigase.muc.repository.IMucRepository#getRoomsIdList()
	 */
	@Override
	public BareJID[] getPublicVisibleRoomsIdList() throws RepositoryException {
		List<BareJID> result = new ArrayList<BareJID>();
		for (Entry<BareJID, InternalRoom> entry : this.allRooms.entrySet()) {
			if (entry.getValue().listPublic) {
				result.add(entry.getKey());
			}
		}
		return result.toArray(new BareJID[]{});
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see tigase.muc.repository.IMucRepository#getRoom()
	 */
	@Override
	public Room getRoom(final BareJID roomJID) throws RepositoryException, MUCException {
		Room room = this.rooms.get(roomJID);
		if (room == null) {
			room = dao.readRoom(roomJID);
			if (room != null) {
				room.getConfig().addListener(roomConfigListener);
				room.addListener(roomListener);
				this.rooms.put(roomJID, room);
			}
		}
		return room;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see tigase.muc.repository.IMucRepository#getRoomName(java.lang.String)
	 */
	@Override
	public String getRoomName(String jid) throws RepositoryException {
		Room r = rooms.get(BareJID.bareJIDInstanceNS(jid));
		if (r != null) {
			return r.getConfig().getRoomName();
		} else {
			return dao.getRoomName(jid);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * tigase.muc.repository.IMucRepository#isRoomIdExists(java.lang.String)
	 */
	@Override
	public boolean isRoomIdExists(String newRoomName) {
		return this.allRooms.containsKey(newRoomName);
	}

	@Override
	public void leaveRoom(Room room) {
		final BareJID roomJID = room.getRoomJID();
		if (log.isLoggable(Level.FINE))
			log.fine("Removing room '" + roomJID + "' from memory");
		this.rooms.remove(roomJID);
		if (!room.getConfig().isPersistentRoom()) {
			this.allRooms.remove(roomJID);
		}
		fireDestroyRoom(room);
	}

	@Override
	public void updateDefaultRoomConfig(RoomConfig config) throws RepositoryException {
		RoomConfig org = getDefaultRoomConfig();
		org.copyFrom(config);
		dao.updateRoomConfig(defaultConfig);
	}

	private class InternalRoom {
		boolean listPublic = true;
	}
}
