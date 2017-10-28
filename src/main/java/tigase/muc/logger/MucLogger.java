/*
 * MucLogger.java
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
package tigase.muc.logger;

import java.util.Date;

import tigase.muc.Room;
import tigase.xmpp.jid.JID;

/**
 * @author bmalkow
 *
 */
public interface MucLogger {

	public static final String ID = "muc-logger";

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

}
