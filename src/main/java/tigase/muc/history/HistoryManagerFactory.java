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

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import tigase.conf.ConfigurationException;

import tigase.db.DataRepository;
import tigase.db.RepositoryFactory;

/**
 * @author bmalkow
 * 
 */
public class HistoryManagerFactory {

	public static final String DB_CLASS_KEY = "history-db";

	public static final String DB_URI_KEY = "history-db-uri";

	protected static final Logger log = Logger.getLogger(HistoryManagerFactory.class.getName());

	public static HistoryProvider getHistoryManager(Map<String, Object> params) throws ConfigurationException {
		try {
			String uri = (String) params.get(DB_URI_KEY);
			String cl = (String) params.get(DB_CLASS_KEY);

			if (uri == null && cl == null)
				return null;

			if (log.isLoggable(Level.CONFIG))
				log.config("Using History Provider"
								+ "; params.size: " + params.size()
								+ "; uri: " + uri
								+ "; cl: " + cl);
			Class<? extends HistoryProvider> cls = null;
			if (cl != null) {
				if (cl.trim().equals("none")) {
					return new NoneHistoryProvider();
				} else if (cl.trim().equals("memory")) {
					return new MemoryHistoryProvider();
				} else if (cl.contains("mysql")) {
					cls = MySqlHistoryProvider.class;
				} else if (cl.contains("derby")) {
					cls = DerbySqlHistoryProvider.class;
				} else if (cl.contains("pgsql")) {
					cls = PostgreSqlHistoryProvider.class;
				} else if (cl.contains("sqlserver")) {
					cls = SqlserverSqlHistoryProvider.class;
				}
			}
			if (cls == null) {
				cls = RepositoryFactory.getRepoClass(HistoryProvider.class, uri);
			}
			if (cls == null) {
				throw new ConfigurationException("Not found implementation of History Provider for " + uri);
			}
			
			HistoryProvider provider = cls.newInstance();
			provider.initRepository(uri, null);
			return provider;		
		} catch (Exception e) {
			throw new ConfigurationException("Cannot initialize History Provider", e);
		}
	}
}
