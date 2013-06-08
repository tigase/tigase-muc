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

import tigase.component.ElementWriter;
import tigase.db.DataRepository;
import tigase.muc.Affiliation;
import tigase.muc.Room;
import tigase.muc.RoomConfig.Anonymity;
import tigase.server.Packet;
import tigase.xmpp.JID;

/**
 * @author bmalkow
 * 
 */
public class MySqlHistoryProvider extends AbstractHistoryProvider {

	public static final String ADD_MESSAGE_QUERY = "insert into muc_history (room_name, event_type, timestamp, sender_jid, sender_nickname, body, public_event) values (?, 1, ?, ?, ?, ?, ?)";

	public static final String DELETE_MESSAGES_QUERY = "delete from muc_history where room_name=?";

	public static final String GET_MESSAGES_MAXSTANZAS_QUERY = "select room_name, event_type, timestamp, sender_jid, sender_nickname, body from (select * from muc_history where room_name=? order by timestamp desc limit ? ) AS t order by t.timestamp";

	public static final String GET_MESSAGES_SINCE_QUERY = "select room_name, event_type, timestamp, sender_jid, sender_nickname, body from (select * from muc_history where room_name=? and timestamp >= ? order by timestamp desc limit ? ) AS t order by t.timestamp";

	private final String createMucHistoryTable = "create table muc_history (" + "room_name char(128) NOT NULL,\n"
			+ "event_type int, \n" + "timestamp bigint,\n" + "sender_jid varchar(2049),\n" + "sender_nickname char(128),\n"
			+ "body text,\n" + "public_event boolean " + ")";

	private final DataRepository dataRepository;

	private Logger log = Logger.getLogger(this.getClass().getName());

