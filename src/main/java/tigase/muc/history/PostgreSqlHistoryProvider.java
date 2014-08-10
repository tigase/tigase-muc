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

import java.sql.SQLException;
import java.sql.Statement;
import java.util.Date;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import tigase.db.DataRepository;
import tigase.db.Repository;
import tigase.muc.Room;
import tigase.xml.Element;
import tigase.xmpp.JID;

/**
 * @author bmalkow
 * 
 */
@Repository.Meta( supportedUris = { "jdbc:postgresql:.*" } )
public class PostgreSqlHistoryProvider extends AbstractJDBCHistoryProvider {

	public static final String ADD_MESSAGE_QUERY_VAL = "insert into muc_history (room_name, event_type, timestamp, sender_jid, sender_nickname, body, public_event, msg) values (?, 1, ?, ?, ?, ?, ?, ?)";

	private static final String CREATE_MUC_HISTORY_TABLE_VAL = "create table muc_history (" + "room_name char(128) NOT NULL,\n"
			+ "event_type int, \n" + "timestamp bigint,\n" + "sender_jid varchar(2049),\n" + "sender_nickname char(128),\n"
			+ "body text,\n" + "public_event boolean,\n " + "msg text " + ")";

	public static final String DELETE_MESSAGES_QUERY_VAL = "delete from muc_history where room_name=?";

	public static final String GET_MESSAGES_MAXSTANZAS_QUERY_VAL = "select room_name, event_type, timestamp, sender_jid, sender_nickname, body, msg from (select * from muc_history where room_name=? order by timestamp desc limit ? ) AS t order by t.timestamp";

	public static final String GET_MESSAGES_SINCE_QUERY_VAL = "select room_name, event_type, timestamp, sender_jid, sender_nickname, body, msg from (select * from muc_history where room_name=? and timestamp >= ? order by timestamp desc limit ? ) AS t order by t.timestamp";

	private Logger log = Logger.getLogger(this.getClass().getName());

	/**
	 * @param dataRepository
	 */
	public PostgreSqlHistoryProvider() {
	}

	/** {@inheritDoc} */
	@Override
	public void addJoinEvent(Room room, Date date, JID senderJID, String nickName) {
		// TODO Auto-generated method stub

	}

	/** {@inheritDoc} */
	@Override
	public void addLeaveEvent(Room room, Date date, JID senderJID, String nickName) {
		// TODO Auto-generated method stub

	}

	/** {@inheritDoc} */
	@Override
	public void addSubjectChange(Room room, Element message, String subject, JID senderJid, String senderNickname, Date time) {
		// TODO Auto-generated method stub

	}

	/** {@inheritDoc} */
	@Override
	public void init(Map<String, Object> props) {
		try {
			this.dataRepository.checkTable("muc_history", CREATE_MUC_HISTORY_TABLE_VAL);

			internalInit();
		} catch (SQLException e) {
			e.printStackTrace();
			if (log.isLoggable(Level.WARNING))
				log.log(Level.WARNING, "Initializing problem", e);
			try {
				if (log.isLoggable(Level.INFO))
					log.info("Trying to create tables: " + CREATE_MUC_HISTORY_TABLE_VAL);
				Statement st = this.dataRepository.createStatement(null);
				st.execute(CREATE_MUC_HISTORY_TABLE_VAL);

				internalInit();
			} catch (SQLException e1) {
				if (log.isLoggable(Level.WARNING))
					log.log(Level.WARNING, "Can't initialize muc history", e1);
				throw new RuntimeException(e1);
			}
		}
	}

	private void internalInit() throws SQLException {
		this.dataRepository.initPreparedStatement(ADD_MESSAGE_QUERY_KEY, ADD_MESSAGE_QUERY_VAL);
		this.dataRepository.initPreparedStatement(DELETE_MESSAGES_QUERY_KEY, DELETE_MESSAGES_QUERY_VAL);
		this.dataRepository.initPreparedStatement(GET_MESSAGES_SINCE_QUERY_KEY, GET_MESSAGES_SINCE_QUERY_VAL);
		this.dataRepository.initPreparedStatement(GET_MESSAGES_MAXSTANZAS_QUERY_KEY, GET_MESSAGES_MAXSTANZAS_QUERY_VAL);
	}

}
