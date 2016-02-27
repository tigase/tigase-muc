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

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Date;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import tigase.component.PacketWriter;
import tigase.db.DataRepository;
import tigase.db.Repository;
import tigase.muc.Room;
import tigase.xml.Element;
import tigase.xmpp.JID;

/**
 * @author bmalkow
 * 
 */
@Repository.Meta( supportedUris = { "jdbc:sqlserver:.*", "jdbc:jtds:.*" } )
public class SqlserverSqlHistoryProvider extends AbstractJDBCHistoryProvider {

	public static final String ADD_MESSAGE_QUERY_VAL = "insert into muc_history (room_name, event_type, timestamp, sender_jid, sender_nickname, body, public_event, msg) values (?, 1, ?, ?, ?, ?, ?, ?)";

	private static final String CREATE_MUC_HISTORY_TABLE_VAL = "create table muc_history ("
			+ "room_name nvarchar(128) NOT NULL,\n" + "event_type int, \n" + "timestamp bigint,\n"
			+ "sender_jid nvarchar(2049),\n" + "sender_nickname nvarchar(128),\n" + "body nvarchar(max),\n" + "public_event bit,\n "
			+ "msg nvarchar(max) " + ")";

	public static final String DELETE_MESSAGES_QUERY_VAL = "delete from muc_history where room_name=?";

	public static final String GET_MESSAGES_MAXSTANZAS_QUERY_VAL = "select room_name, event_type, timestamp, sender_jid, sender_nickname, body, msg from (select top (?) * from muc_history where room_name=? order by timestamp desc  ) AS t order by t.timestamp";

	public static final String GET_MESSAGES_SINCE_QUERY_VAL = "select room_name, event_type, timestamp, sender_jid, sender_nickname, body, msg from (select top (?) * from muc_history where room_name= ? and timestamp >= ? order by timestamp desc  ) AS t order by t.timestamp";

	public static final String CHECK_TEXT_FIELD_INVALID_TYPES = "select 1 from [INFORMATION_SCHEMA].[COLUMNS] where [TABLE_NAME] = 'muc_history' and ([COLUMN_NAME] = 'body' or [COLUMN_NAME] = 'msg') and [DATA_TYPE] = 'TEXT' and [TABLE_CATALOG] = DB_NAME()";

	private Logger log = Logger.getLogger(this.getClass().getName());

	/**
	 * @param dataRepository
	 */
	public SqlserverSqlHistoryProvider() {
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
	public void getHistoryMessages(Room room, JID senderJID, Integer maxchars, Integer maxstanzas, Integer seconds, Date since,
			PacketWriter writer) {
		final String roomJID = room.getRoomJID().toString();

		int maxMessages = room.getConfig().getMaxHistory();
		try {
			ResultSet rs = null;
			if (since != null) {
				if (log.isLoggable(Level.FINEST)) {
					log.finest("Using SINCE selector: roomJID=" + roomJID + ", since=" + since.getTime() + " (" + since + ")");
				}
				PreparedStatement st = dataRepository.getPreparedStatement(senderJID.getBareJID(), GET_MESSAGES_SINCE_QUERY_KEY);
				synchronized (st) {
					try {
						st.setInt(1, maxMessages);
						st.setString(2, roomJID);
						st.setLong(3, since.getTime());
						rs = st.executeQuery();
						processResultSet(room, senderJID, writer, rs);
					} finally {
						dataRepository.release(null, rs);
					}
				}
			} else if (maxstanzas != null) {
				if (log.isLoggable(Level.FINEST)) {
					log.finest("Using MAXSTANZAS selector: roomJID=" + roomJID + ", maxstanzas=" + maxstanzas);
				}
				PreparedStatement st = dataRepository.getPreparedStatement(senderJID.getBareJID(),
						GET_MESSAGES_MAXSTANZAS_QUERY_KEY);
				synchronized (st) {
					try {
						st.setInt(1, Math.min(maxstanzas, maxMessages));
						st.setString(2, roomJID);
						System.out.println("getHistoryMessages: " + st + " || \t " + GET_MESSAGES_MAXSTANZAS_QUERY_KEY);
						rs = st.executeQuery();
						processResultSet(room, senderJID, writer, rs);
					} finally {
						dataRepository.release(null, rs);
					}
				}
			} else if (seconds != null) {
				if (log.isLoggable(Level.FINEST)) {
					log.finest("Using SECONDS selector: roomJID=" + roomJID + ", seconds=" + seconds);
				}
				PreparedStatement st = dataRepository.getPreparedStatement(senderJID.getBareJID(), GET_MESSAGES_SINCE_QUERY_KEY);
				synchronized (st) {
					try {
						st.setInt(1, maxMessages);
						st.setString(2, roomJID);
						st.setLong(3, new Date().getTime() - seconds * 1000);
						rs = st.executeQuery();
						processResultSet(room, senderJID, writer, rs);
					} finally {
						dataRepository.release(null, rs);
					}
				}
			} else {
				if (log.isLoggable(Level.FINEST)) {
					log.finest("Using DEFAULT selector: roomJID=" + roomJID);
				}
				PreparedStatement st = dataRepository.getPreparedStatement(senderJID.getBareJID(),
						GET_MESSAGES_MAXSTANZAS_QUERY_KEY);
				synchronized (st) {
					try {
						st.setInt(1, maxMessages);
						st.setString(2, roomJID);
						System.out.println("getHistoryMessages: " + st.toString() + " max " + maxMessages + " roomJID " + roomJID
								+ " || \t " + GET_MESSAGES_MAXSTANZAS_QUERY_KEY);
						rs = st.executeQuery();
						processResultSet(room, senderJID, writer, rs);
					} finally {
						dataRepository.release(null, rs);
					}
				}
			}

		} catch (Exception e) {
			if (log.isLoggable(Level.SEVERE))
				log.log(Level.SEVERE, "Can't get history", e);
			throw new RuntimeException(e);
		}
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
		Statement stmt = this.dataRepository.createStatement(null);
		ResultSet rs = null;
		try {
			rs = stmt.executeQuery(CHECK_TEXT_FIELD_INVALID_TYPES);
			if (rs.next()) {
				rs.close();
				rs = null;
				stmt.execute("alter table [dbo].[muc_history] alter column msg nvarchar(MAX)");
				stmt.execute("alter table [dbo].[muc_history] alter column body nvarchar(MAX)");
			}
		} finally {
			if (rs != null)
				rs.close();
			if (stmt != null)
				stmt.close();
		}

		this.dataRepository.initPreparedStatement(ADD_MESSAGE_QUERY_KEY, ADD_MESSAGE_QUERY_VAL);
		this.dataRepository.initPreparedStatement(DELETE_MESSAGES_QUERY_KEY, DELETE_MESSAGES_QUERY_VAL);
		this.dataRepository.initPreparedStatement(GET_MESSAGES_SINCE_QUERY_KEY, GET_MESSAGES_SINCE_QUERY_VAL);
		this.dataRepository.initPreparedStatement(GET_MESSAGES_MAXSTANZAS_QUERY_KEY, GET_MESSAGES_MAXSTANZAS_QUERY_VAL);
	}

}
