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
package tigase.muc;

import tigase.component.exceptions.RepositoryException;
import tigase.muc.RoomConfig.RoomConfigListener;
import tigase.muc.repository.IMucRepository;
import tigase.xml.Element;
import tigase.xmpp.BareJID;
import tigase.xmpp.JID;

import java.util.*;
import java.util.Map.Entry;
import java.util.logging.Logger;

/**
 * @author bmalkow
 * 
 */
public class MockMucRepository implements IMucRepository {

	private class InternalRoom {
		boolean isPublic = true;
	}

	private final Map<BareJID, InternalRoom> allRooms = new HashMap<BareJID, InternalRoom>();

	private RoomConfig defaultConfig = new RoomConfig(null);

	protected Logger log = Logger.getLogger(this.getClass().getName());

	private final RoomConfigListener roomConfigListener;

	private final HashMap<BareJID, Room> rooms = new HashMap<BareJID, Room>();

	public MockMucRepository() throws RepositoryException {
		this.roomConfigListener = new RoomConfig.RoomConfigListener() {

			@Override
			public void onConfigChanged(final RoomConfig roomConfig, final Set<String> modifiedVars) {
				try {
					if (modifiedVars.contains(RoomConfig.MUC_ROOMCONFIG_PUBLICROOM_KEY)) {
						InternalRoom ir = allRooms.get(roomConfig.getRoomJID());
						if (ir != null) {
							ir.isPublic = roomConfig.isRoomconfigPublicroom();
						}
					}

					if (modifiedVars.contains(RoomConfig.MUC_ROOMCONFIG_PERSISTENTROOM_KEY)) {
						if (roomConfig.isPersistentRoom()) {
							System.out.println("now is PERSISTENT");
							@SuppressWarnings("unused")
							final Room room = getRoom(roomConfig.getRoomJID());
							// dao.createRoom(room);
						} else {
							System.out.println("now is NOT! PERSISTENT");
							// dao.destroyRoom(roomConfig.getRoomId());
						}
					} else if (roomConfig.isPersistentRoom()) {
						// dao.updateRoomConfig(roomConfig);
					}
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			}

			@Override
			public void onInitialRoomConfig( RoomConfig roomConfig ) {
				try {
					if ( roomConfig.isRoomconfigPublicroom() ){
						InternalRoom ir = allRooms.get( roomConfig.getRoomJID() );
						if ( ir != null ){
							ir.isPublic = roomConfig.isRoomconfigPublicroom();
						}
					}

				} catch ( Exception e ) {
					throw new RuntimeException( e );
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
		log.fine("Creating new room '" + roomJID + "'");
		RoomConfig rc = new RoomConfig(roomJID);

		rc.copyFrom(getDefaultRoomConfig(), false);

		Room room = new Room(rc, new Date(), senderJid.getBareJID());
		room.getConfig().addListener(roomConfigListener);
		this.rooms.put(roomJID, room);
		this.allRooms.put(roomJID, new InternalRoom());

		return room;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see tigase.muc.repository.IMucRepository#destroyRoom(tigase.muc.Room)
	 */
	@Override
	public void destroyRoom(Room room, Element destroyElement) throws RepositoryException {
		final BareJID roomJID = room.getRoomJID();
		this.rooms.remove(roomJID);
		this.allRooms.remove(roomJID);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see tigase.muc.repository.IMucRepository#getActiveRooms()
	 */
	@Override
	public Map<BareJID, Room> getActiveRooms() {
		return Collections.unmodifiableMap(rooms);
	}

	@Override
	public RoomConfig getDefaultRoomConfig() throws RepositoryException {
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
			if (entry.getValue().isPublic) {
				result.add(entry.getKey());
			}
		}
		return result.toArray(new BareJID[] {});
	}

	@Override
	public Map<BareJID, String> getPublicVisibleRooms(String domain) throws RepositoryException {
		Map<BareJID, String> result = new HashMap<>();
		for (Entry<BareJID, InternalRoom> entry : this.allRooms.entrySet()) {
			if (entry.getValue().isPublic) {
				BareJID jid = entry.getKey();
				Room room = getRoom(jid);
				String name = room != null ? room.getConfig().getRoomName() : null;
				if (name != null && name.isEmpty()) {
					name = null;
				}
				if (name == null) {
					name = jid.getLocalpart();
				}
				result.put(jid, name);
			}
		}
		return result;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see tigase.muc.repository.IMucRepository#getRoom()
	 */
	@Override
	public Room getRoom(final BareJID roomJID) throws RepositoryException {
		Room room = this.rooms.get(roomJID);

		return room;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see tigase.muc.repository.IMucRepository#getRoomName(java.lang.String)
	 */
	@Override
	public String getRoomName(String jid) throws RepositoryException {
		return null;
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
		log.fine("Removing room '" + roomJID + "' from memory");
		this.rooms.remove(roomJID);
		if (!room.getConfig().isPersistentRoom()) {
			this.allRooms.remove(roomJID);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * tigase.muc.repository.IMucRepository#updateDefaultRoomConfig(tigase.muc
	 * .RoomConfig)
	 */
	@Override
	public void updateDefaultRoomConfig(RoomConfig config) throws RepositoryException {
		throw new RuntimeException("NOT IMPLEMENTED");
	}
}
