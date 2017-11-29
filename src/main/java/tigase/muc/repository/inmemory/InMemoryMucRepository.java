/*
 * InMemoryMucRepository.java
 *
 * Tigase Jabber/XMPP Multi-User Chat Component
 * Copyright (C) 2004-2017 "Tigase, Inc." <office@tigase.com>
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
package tigase.muc.repository.inmemory;

import tigase.component.exceptions.RepositoryException;
import tigase.db.UserExistsException;
import tigase.db.UserRepository;
import tigase.eventbus.EventBus;
import tigase.kernel.beans.Bean;
import tigase.kernel.beans.Initializable;
import tigase.kernel.beans.Inject;
import tigase.muc.*;
import tigase.muc.Room.RoomListener;
import tigase.muc.RoomConfig.RoomConfigListener;
import tigase.muc.repository.IMucDAO;
import tigase.muc.repository.IMucRepository;
import tigase.server.Packet;
import tigase.xml.Element;
import tigase.xmpp.jid.BareJID;
import tigase.xmpp.jid.JID;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author bmalkow
 */
@Bean(name = IMucRepository.ID, parent = MUCComponent.class, active = true)
public class InMemoryMucRepository
		implements IMucRepository, Initializable {

	private static final String ROOMS_KEY = "rooms/";

	protected final Map<BareJID, InternalRoom> allRooms = new ConcurrentHashMap<BareJID, InternalRoom>();
	protected final Map<BareJID, RoomWithId> rooms = new ConcurrentHashMap<>();
	private final RoomConfigListener roomConfigListener;
	private final RoomListener roomListener;
	protected Logger log = Logger.getLogger(this.getClass().getName());
	@Inject(nullAllowed = false)
	private IMucDAO dao;
	private RoomConfig defaultConfig;
	@Inject
	private EventBus eventBus;
	@Inject
	private MUCConfig mucConfig;
	@Inject
	private Room.RoomFactory roomFactory;
	@Inject
	private UserRepository userRepository;

	public InMemoryMucRepository() {
		this.roomListener = new Room.RoomListener() {

			@Override
			public void onChangeSubject(Room room, String nick, String newSubject, Date changeDate) {
				try {
					if (room.getConfig().isPersistentRoom()) {
						dao.setSubject((RoomWithId) room, newSubject, nick, changeDate);
					}
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
					if (room.getConfig().isPersistentRoom()) {
						RoomWithId roomWithId = (RoomWithId) room;
						if (roomWithId.getId() != null) {
							dao.setAffiliation(roomWithId, jid, newAffiliation);
						}
					}
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			}
		};

		this.roomConfigListener = new RoomConfig.RoomConfigListener() {

			@Override
			public void onConfigChanged(final RoomConfig roomConfig, final Set<String> modifiedVars) {
				roomConfigChanged(roomConfig, modifiedVars);
			}

			@Override
			public void onInitialRoomConfig(RoomConfig roomConfig) {
				try {
					if (roomConfig.isPersistentRoom()) {
						final Room room = getRoom(roomConfig.getRoomJID());
						dao.createRoom((RoomWithId) room);
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
		if (log.isLoggable(Level.FINE)) {
			log.fine("Creating new room '" + roomJID + "'");
		}

		RoomConfig rc = new RoomConfig(roomJID);

		rc.copyFrom(getDefaultRoomConfig(), false);

		RoomWithId room = roomFactory.newInstance(null, rc, new Date(), senderJid.getBareJID());
		room.getConfig().addListener(roomConfigListener);
		room.addListener(roomListener);
		this.rooms.put(roomJID, room);
		InternalRoom ir = new InternalRoom();
		ir.isPersistent = room.getConfig().isPersistentRoom();
		ir.isPublic = room.getConfig().isRoomconfigPublicroom();
		addToAllRooms(roomJID, ir);

		// if (rc.isPersistentRoom()) {
		// dao.createRoom( room );
		// }

		return room;
	}

	@Override
	public void destroyRoom(Room room, Element destroyElement) throws RepositoryException {
		final BareJID roomJID = room.getRoomJID();
		if (log.isLoggable(Level.FINE)) {
			log.fine("Destroying room '" + roomJID);
		}
		this.rooms.remove(roomJID);
		removeFromAllRooms(roomJID);
		dao.destroyRoom(roomJID);
		fireDestroyRoom(room);
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
				defaultConfig.read(userRepository, mucConfig,
								   ROOMS_KEY + MUCComponent.DEFAULT_ROOM_CONFIG_KEY + "/config");
			} catch (Exception e) {
				log.log(Level.FINEST, "Error reading default room configuration", e);
			}
			// dao.updateRoomConfig(defaultConfig);
		}
		return defaultConfig;
	}

	@Override
	public Map<BareJID, String> getPublicVisibleRooms(String domain) throws RepositoryException {
		Map<BareJID, String> result = new HashMap<>();
		for (Map.Entry<BareJID, InternalRoom> entry : this.allRooms.entrySet()) {
			if (entry.getValue().isPublic == null) {
				InternalRoom ir = entry.getValue();
				try {
					Room room = dao.getRoom(entry.getKey());
					synchronized (ir) {
						ir.isPublic = room == null ? false : room.getConfig().isRoomconfigPublicroom();
					}
				} catch (RepositoryException ex) {
					entry.getValue().isPublic = false;
				}
			}
			if (entry.getValue().isPublic) {
				BareJID jid = entry.getKey();
				if (!domain.equals(jid.getDomain())) {
					continue;
				}

				String name = entry.getValue().name;
				if (name == null) {
					Room room = getRoom(jid);
					if (room != null) {
						name = room.getConfig().getRoomName();
						if (name != null && name.isEmpty()) {
							name = null;
						}
					}
				}
				if (name == null) {
					name = jid.getLocalpart();
				}
				result.put(jid, name);
			}
		}
		return result;
	}

	@Override
	public BareJID[] getPublicVisibleRoomsIdList() throws RepositoryException {
		List<BareJID> result = new ArrayList<BareJID>();
		for (Map.Entry<BareJID, InternalRoom> entry : this.allRooms.entrySet()) {
			if (entry.getValue().isPublic == null) {
				InternalRoom ir = entry.getValue();
				try {
					Room room = dao.getRoom(entry.getKey());
					synchronized (ir) {
						ir.isPublic = room == null ? false : room.getConfig().isRoomconfigPublicroom();
					}
				} catch (RepositoryException ex) {
					entry.getValue().isPublic = false;
				}
			}
			if (entry.getValue().isPublic) {
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
	public Room getRoom(final BareJID roomJID) throws RepositoryException {
		RoomWithId room = this.rooms.get(roomJID);
		if (room == null) {
			room = dao.getRoom(roomJID);
			if (room != null) {
				room.setAffiliations(dao.getAffiliations(room));
				room.getConfig().addListener(roomConfigListener);
				room.addListener(roomListener);
				this.rooms.put(roomJID, room);
			}
		}
		return room;
	}

	@Override
	public void initialize() {
		try {
			try {
				getDefaultRoomConfig();
			} catch (RepositoryException e) {
				// if we cannot load default room config we need to check a few things
				if (!userRepository.userExists(mucConfig.getServiceName())) {
					try {
						userRepository.addUser(mucConfig.getServiceName());
					} catch (UserExistsException ex) {
						// maybe other node create service user, let's ignore this exception..
					}
				}
			}

			List<BareJID> roomJids = dao.getRoomsJIDList();
			if (roomJids != null) {
				for (BareJID jid : roomJids) {
					addToAllRooms(jid, new InternalRoom());
				}
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
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
		if (log.isLoggable(Level.FINE)) {
			log.fine("Removing room '" + roomJID + "' from memory");
		}
		this.rooms.remove(roomJID);
		if (!room.getConfig().isPersistentRoom()) {
			removeFromAllRooms(roomJID);
		}
		fireDestroyRoom(room);
	}

	@Override
	public void updateDefaultRoomConfig(RoomConfig config) throws RepositoryException {
		RoomConfig roomConfig = getDefaultRoomConfig();
		roomConfig.copyFrom(config);
		try {
			String roomJID = roomConfig.getRoomJID() != null ? roomConfig.getRoomJID().toString() : null;
			if (roomJID == null) {
				roomJID = MUCComponent.DEFAULT_ROOM_CONFIG_KEY;
			}
			roomConfig.write(userRepository, mucConfig, ROOMS_KEY + roomJID + "/config");
		} catch (Exception e) {
			// finest is OK as it will be re-thrown
			log.log(Level.FINEST, "error during updating default room configuration", e);
			throw new RepositoryException("Room config writing error", e);
		}
	}

	protected void addToAllRooms(BareJID roomJid, InternalRoom internalRoom) {
		allRooms.put(roomJid, internalRoom);
	}

	protected void removeFromAllRooms(BareJID roomJid) {
		allRooms.remove(roomJid);
	}

	protected void roomConfigChanged(RoomConfig roomConfig, Set<String> modifiedVars) {
		try {
			if (modifiedVars.contains(RoomConfig.MUC_ROOMCONFIG_PUBLICROOM_KEY)) {
				InternalRoom ir = allRooms.get(roomConfig.getRoomJID());
				if (ir != null) {
					ir.isPublic = roomConfig.isRoomconfigPublicroom();
				}
			}
			if (modifiedVars.contains(RoomConfig.MUC_ROOMCONFIG_ROOMNAME_KEY)) {
				InternalRoom ir = allRooms.get(roomConfig.getRoomJID());
				if (ir != null) {
					String name = roomConfig.getRoomName();
					if (name != null && name.isEmpty()) {
						name = null;
					}
					log.log(Level.FINEST, "setting room name '" + name + "'");
					ir.name = name;
				}
			}

			if (modifiedVars.contains(RoomConfig.MUC_ROOMCONFIG_PERSISTENTROOM_KEY)) {
				if (roomConfig.isPersistentRoom()) {
					final Room room = getRoom(roomConfig.getRoomJID());
					dao.createRoom((RoomWithId) room);
				} else {
					dao.destroyRoom(roomConfig.getRoomJID());
				}
				InternalRoom ir = allRooms.get(roomConfig.getRoomJID());
				if (ir != null) {
					ir.isPersistent = roomConfig.isPersistentRoom();
				}
			} else if (roomConfig.isPersistentRoom()) {
				dao.updateRoomConfig(roomConfig);
			}

		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private void fireDestroyRoom(Room room) {
		Element emptyRoomEvent = new Element("RoomDestroyed", new String[]{"xmlns"}, new String[]{"tigase:events:muc"});
		emptyRoomEvent.addChild(new Element("room", room.getRoomJID().toString()));
		eventBus.fire(emptyRoomEvent);
	}

	public static class InternalRoom {

		public boolean isPersistent = false;
		public Boolean isPublic = null;
		public String name;
	}
}
