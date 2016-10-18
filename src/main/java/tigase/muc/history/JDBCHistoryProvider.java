/*
 * JDBCHistoryProvider.java
 *
 * Tigase Multi User Chat Component
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
 */
package tigase.muc.history;

import tigase.component.PacketWriter;
import tigase.db.DataRepository;
import tigase.db.Repository;
import tigase.kernel.beans.config.ConfigField;
import tigase.muc.Affiliation;
import tigase.muc.Room;
import tigase.muc.RoomConfig;
import tigase.server.Packet;
import tigase.util.TigaseStringprepException;
import tigase.xml.Element;
import tigase.xmpp.JID;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by andrzej on 17.10.2016.
 */
@Repository.Meta(supportedUris = {"jdbc:.*" })
public class JDBCHistoryProvider implements HistoryProvider<DataRepository> {

	private static final Logger log = Logger.getLogger(JDBCHistoryProvider.class.getCanonicalName());

	@ConfigField(desc = "Query to append message to history", alias = "add-message-query")
	private String addMessageQuery = "{ call Tig_MUC_AddMessage(?,?,?,?,?,?,?) }";
	@ConfigField(desc = "Delete messages from history", alias = "delete-messages-query")
	private String deleteMessagesQuery = "{ call Tig_MUC_DeleteMessages(?) }";
	@ConfigField(desc = "Retrieve messages from history", alias = "get-messages-query")
	private String getMessagesQuery = "{ call Tig_MUC_GetMessages(?,?,?) }";

	protected DataRepository data_repo;

	@Override
	public void addJoinEvent(Room room, Date date, JID senderJID, String nickName) {
		// TODO Auto-generated method stub
	}

	@Override
	public void addLeaveEvent(Room room, Date date, JID senderJID, String nickName) {
		// TODO Auto-generated method stub
	}

	@Override
	public void addMessage(Room room, Element message, String body, JID senderJid, String senderNickname, Date time) {
		PreparedStatement st = null;
		try {
			st = this.data_repo.getPreparedStatement(senderJid.getBareJID(), addMessageQuery);

			synchronized (st) {
				st.setString(1, room.getRoomJID().toString());
				st.setTimestamp(2, new Timestamp(time.getTime()));
				st.setString(3, senderJid.toString());
				st.setString(4, senderNickname);
				st.setString(5, body);
				st.setBoolean(6, room.getConfig().isLoggingEnabled());
				st.setString(7, message == null ? null : message.toString());

				st.executeUpdate();
			}
		} catch (SQLException e) {
			log.log(Level.WARNING, "Can't add MUC message to database", e);
			throw new RuntimeException(e);
		} finally {
			data_repo.release(null, null);
		}

	}

	@Override
	public void addSubjectChange(Room room, Element message, String subject, JID senderJid, String senderNickname,
								 Date time) {
		// TODO Auto-generated method stub
	}

	@Override
	public void destroy() {

	}

	@Override
	public void getHistoryMessages(Room room, JID senderJID, Integer maxchars, Integer maxstanzas, Integer seconds,
								   Date since, PacketWriter writer) {
		final String roomJID = room.getRoomJID().toString();

		int maxMessages = room.getConfig().getMaxHistory();
		try {
			if (since != null) {
				if (log.isLoggable(Level.FINEST)) {
					log.finest("Using SINCE selector: roomJID=" + roomJID + ", since=" + since.getTime() + " (" + since + ")");
				}
				getMessagesSince(room, senderJID, maxMessages, new Timestamp(since.getTime()), writer);
			} else if (maxstanzas != null) {
				if (log.isLoggable(Level.FINEST)) {
					log.finest("Using MAXSTANZAS selector: roomJID=" + roomJID + ", maxstanzas=" + maxstanzas);
				}
				getMessagesSince(room, senderJID, Math.min(maxstanzas, maxMessages), null, writer);
			} else if (seconds != null) {
				if (log.isLoggable(Level.FINEST)) {
					log.finest("Using SECONDS selector: roomJID=" + roomJID + ", seconds=" + seconds);
				}
				getMessagesSince(room, senderJID, maxMessages, new Timestamp(System.currentTimeMillis() - seconds * 1000), writer);
			} else {
				if (log.isLoggable(Level.FINEST)) {
					log.finest("Using DEFAULT selector: roomJID=" + roomJID);
				}
				getMessagesSince(room, senderJID, maxMessages, null, writer);
			}

		} catch (Exception e) {
			if (log.isLoggable(Level.SEVERE)) log.log(Level.SEVERE, "Can't get history", e);
			throw new RuntimeException(e);
		}
	}

