/*
 * Tigase Jabber/XMPP Multi-User Chat Component
 * Copyright (C) 2008 "Bartosz M. Małkowski" <bartosz.malkowski@tigase.org>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
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
 * $Rev$
 * Last modified by $Author$
 * $Date$
 */
package tigase.muc.repository.inmemory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import tigase.component.exceptions.RepositoryException;
import tigase.muc.Affiliation;
import tigase.muc.MucConfig;
import tigase.muc.Room;
import tigase.muc.Room.RoomListener;
import tigase.muc.RoomConfig;
import tigase.muc.RoomConfig.RoomConfigListener;
import tigase.muc.exceptions.MUCException;
import tigase.muc.repository.IMucRepository;
import tigase.muc.repository.MucDAO;
import tigase.util.TigaseStringprepException;
import tigase.xmpp.BareJID;
import tigase.xmpp.JID;

/**
 * @author bmalkow
 * 
 */
public class InMemoryMucRepository implements IMucRepository {

	private class InternalRoom {
		boolean listPublic = true;
	}

	private final Map<BareJID, InternalRoom> allRooms = new ConcurrentHashMap<BareJID, InternalRoom>();

	private final MucDAO dao;

	private RoomConfig defaultConfig;

	protected Logger log = Logger.getLogger(this.getClass().getName());

	private MucConfig mucConfig;

	private final RoomConfigListener roomConfigListener;

	private final RoomListener roomListener;

	private final Map<BareJID, Room> rooms = new ConcurrentHashMap<BareJID, Room>();

	public InMemoryMucRepository(final MucConfig mucConfig, final MucDAO dao) throws RepositoryException {
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

		RoomConfig rc = new RoomConfig(roomJID, this.mucConfig.isPublicLoggingEnabled());

		rc.copyFrom(getDefaultRoomConfig(), false);

		Room room = new Room(rc, new Date(), senderJid.getBareJID());
		room.getConfig().addListener(roomConfigListener);
		room.addListener(roomListener);
		this.rooms.put(roomJID, room);
		this.allRooms.put(roomJID, new InternalRoom());

		return room;
	}

	@Override
	public void destroyRoom(Room room) throws RepositoryException {
		final BareJID roomJID = room.getRoomJID();
		if (log.isLoggable(Level.FINE))
			log.fine("Destroying room '" + roomJID);
		this.rooms.remove(roomJID);
		this.allRooms.remove(roomJID);
		dao.destroyRoom(roomJID);
	}

	@Override
	public Map<BareJID, Room> getActiveRooms() {
		return Collections.unmodifiableMap(rooms);
	}

	@Override
	public RoomConfig getDefaultRoomConfig() throws RepositoryException {
		if (defaultConfig == null) {
			defaultConfig = new RoomConfig(null, this.mucConfig.isPublicLoggingEnabled());
			try {
				defaultConfig.read(dao.getRepository(), mucConfig, MucDAO.ROOMS_KEY + null + "/config");
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
		return result.toArray(new BareJID[] {});
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see tigase.muc.repository.IMucRepository#getRoom()
	 */
	@Override
	public Room getRoom(final BareJID roomJID) throws RepositoryException, MUCException, TigaseStringprepException {
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
	}

	@Override
	public void updateDefaultRoomConfig(RoomConfig config) throws RepositoryException {
		RoomConfig org = getDefaultRoomConfig();
		org.copyFrom(config);
		dao.updateRoomConfig(defaultConfig);
	}
}
