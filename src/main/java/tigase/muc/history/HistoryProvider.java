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
package tigase.muc.history;

import java.util.Date;
import java.util.Map;
import tigase.component.PacketWriter;
import tigase.db.Repository;
import tigase.muc.Room;
import tigase.xml.Element;
import tigase.xmpp.JID;

/**
 * @author bmalkow
 * 
 */
public interface HistoryProvider extends Repository {

	/**
	 * Adds join event.
	 * 
	 * @param room
	 * @param date
	 * @param senderJID
	 * @param nickName
	 */
	void addJoinEvent(Room room, Date date, JID senderJID, String nickName);

	/**
	 * @param room
	 * @param date
	 * @param senderJID
	 * @param nickName
	 */
	void addLeaveEvent(Room room, Date date, JID senderJID, String nickName);

	/**
	 * @param room
	 * @param message
	 *            TODO
	 * @param body
	 * @param senderJid
	 * @param senderNickname
	 * @param time
	 */
	void addMessage(Room room, Element message, String body, JID senderJid, String senderNickname, Date time);

	/**
	 * Adds subject changes to log/history.
	 * 
	 * @param room
	 * @param message
	 *            TODO
	 * @param subject
	 * @param senderJid
	 * @param senderNickname
	 * @param time
	 */
	void addSubjectChange(Room room, Element message, String subject, JID senderJid, String senderNickname, Date time);

	/**
	 * Destroys this instance of HistoryProvider releasing all resources allocated
	 * but this provider if they should be released
	 */
	void destroy();
	
	/**
	 * @param room
	 * @param senderJID
	 * @param maxchars
	 * @param maxstanzas
	 * @param seconds
	 * @param since
	 * @param writer
	 *            TODO
	 */
	void getHistoryMessages(Room room, JID senderJID, Integer maxchars, Integer maxstanzas, Integer seconds, Date since,
			PacketWriter writer);

	public void init(Map<String, Object> props);

	/**
	 * @return
	 */
	boolean isPersistent();

	void removeHistory(Room room);

}
