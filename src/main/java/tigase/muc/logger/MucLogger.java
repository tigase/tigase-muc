/*
 * Tigase Jabber/XMPP Multi-User Chat Component
 * Copyright (C) 2008 "Bartosz M. Ma¸kowski" <bartosz.malkowski@tigase.org>
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
package tigase.muc.logger;

import java.util.Date;
import java.util.Map;

import tigase.muc.Room;
import tigase.xmpp.JID;

/**
 * @author bmalkow
 * 
 */
public interface MucLogger {

	public final static String MUC_LOGGER_CLASS_KEY = "muc-logger-class";

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
	 * @param senderJid
	 * @param senderNickname
	 * @param time
	 */
	void addMessage(Room room, String message, JID senderJid, String senderNickname, Date time);

	/**
	 * Adds subject changes to log/history.
	 * 
	 * @param room
	 * @param message
	 * @param senderJid
	 * @param senderNickname
	 * @param time
	 */
	void addSubjectChange(Room room, String message, JID senderJid, String senderNickname, Date time);

	public void init(Map<String, Object> props);

}
