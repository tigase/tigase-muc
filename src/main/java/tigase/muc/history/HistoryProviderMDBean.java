/*
 * HistoryProviderMDBean.java
 *
 * Tigase Jabber/XMPP Server
 * Copyright (C) 2004-2016 "Tigase, Inc." <office@tigase.com>
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
package tigase.muc.history;

import tigase.component.PacketWriter;
import tigase.db.DBInitException;
import tigase.db.DataSource;
import tigase.db.DataSourceHelper;
import tigase.db.beans.MDRepositoryBean;
import tigase.kernel.beans.Bean;
import tigase.muc.MUCComponent;
import tigase.muc.Room;
import tigase.xml.Element;
import tigase.xmpp.JID;

import java.util.Date;

/**
 * Created by andrzej on 25.08.2016.
 */
@Bean(name = "historyProviderPool", parent = MUCComponent.class)
public class HistoryProviderMDBean extends MDRepositoryBean<HistoryProvider> implements HistoryProvider {
	@Override
	public Class<?> getDefaultBeanClass() {
		return HistoryProviderConfigBean.class;
	}

	@Override
	public void setDataSource(DataSource dataSource) {
		// Nothing to do
	}

	@Override
	public void addJoinEvent(Room room, Date date, JID senderJID, String nickName) {
		getRepository(room).addJoinEvent(room, date, senderJID, nickName);
	}

	@Override
	public void addLeaveEvent(Room room, Date date, JID senderJID, String nickName) {
		getRepository(room).addLeaveEvent(room, date, senderJID, nickName);
	}

	@Override
	public void addMessage(Room room, Element message, String body, JID senderJid, String senderNickname, Date time) {
		getRepository(room).addMessage(room, message, body, senderJid, senderNickname, time);
	}

	@Override
	public void addSubjectChange(Room room, Element message, String subject, JID senderJid, String senderNickname, Date time) {
		getRepository(room).addSubjectChange(room, message, subject, senderJid, senderNickname, time);
	}

	@Override
	public void destroy() {
		// nothing to do
	}

	@Override
	public void getHistoryMessages(Room room, JID senderJID, Integer maxchars, Integer maxstanzas, Integer seconds, Date since, PacketWriter writer) {
		getRepository(room).getHistoryMessages(room, senderJID, maxchars, maxstanzas, seconds, since, writer);
	}

	@Override
	public boolean isPersistent(Room room) {
		return getRepository(room).isPersistent(room);
	}

	@Override
	public void removeHistory(Room room) {
		getRepository(room).removeHistory(room);
	}

	protected HistoryProvider getRepository(Room room) {
		return getRepository(room.getRoomJID().getDomain());
	}

	@Override
	protected Class<? extends HistoryProvider> findClassForDataSource(DataSource dataSource) throws DBInitException {
		return DataSourceHelper.getDefaultClass(HistoryProvider.class, dataSource.getResourceUri());
	}

	public static class HistoryProviderConfigBean extends MDRepositoryBean.MDRepositoryConfigBean<HistoryProvider> {

		@Override
		protected Class<?> getRepositoryClassName() throws DBInitException, ClassNotFoundException {
			if ("memory".equals(getCls())) {
				return MemoryHistoryProvider.class;
			} else if ("none".equals(getCls())) {
				return NoneHistoryProvider.class;
			}
			return super.getRepositoryClassName();
		}
	}
}
