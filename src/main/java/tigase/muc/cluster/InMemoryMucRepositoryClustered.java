/*
 * InMemoryMucRepositoryClustered.java
 *
 * Tigase Jabber/XMPP Server
 * Copyright (C) 2004-2013 "Tigase, Inc." <office@tigase.com>
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