	@Override
	public boolean isPersistent(Room room) {
		return false;
	}

	@Override
	public void removeHistory(Room room) {
		PreparedStatement st = null;
		try {
			st = this.data_repo.getPreparedStatement(null, deleteMessagesQuery);

			synchronized (st) {
				st.setString(1, room.getRoomJID().toString());

				if (log.isLoggable(Level.FINE))
					log.fine("Removing history of room " + room.getRoomJID() + " from database.");
				if (log.isLoggable(Level.FINEST))
					log.finest("Executing " + st.toString());

				st.executeUpdate();
			}
		} catch (SQLException e) {
			if (log.isLoggable(Level.WARNING))
				log.log(Level.WARNING, "Can't delete MUC messages from database", e);
			throw new RuntimeException(e);
		} finally {
			data_repo.release(null, null);
		}
	}

	protected void getMessagesSince(Room room, JID senderJID, int maxMessages, Timestamp since,
									PacketWriter writer) throws SQLException, TigaseStringprepException {
		PreparedStatement st = data_repo.getPreparedStatement(senderJID.getBareJID(), getMessagesQuery);
		synchronized (st) {
			ResultSet rs = null;
			try {
				st.setString(1, room.getRoomJID().toString());
				st.setInt(2, maxMessages);
				st.setTimestamp(3, since);
				rs = st.executeQuery();
				processResultSet(room, senderJID, writer, rs);
			} finally {
				data_repo.release(null, rs);
			}
		}
	}

	protected void processResultSet(Room room, JID senderJID, PacketWriter writer, ResultSet rs)
			throws SQLException, TigaseStringprepException {
		if (log.isLoggable(Level.FINEST)) {
			log.finest("Select messages for " + senderJID + " from room " + room.getRoomJID());
		}

		Affiliation recipientAffiliation = room.getAffiliation(senderJID.getBareJID());
		boolean addRealJids = room.getConfig().getRoomAnonymity() == RoomConfig.Anonymity.nonanonymous
				|| room.getConfig().getRoomAnonymity() == RoomConfig.Anonymity.semianonymous
				&& (recipientAffiliation == Affiliation.owner || recipientAffiliation == Affiliation.admin);

		while (rs.next()) {
			String msgSenderNickname = rs.getString("sender_nickname");
			Date msgTimestamp = rs.getTimestamp("ts");
			String msgSenderJid = rs.getString("sender_jid");
			String body = rs.getString("body");
			String msg = rs.getString("msg");

			Packet m = AbstractHistoryProvider.createMessage(room.getRoomJID(), senderJID, msgSenderNickname, msg, body, msgSenderJid, addRealJids,
															 msgTimestamp);
			writer.write(m);
		}
	}

	public void setDataSource(DataRepository dataSource) {
		try {
			initPreparedStatements(dataSource);
		} catch (SQLException ex) {
			new RuntimeException("Failed to initialize access to SQL database for PubSubDAOJDBC", ex);
		}
		this.data_repo = dataSource;
	}

	protected void initPreparedStatements(DataRepository repo) throws SQLException {
		repo.initPreparedStatement(addMessageQuery, addMessageQuery);
		repo.initPreparedStatement(deleteMessagesQuery, deleteMessagesQuery);
		repo.initPreparedStatement(getMessagesQuery, getMessagesQuery);
	}

}
