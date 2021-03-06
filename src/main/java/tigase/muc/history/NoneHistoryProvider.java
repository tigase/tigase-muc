/*
 * Tigase MUC - Multi User Chat component for Tigase
 * Copyright (C) 2007 Tigase, Inc. (office@tigase.com)
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
import tigase.muc.Room;
import tigase.xml.Element;
import tigase.xmpp.jid.JID;

import java.util.Date;

/**
 * @author bmalkow
 */
public class NoneHistoryProvider
		implements HistoryProvider {

	/*
	 * (non-Javadoc)
	 *
	 * @see tigase.muc.history.HistoryProvider#addJoinEvent(tigase.muc.Room,
	 * java.util.Date, tigase.xmpp.JID, java.lang.String)
	 */
	@Override
	public void addJoinEvent(Room room, Date date, JID senderJID, String nickName) {
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see tigase.muc.history.HistoryProvider#addLeaveEvent(tigase.muc.Room,
	 * java.util.Date, tigase.xmpp.JID, java.lang.String)
	 */
	@Override
	public void addLeaveEvent(Room room, Date date, JID senderJID, String nickName) {
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see tigase.muc.history.HistoryProvider#addMessage(tigase.muc.Room,
	 * tigase.xml.Element, java.lang.String, tigase.xmpp.JID, java.lang.String,
	 * java.util.Date)
	 */
	@Override
	public void addMessage(Room room, Element message, String body, JID senderJid, String senderNickname, Date time) {
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see tigase.muc.history.HistoryProvider#addSubjectChange(tigase.muc.Room,
	 * tigase.xml.Element, java.lang.String, tigase.xmpp.JID, java.lang.String,
	 * java.util.Date)
	 */
	@Override
	public void addSubjectChange(Room room, Element message, String subject, JID senderJid, String senderNickname,
								 Date time) {
	}

	@Override
	public void destroy() {
		// nothing to do
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see
	 * tigase.muc.history.HistoryProvider#getHistoryMessages(tigase.muc.Room,
	 * tigase.xmpp.JID, java.lang.Integer, java.lang.Integer, java.lang.Integer,
	 * java.util.Date, tigase.component.ElementWriter)
	 */
	@Override
	public void getHistoryMessages(Room room, JID senderJID, Integer maxchars, Integer maxstanzas, Integer seconds,
								   Date since, PacketWriter writer) {
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see tigase.muc.history.HistoryProvider#isPersistent()
	 */
	@Override
	public boolean isPersistent(Room room) {
		return false;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see tigase.muc.history.HistoryProvider#removeHistory(tigase.muc.Room)
	 */
	@Override
	public void removeHistory(Room room) {
	}

	@Override
	public void setDataSource(DataSource dataSource) {

	}
}
