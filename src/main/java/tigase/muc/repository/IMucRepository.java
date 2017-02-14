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
package tigase.muc.repository;

import tigase.component.exceptions.RepositoryException;
import tigase.muc.Room;
import tigase.muc.RoomConfig;
import tigase.muc.exceptions.MUCException;
import tigase.xml.Element;
import tigase.xmpp.BareJID;
import tigase.xmpp.JID;

import java.util.Map;

/**
 * @author bmalkow
 * 
 */
public interface IMucRepository {

	Room createNewRoom(BareJID roomJID, JID senderJid) throws RepositoryException;

	void destroyRoom(Room room, Element destroyElement) throws RepositoryException;

	Map<BareJID, Room> getActiveRooms();

	RoomConfig getDefaultRoomConfig() throws RepositoryException;

	@Deprecated
	BareJID[] getPublicVisibleRoomsIdList() throws RepositoryException;

	Map<BareJID, String> getPublicVisibleRooms(String domain) throws RepositoryException;

	Room getRoom(BareJID roomJID) throws RepositoryException, MUCException;

	/**
	 * @param jid
	 * @return
	 */
	String getRoomName(String jid) throws RepositoryException;

	/**
	 * @param newRoomName
	 * @return
	 */
	boolean isRoomIdExists(String newRoomName);

	void leaveRoom(Room room);

	void updateDefaultRoomConfig(RoomConfig config) throws RepositoryException;

}