	/**
	 * @param dataRepository
	 */
	public MySqlHistoryProvider(DataRepository dataRepository) {
		this.dataRepository = dataRepository;
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
	public void addMessage(Room room, String message, JID senderJid, String senderNickname, Date time) {
		try {
			PreparedStatement st = this.dataRepository.getPreparedStatement(null, ADD_MESSAGE_QUERY);

			synchronized (st) {
				st.setString(1, room.getRoomJID().toString());
				st.setLong(2, time == null ? null : time.getTime());
				st.setString(3, senderJid.toString());
				st.setString(4, senderNickname);
				st.setString(5, message);
				st.setBoolean(6, room.getConfig().isLoggingEnabled());

				st.executeUpdate();
			}
		} catch (SQLException e) {
			log.log(Level.WARNING, "Can't add MUC message to database", e);
			throw new RuntimeException(e);
		}
	}

	/** {@inheritDoc} */
	@Override
	public void addSubjectChange(Room room, String message, JID senderJid, String senderNickname, Date time) {
		// TODO Auto-generated method stub

	}

	/** {@inheritDoc} */
	@Override
	public void getHistoryMessages(Room room, JID senderJID, Integer maxchars, Integer maxstanzas, Integer seconds, Date since,
			ElementWriter writer) {
		PreparedStatement st = null;
		ResultSet rs = null;
		final String roomJID = room.getRoomJID().toString();

		int maxMessages = room.getConfig().getMaxHistory();
		try {
			if (since != null) {
				if (log.isLoggable(Level.FINEST)) {
					log.finest("Using SINCE selector: roomJID=" + roomJID + ", since=" + since.getTime() + " (" + since + ")");
				}
				st = dataRepository.getPreparedStatement(null, GET_MESSAGES_SINCE_QUERY);
				st.setString(1, roomJID);
				st.setLong(2, since.getTime());
				st.setInt(3, maxMessages);
			} else if (maxstanzas != null) {
				if (log.isLoggable(Level.FINEST)) {
					log.finest("Using MAXSTANZAS selector: roomJID=" + roomJID + ", maxstanzas=" + maxstanzas);
				}
				st = dataRepository.getPreparedStatement(null, GET_MESSAGES_MAXSTANZAS_QUERY);
				st.setString(1, roomJID);
				st.setInt(2, Math.min(maxstanzas, maxMessages));
			} else if (seconds != null) {
				if (log.isLoggable(Level.FINEST)) {
					log.finest("Using SECONDS selector: roomJID=" + roomJID + ", seconds=" + seconds);
				}
				st = dataRepository.getPreparedStatement(null, GET_MESSAGES_SINCE_QUERY);
				st.setString(1, roomJID);
				st.setLong(2, new Date().getTime() - seconds * 1000);
				st.setInt(3, maxMessages);
			} else {
				if (log.isLoggable(Level.FINEST)) {
					log.finest("Using DEFAULT selector: roomJID=" + roomJID);
				}
				st = dataRepository.getPreparedStatement(null, GET_MESSAGES_MAXSTANZAS_QUERY);
				st.setString(1, roomJID);
				st.setInt(2, maxMessages);
			}

			if (log.isLoggable(Level.FINEST)) {
				log.finest("Select messages for " + senderJID + " from room " + roomJID + ": " + st);
			}

			rs = st.executeQuery();

			Affiliation recipientAffiliation = room.getAffiliation(senderJID.getBareJID());
			boolean addRealJids = room.getConfig().getRoomAnonymity() == Anonymity.nonanonymous
					|| room.getConfig().getRoomAnonymity() == Anonymity.semianonymous
					&& (recipientAffiliation == Affiliation.owner || recipientAffiliation == Affiliation.admin);

			while (rs.next()) {
				String msgSenderNickname = rs.getString("sender_nickname");
				Date msgTimestamp = new Date(rs.getLong("timestamp"));
				String msgSenderJid = rs.getString("sender_jid");
				String msg = rs.getString("body");

				Packet m = createMessage(room.getRoomJID(), senderJID, msgSenderNickname, msg, msgSenderJid, addRealJids,
						msgTimestamp);
				writer.write(m);
			}
		} catch (Exception e) {
			if (log.isLoggable(Level.SEVERE))
				log.log(Level.SEVERE, "Can't get history", e);
			throw new RuntimeException(e);
		} finally {
			dataRepository.release(null, rs);
		}
	}

	/** {@inheritDoc} */
	@Override
	public void init(Map<String, Object> props) {
		try {
			this.dataRepository.checkTable("muc_history", createMucHistoryTable);

			internalInit();
		} catch (SQLException e) {
			e.printStackTrace();
			if (log.isLoggable(Level.WARNING))
				log.log(Level.WARNING, "Initializing problem", e);
			try {
				if (log.isLoggable(Level.INFO))
					log.info("Trying to create tables: " + createMucHistoryTable);
				Statement st = this.dataRepository.createStatement(null);
				st.execute(createMucHistoryTable);

				internalInit();
			} catch (SQLException e1) {
				if (log.isLoggable(Level.WARNING))
					log.log(Level.WARNING, "Can't initialize muc history", e1);
				throw new RuntimeException(e1);
			}
		}
	}

	private void internalInit() throws SQLException {
		this.dataRepository.initPreparedStatement(ADD_MESSAGE_QUERY, ADD_MESSAGE_QUERY);
		this.dataRepository.initPreparedStatement(DELETE_MESSAGES_QUERY, DELETE_MESSAGES_QUERY);
		this.dataRepository.initPreparedStatement(GET_MESSAGES_SINCE_QUERY, GET_MESSAGES_SINCE_QUERY);
		this.dataRepository.initPreparedStatement(GET_MESSAGES_MAXSTANZAS_QUERY, GET_MESSAGES_MAXSTANZAS_QUERY);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see tigase.muc.history.HistoryProvider#isPersistent()
	 */
	@Override
	public boolean isPersistent() {
		return true;
	}

	@Override
	public void removeHistory(Room room) {
		try {
			PreparedStatement st = this.dataRepository.getPreparedStatement(null, DELETE_MESSAGES_QUERY);

			synchronized (st) {
				st.setString(1, room.getRoomJID().toString());

				st.executeUpdate();
			}
		} catch (SQLException e) {
			if (log.isLoggable(Level.WARNING))
				log.log(Level.WARNING, "Can't delete MUC messages from database", e);
			throw new RuntimeException(e);
		}
	}
}
