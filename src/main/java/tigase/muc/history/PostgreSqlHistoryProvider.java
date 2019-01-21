/**
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

import tigase.db.DataRepository;
import tigase.muc.Room;
import tigase.xml.Element;
import tigase.xmpp.jid.JID;

import java.sql.SQLException;
import java.sql.Statement;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author bmalkow
 */
public class PostgreSqlHistoryProvider
		extends AbstractJDBCHistoryProvider {

	public static final String ADD_MESSAGE_QUERY_VAL = "insert into muc_history (room_name, event_type, timestamp, sender_jid, sender_nickname, body, public_event, msg) values (?, 1, ?, ?, ?, ?, ?, ?)";
	public static final String DELETE_MESSAGES_QUERY_VAL = "delete from muc_history where room_name=?";
	public static final String GET_MESSAGES_MAXSTANZAS_QUERY_VAL = "select room_name, event_type, timestamp, sender_jid, sender_nickname, body, msg from (select * from muc_history where room_name=? order by timestamp desc limit ? ) AS t order by t.timestamp";
	public static final String GET_MESSAGES_SINCE_QUERY_VAL = "select room_name, event_type, timestamp, sender_jid, sender_nickname, body, msg from (select * from muc_history where room_name=? and timestamp >= ? order by timestamp desc limit ? ) AS t order by t.timestamp";
	private static final String CREATE_MUC_HISTORY_TABLE_VAL =
			"create table muc_history (" + "room_name char(128) NOT NULL,\n" + "event_type int, \n" +
					"timestamp bigint,\n" + "sender_jid varchar(2049),\n" + "sender_nickname char(128),\n" +
					"body text,\n" + "public_event boolean,\n " + "msg text " + ")";
	private Logger log = Logger.getLogger(this.getClass().getName());

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
	public void addSubjectChange(Room room, Element message, String subject, JID senderJid, String senderNickname,
								 Date time) {
		// TODO Auto-generated method stub

	}

	public void init(DataRepository dataRepository) {
		try {
			dataRepository.checkTable("muc_history", CREATE_MUC_HISTORY_TABLE_VAL);

			internalInit(dataRepository);
		} catch (SQLException e) {
			if (log.isLoggable(Level.WARNING)) {
				log.log(Level.WARNING, "Initializing problem", e);
			}
			try {
				if (log.isLoggable(Level.INFO)) {
					log.info("Trying to create tables: " + CREATE_MUC_HISTORY_TABLE_VAL);
				}
				Statement st = dataRepository.createStatement(null);
				st.execute(CREATE_MUC_HISTORY_TABLE_VAL);

				internalInit(dataRepository);
			} catch (SQLException e1) {
				if (log.isLoggable(Level.WARNING)) {
					log.log(Level.WARNING, "Can't initialize muc history", e1);
				}
				throw new RuntimeException(e1);
			}
		}
	}

	@Override
	public void setDataSource(DataRepository dataSource) {
		init(dataSource);
		super.setDataSource(dataSource);
	}

	private void internalInit(DataRepository dataRepository) throws SQLException {
		dataRepository.initPreparedStatement(ADD_MESSAGE_QUERY_KEY, ADD_MESSAGE_QUERY_VAL);
		dataRepository.initPreparedStatement(DELETE_MESSAGES_QUERY_KEY, DELETE_MESSAGES_QUERY_VAL);
		dataRepository.initPreparedStatement(GET_MESSAGES_SINCE_QUERY_KEY, GET_MESSAGES_SINCE_QUERY_VAL);
		dataRepository.initPreparedStatement(GET_MESSAGES_MAXSTANZAS_QUERY_KEY, GET_MESSAGES_MAXSTANZAS_QUERY_VAL);
	}

}
