/*
 * Tigase Jabber/XMPP Multi-User Chat Component
 * Copyright (C) 2008 "Bartosz M. Małkowski" <bartosz.malkowski@tigase.org>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. Look for COPYING file in the top folder.
 * If not, see http://www.gnu.org/licenses/.
 *
 * $Rev$
 * Last modified by $Author$
 * $Date$
 */
package tigase.muc.repository.inmemory;

import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

import tigase.muc.Affiliation;
import tigase.muc.MucConfig;
import tigase.muc.Room;
import tigase.muc.RoomConfig;
import tigase.muc.Room.RoomListener;
import tigase.muc.RoomConfig.RoomConfigListener;
import tigase.muc.repository.IMucRepository;
import tigase.muc.repository.MucDAO;
import tigase.muc.repository.RepositoryException;
import tigase.util.JIDUtils;

/**
 * @author bmalkow
 * 
 */
public class InMemoryMucRepository implements IMucRepository {

	private final Set<String> allRooms = new HashSet<String>();

	private final MucConfig config;

	private final MucDAO dao;

	private RoomConfig defaultConfig = new RoomConfig(null);

	protected Logger log = Logger.getLogger(this.getClass().getName());

	private final RoomConfigListener roomConfigListener;

	private final RoomListener roomListener;

	private final HashMap<String, Room> rooms = new HashMap<String, Room>();

	public InMemoryMucRepository(final MucConfig mucConfig, final MucDAO dao) throws RepositoryException {
		this.dao = dao;
		this.config = mucConfig;

		for (String jid : dao.getRoomsIdList()) {
			this.allRooms.add(jid);
		}

		this.roomListener = new Room.RoomListener() {

			@Override
			public void onChangeSubject(Room room, String nick, String newSubject) {
				try {
					if (room.getConfig().isPersistentRoom())
						dao.setSubject(room.getRoomId(), newSubject, nick);
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			}

			@Override
			public void onSetAffiliation(Room room, String jid, Affiliation newAffiliation) {
				try {
					if (room.getConfig().isPersistentRoom())
						dao.setAffiliation(room.getRoomId(), jid, newAffiliation);
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			}
		};

		this.roomConfigListener = new RoomConfig.RoomConfigListener() {

			@Override
			public void onConfigChanged(final RoomConfig roomConfig, final Set<String> modifiedVars) {
				try {
					if (modifiedVars.contains("muc#roomconfig_persistentroom")) {
						if (roomConfig.isPersistentRoom()) {
							System.out.println("now is PERSISTENT");
							final Room room = getRoom(roomConfig.getRoomId());
							dao.createRoom(room);
						} else {
							System.out.println("now is NOT! PERSISTENT");
							dao.destroyRoom(roomConfig.getRoomId());
						}
					} else if (roomConfig.isPersistentRoom()) {
						dao.updateRoomConfig(roomConfig);
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
	public Room createNewRoom(String roomId, String senderJid) throws RepositoryException {
		log.fine("Creating new room '" + roomId + "'");
		RoomConfig rc = new RoomConfig(roomId);

		rc.copyFrom(getDefaultRoomConfig(), false);

		Room room = new Room(rc, new Date(), JIDUtils.getNodeID(senderJid));
		room.getConfig().addListener(roomConfigListener);
		room.addListener(roomListener);
		this.rooms.put(roomId, room);
		this.allRooms.add(roomId);

		return room;
	}

	@Override
	public RoomConfig getDefaultRoomConfig() throws RepositoryException {
		return defaultConfig;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see tigase.muc.repository.IMucRepository#getRoom()
	 */
	@Override
	public Room getRoom(final String roomId) throws RepositoryException {
		Room room = this.rooms.get(roomId);
		if (room == null) {
			room = dao.readRoom(roomId);
			if (room != null) {
				room.getConfig().addListener(roomConfigListener);
				room.addListener(roomListener);
				this.rooms.put(roomId, room);
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
		return dao.getRoomName(jid);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see tigase.muc.repository.IMucRepository#getRoomsIdList()
	 */
	@Override
	public String[] getRoomsIdList() throws RepositoryException {
		return allRooms.toArray(new String[] {});
	}

	@Override
	public void leaveRoom(Room room) {
		final String roomId = room.getRoomId();
		log.fine("Removing room '" + roomId + "' from memory");
		this.rooms.remove(roomId);
		if (!room.getConfig().isPersistentRoom()) {
			this.allRooms.remove(roomId);
		}
	}

}
