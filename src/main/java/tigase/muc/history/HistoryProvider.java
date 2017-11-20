/*
 * HistoryProvider.java
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
package tigase.muc.history;

import tigase.component.PacketWriter;
import tigase.db.DataSource;
import tigase.db.DataSourceAware;
import tigase.kernel.beans.Bean;
import tigase.muc.Room;
import tigase.xml.Element;
import tigase.xmpp.jid.JID;

import java.util.Date;

/**
 * @author bmalkow
 */
@Bean(name = "history-provider", active = true)
public interface HistoryProvider<DS extends DataSource>
		extends DataSourceAware<DS> {

	/**
	 * Adds join event.
	 */
	void addJoinEvent(Room room, Date date, JID senderJID, String nickName);

	void addLeaveEvent(Room room, Date date, JID senderJID, String nickName);

	void addMessage(Room room, Element message, String body, JID senderJid, String senderNickname, Date time);

	/**
	 * Adds subject changes to log/history.
	 */
	void addSubjectChange(Room room, Element message, String subject, JID senderJid, String senderNickname, Date time);

	/**
	 * Destroys this instance of HistoryProvider releasing all resources allocated but this provider if they should be
	 * released
	 */
	void destroy();

	void getHistoryMessages(Room room, JID senderJID, Integer maxchars, Integer maxstanzas, Integer seconds, Date since,
							PacketWriter writer);

	boolean isPersistent(Room room);

	void removeHistory(Room room);

}
