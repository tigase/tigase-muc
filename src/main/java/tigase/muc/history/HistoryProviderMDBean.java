/*
 * HistoryProviderMDBean.java
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
import tigase.component.exceptions.ComponentException;
import tigase.component.exceptions.RepositoryException;
import tigase.db.DBInitException;
import tigase.db.DataSource;
import tigase.db.DataSourceHelper;
import tigase.db.beans.MDRepositoryBean;
import tigase.db.beans.MDRepositoryBeanWithStatistics;
import tigase.kernel.beans.Bean;
import tigase.kernel.beans.config.ConfigField;
import tigase.muc.MUCComponent;
import tigase.muc.Room;
import tigase.osgi.ModulesManagerImpl;
import tigase.server.BasicComponent;
import tigase.xml.Element;
import tigase.xmpp.Authorization;
import tigase.xmpp.jid.JID;
import tigase.xmpp.mam.MAMRepository;
import tigase.xmpp.mam.Query;
import tigase.xmpp.mam.QueryImpl;

import java.util.Date;

/**
 * Created by andrzej on 25.08.2016.
 */
@Bean(name = "historyProviderPool", parent = MUCComponent.class, active = true)
public class HistoryProviderMDBean extends MDRepositoryBeanWithStatistics<HistoryProvider>
		implements HistoryProvider, MAMRepository {

	@ConfigField(desc = "Use domain without component name to lookup for repository", alias = "map-component-to-bare-domain")
	private boolean mapComponentToBareDomain = false;

	public HistoryProviderMDBean() {
		super(HistoryProvider.class, MAMRepository.class);
	}

	@Override
	public boolean belongsTo(Class<? extends BasicComponent> component) {
		return MUCComponent.class.isAssignableFrom(component);
	}

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

//	@Override
//	public void register(Kernel kernel) {
//		kernel.getParent().ln("muc-dao", kernel, "muc-dao");
//		super.register(kernel);
//	}

	@Override
	public void removeHistory(Room room) {
		getRepository(room).removeHistory(room);
	}

	@Override
	public void queryItems(Query query, ItemHandler itemHandler) throws RepositoryException, ComponentException {
		HistoryProvider historyProvider = getRepository(query.getComponentJID().getDomain());
		if (historyProvider instanceof MAMRepository) {
			((MAMRepository) historyProvider).queryItems(query, itemHandler);
		} else {
			throw new ComponentException(Authorization.FEATURE_NOT_IMPLEMENTED);
		}
	}

	@Override
	public Query newQuery() {
		return new QueryImpl();
	}

	protected HistoryProvider getRepository(Room room) {
		return getRepository(room.getRoomJID().getDomain());
	}

	@Override
	protected HistoryProvider getRepository(String domain) {
		if (mapComponentToBareDomain) {
			int idx = domain.indexOf(".");
			if (idx > 0) {
				domain = domain.substring(idx + 1);
			}
		}
		return super.getRepository(domain);
	}


	@Override
	protected Class<? extends HistoryProvider> findClassForDataSource(DataSource dataSource) throws DBInitException {
		return DataSourceHelper.getDefaultClass(HistoryProvider.class, dataSource.getResourceUri());
	}

	public static class HistoryProviderConfigBean extends MDRepositoryBean.MDRepositoryConfigBean<HistoryProvider> {

//		@Override
//		public void register(Kernel kernel) {
//			kernel.getParent().ln("muc-dao", kernel, "muc-dao");
//			super.register(kernel);
//		}

		@Override
		protected Class<?> getRepositoryClassName() throws DBInitException, ClassNotFoundException {
			String cls = getCls();
			if (cls == null) {
				cls = "default";
			}

			switch (cls) {
				case "memory":
					return MemoryHistoryProvider.class;
				case "none":
					return NoneHistoryProvider.class;
				case "default":
					return super.getRepositoryClassName();
				default:
					return ModulesManagerImpl.getInstance().forName(cls);

				// Old history providers - should be removed in future
				case "mysql":
					return MySqlHistoryProvider.class;
				case "postgresql":
					return PostgreSqlHistoryProvider.class;
				case "sqlserver":
					return SqlserverSqlHistoryProvider.class;
				case "derby":
					return DerbySqlHistoryProvider.class;
			}
		}
	}
}
