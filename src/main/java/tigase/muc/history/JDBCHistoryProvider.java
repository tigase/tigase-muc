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
import tigase.component.exceptions.ComponentException;
import tigase.db.DataRepository;
import tigase.db.Repository;
import tigase.db.TigaseDBException;
import tigase.db.util.RepositoryVersionAware;
import tigase.kernel.beans.config.ConfigField;
import tigase.muc.Room;
import tigase.muc.repository.Schema;
import tigase.server.Packet;
import tigase.util.stringprep.TigaseStringprepException;
import tigase.xml.Element;
import tigase.xmpp.Authorization;
import tigase.xmpp.jid.JID;
import tigase.xmpp.mam.MAMRepository;
import tigase.xmpp.mam.Query;
import tigase.xmpp.mam.QueryImpl;

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
@Repository.Meta(supportedUris = {"jdbc:.*"})
@Repository.SchemaId(id = Schema.MUC_SCHEMA_ID, name = Schema.MUC_SCHEMA_NAME)
public class JDBCHistoryProvider
		extends AbstractHistoryProvider<DataRepository>
		implements HistoryProvider<DataRepository>, MAMRepository, RepositoryVersionAware {

	private static final Logger log = Logger.getLogger(JDBCHistoryProvider.class.getCanonicalName());
	protected DataRepository data_repo;
	@ConfigField(desc = "Query to append message to history", alias = "add-message-query")
	private String addMessageQuery = "{ call Tig_MUC_AddMessage(?,?,?,?,?,?,?) }";
	@ConfigField(desc = "Delete messages from history", alias = "delete-messages-query")
	private String deleteMessagesQuery = "{ call Tig_MUC_DeleteMessages(?) }";
	@ConfigField(desc = "Retrieve messages from history", alias = "get-messages-query")
	private String getMessagesQuery = "{ call Tig_MUC_GetMessages(?,?,?) }";
	@ConfigField(desc = "Retrieve position of message in archive", alias = "mam-get-message-position-query")
	private String mamGetMessagePositionQuery = "{ call Tig_MUC_MAM_GetMessagePosition(?,?,?,?,?) }";
	@ConfigField(desc = "Retrieve messages from archive", alias = "mam-get-messages-count-query")
	private String mamGetMessagesCountQuery = "{ call Tig_MUC_MAM_GetMessagesCount(?,?,?,?) }";
	@ConfigField(desc = "Retrieve messages from archive", alias = "mam-get-messages-query")
	private String mamGetMessagesQuery = "{ call Tig_MUC_MAM_GetMessages(?,?,?,?,?,?) }";

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
				data_repo.setTimestamp(st, 2, new Timestamp(time.getTime()));
				st.setString(3, senderJid.toString());
				st.setString(4, senderNickname);
				st.setString(5, body);
				st.setBoolean(6, room.getConfig().isLoggingEnabled());
				st.setString(7, message == null ? null : message.toString());

				st.executeUpdate();
			}
		} catch (SQLException e) {
			if (e.getErrorCode() == 1366 ||
					e.getMessage() != null && e.getMessage().startsWith("Incorrect string value")) {
				log.log(Level.WARNING,
						"Your MySQL configuration can't handle extended Unicode (for example emoji) correctly. Please refer to <Support for emoji and other icons> section of the server documentation");
			} else {
				log.log(Level.WARNING, "Can't add MUC message to database", e);
			}
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
			if (maxchars != null && maxchars == 0) {
				return;
			} else if (since != null) {
				if (log.isLoggable(Level.FINEST)) {
					log.finest(
							"Using SINCE selector: roomJID=" + roomJID + ", since=" + since.getTime() + " (" + since +
									")");
				}
				getMessagesSince(room, senderJID, maxMessages,
								 since.getTime() == 0 ? null : new Timestamp(since.getTime()), writer);
			} else if (maxstanzas != null) {
				if (log.isLoggable(Level.FINEST)) {
					log.finest("Using MAXSTANZAS selector: roomJID=" + roomJID + ", maxstanzas=" + maxstanzas);
				}
				getMessagesSince(room, senderJID, Math.min(maxstanzas, maxMessages), null, writer);
			} else if (seconds != null) {
				if (log.isLoggable(Level.FINEST)) {
					log.finest("Using SECONDS selector: roomJID=" + roomJID + ", seconds=" + seconds);
				}
				getMessagesSince(room, senderJID, maxMessages,
								 new Timestamp(System.currentTimeMillis() - seconds * 1000), writer);
			} else {
				if (log.isLoggable(Level.FINEST)) {
					log.finest("Using DEFAULT selector: roomJID=" + roomJID);
				}
				getMessagesSince(room, senderJID, maxMessages, null, writer);
			}

		} catch (Exception e) {
			if (log.isLoggable(Level.SEVERE)) {
				log.log(Level.SEVERE, "Can't get history", e);
			}
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

				if (log.isLoggable(Level.FINE)) {
					log.fine("Removing history of room " + room.getRoomJID() + " from database.");
				}
				if (log.isLoggable(Level.FINEST)) {
					log.finest("Executing " + st.toString());
				}

				st.executeUpdate();
			}
		} catch (SQLException e) {
			if (log.isLoggable(Level.WARNING)) {
				log.log(Level.WARNING, "Can't delete MUC messages from database", e);
			}
			throw new RuntimeException(e);
		} finally {
			data_repo.release(null, null);
		}
	}

	@Override
	public void queryItems(Query query, ItemHandler itemHandler) throws TigaseDBException, ComponentException {
		try {
			Integer count = countItems(query);
			if (count == null) {
				count = 0;
			}

			Integer after = getItemPosition(query.getRsm().getAfter(), query);
			Integer before = getItemPosition(query.getRsm().getBefore(), query);

			AbstractHistoryProvider.calculateOffsetAndPosition(query, count, before, after);

			PreparedStatement st = data_repo.getPreparedStatement(query.getQuestionerJID().getBareJID(),
																  mamGetMessagesQuery);

			synchronized (st) {
				ResultSet rs = null;
				try {
					int i = setStatementParamsForMAM(st, query);
					st.setInt(i++, query.getRsm().getMax());
					st.setInt(i++, query.getRsm().getIndex());

					rs = st.executeQuery();

					while (rs.next()) {
						String msgSenderNickname = rs.getString("sender_nickname");
						Date msgTimestamp = data_repo.getTimestamp(rs, "ts");
						String msgSenderJid = rs.getString("sender_jid");
						String body = rs.getString("body");
						String msg = rs.getString("msg");

						Element msgEl = createMessageElement(query.getComponentJID().getBareJID(),
															 query.getQuestionerJID(), msgSenderNickname, msg, body);

						Item item = new Item() {
							@Override
							public String getId() {
								return String.valueOf(msgTimestamp.getTime());
							}

							@Override
							public Element getMessage() {
								return msgEl;
							}

							@Override
							public Date getTimestamp() {
								return msgTimestamp;
							}
						};
						itemHandler.itemFound(query, item);
					}
				} finally {
					data_repo.release(null, rs);
				}
			}
		} catch (SQLException | TigaseStringprepException ex) {
			throw new TigaseDBException("Cound not retrieve items", ex);
		}

	}

	@Override
	public Query newQuery() {
		return new QueryImpl();
	}

	public void setDataSource(DataRepository dataSource) {
		try {
			initPreparedStatements(dataSource);
		} catch (SQLException ex) {
			new RuntimeException("Failed to initialize access to SQL database for PubSubDAOJDBC", ex);
		}
		this.data_repo = dataSource;
	}

	protected void getMessagesSince(Room room, JID senderJID, int maxMessages, Timestamp since, PacketWriter writer)
			throws SQLException, TigaseStringprepException {
		PreparedStatement st = data_repo.getPreparedStatement(senderJID.getBareJID(), getMessagesQuery);
		synchronized (st) {
			ResultSet rs = null;
			try {
				st.setString(1, room.getRoomJID().toString());
				st.setInt(2, maxMessages);
				data_repo.setTimestamp(st, 3, since);
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

		boolean addRealJids = isAllowedToSeeJIDs(senderJID.getBareJID(), room);

		while (rs.next()) {
			String msgSenderNickname = rs.getString("sender_nickname");
			Date msgTimestamp = data_repo.getTimestamp(rs, "ts");
			String msgSenderJid = rs.getString("sender_jid");
			String body = rs.getString("body");
			String msg = rs.getString("msg");

			Packet m = createMessage(room.getRoomJID(), senderJID, msgSenderNickname, msg, body, msgSenderJid,
									 addRealJids, msgTimestamp);
			writer.write(m);
		}
	}

	protected void initPreparedStatements(DataRepository repo) throws SQLException {
		repo.initPreparedStatement(addMessageQuery, addMessageQuery);
		repo.initPreparedStatement(deleteMessagesQuery, deleteMessagesQuery);
		repo.initPreparedStatement(getMessagesQuery, getMessagesQuery);
		repo.initPreparedStatement(mamGetMessagesQuery, mamGetMessagesQuery);
		repo.initPreparedStatement(mamGetMessagesCountQuery, mamGetMessagesCountQuery);
		repo.initPreparedStatement(mamGetMessagePositionQuery, mamGetMessagePositionQuery);
	}

	private int setStatementParamsForMAM(PreparedStatement st, Query query) throws SQLException {
		int i = 1;
		st.setString(i++, query.getComponentJID().getBareJID().toString());
		if (query.getStart() != null) {
			data_repo.setTimestamp(st, i++, new Timestamp(query.getStart().getTime()));
		} else {
			st.setObject(i++, null);
		}
		if (query.getEnd() != null) {
			data_repo.setTimestamp(st, i++, new Timestamp(query.getEnd().getTime()));
		} else {
			st.setObject(i++, null);
		}
		if (query.getWith() != null) {
			st.setString(i++, query.getWith().toString());
		} else {
			st.setObject(i++, null);
		}
		return i;
	}

	private Integer countItems(Query query) throws TigaseDBException {
		try {
			PreparedStatement st = this.data_repo.getPreparedStatement(query.getQuestionerJID().getBareJID(),
																	   mamGetMessagesCountQuery);
			synchronized (st) {
				ResultSet rs = null;
				try {
					setStatementParamsForMAM(st, query);

					rs = st.executeQuery();
					if (rs.next()) {
						return rs.getInt(1);
					} else {
						return null;
					}
				} finally {
					data_repo.release(null, rs);
				}
			}
		} catch (SQLException ex) {
			throw new TigaseDBException("Failed to retrieve number of messages for room " + query.getComponentJID(),
										ex);
		}
	}

	private Integer getItemPosition(String msgId, Query query) throws TigaseDBException, ComponentException {
		if (msgId == null) {
			return null;
		}

		try {
			java.sql.Timestamp ts = new Timestamp(Long.parseLong(msgId));

			PreparedStatement st = this.data_repo.getPreparedStatement(query.getQuestionerJID().getBareJID(),
																	   mamGetMessagePositionQuery);
			synchronized (st) {
				ResultSet rs = null;
				try {
					int i = setStatementParamsForMAM(st, query);
					data_repo.setTimestamp(st, i++, ts);

					rs = st.executeQuery();
					if (rs.next()) {
						return rs.getInt(1);
					} else {
						return null;
					}
				} finally {
					data_repo.release(null, rs);
				}
			}
		} catch (NumberFormatException ex) {
			throw new ComponentException(Authorization.ITEM_NOT_FOUND, "Not found message with id = " + msgId);
		} catch (SQLException ex) {
			throw new TigaseDBException("Can't find position for message with id " + msgId + " in archive for room " +
												query.getComponentJID(), ex);
		}
	}

}
