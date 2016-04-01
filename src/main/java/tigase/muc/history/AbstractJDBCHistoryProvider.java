/*
 * Tigase Jabber/XMPP Multi-User Chat Component
 * Copyright (C) 2008 "Bartosz M. Małkowski" <bartosz.malkowski@tigase.org>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
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
import java.util.Date;
import java.util.Map;
import java.util.logging.Level;

import tigase.component.PacketWriter;
import tigase.db.DBInitException;
import tigase.db.DataRepository;
import tigase.db.RepositoryFactory;
import tigase.muc.Affiliation;
import tigase.muc.Room;
import tigase.muc.RoomConfig.Anonymity;
import tigase.server.Packet;
import tigase.util.TigaseStringprepException;
import tigase.xml.Element;
import tigase.xmpp.JID;

/**
 * @author bmalkow
 * 
 */
public abstract class AbstractJDBCHistoryProvider extends AbstractHistoryProvider {

	public static final String ADD_MESSAGE_QUERY_KEY = "ADD_MESSAGE_QUERY_KEY";

	public static final String DELETE_MESSAGES_QUERY_KEY = "DELETE_MESSAGES_QUERY_KEY";

	public static final String GET_MESSAGES_MAXSTANZAS_QUERY_KEY = "GET_MESSAGES_MAXSTANZAS_QUERY_KEY";

	public static final String GET_MESSAGES_SINCE_QUERY_KEY = "GET_MESSAGES_SINCE_QUERY_KEY";

	protected DataRepository dataRepository;

	/**
	 * @param dataRepository
	 */
	public AbstractJDBCHistoryProvider() {
	}

	/** {@inheritDoc} */
	@Override
	public void addMessage(Room room, Element message, String body, JID senderJid, String senderNickname, Date time) {
		PreparedStatement st = null;
		try {
			st = this.dataRepository.getPreparedStatement(null, ADD_MESSAGE_QUERY_KEY);

			synchronized (st) {
				st.setString(1, room.getRoomJID().toString());
				st.setLong(2, time == null ? null : time.getTime());
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
			dataRepository.release(null, null);
		}
	}

	@Override
	public void destroy() {
		// we have nothing to release as we use DataRepository instance which is
		// cached by RepositoryFactory and may be used in other places
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
				PreparedStatement st = dataRepository.getPreparedStatement(senderJID.getBareJID(),
						GET_MESSAGES_SINCE_QUERY_KEY);
				synchronized (st) {
					try {
						st.setString(1, roomJID);
						st.setLong(2, since.getTime());
						st.setInt(3, maxMessages);
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
						st.setString(1, roomJID);
						st.setInt(2, Math.min(maxstanzas, maxMessages));
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
				PreparedStatement st = dataRepository.getPreparedStatement(senderJID.getBareJID(),
						GET_MESSAGES_SINCE_QUERY_KEY);
				synchronized (st) {
					try {
						st.setString(1, roomJID);
						st.setLong(2, new Date().getTime() - seconds * 1000);
						st.setInt(3, maxMessages);
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
						st.setString(1, roomJID);
						st.setInt(2, maxMessages);
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

	@Override
	public void initRepository(String resource_uri, Map<String, String> params) throws DBInitException {
		try {
			dataRepository = RepositoryFactory.getDataRepository(null, resource_uri, params);
		} catch (Exception ex) {
			throw new DBInitException("Error during initialization of repository", ex);
		}
	}

	@Override
	public final boolean isPersistent() {
		return true;
	}

	protected void processResultSet(Room room, JID senderJID, PacketWriter writer, ResultSet rs)
			throws SQLException, TigaseStringprepException {
		if (log.isLoggable(Level.FINEST)) {
			log.finest("Select messages for " + senderJID + " from room " + room.getRoomJID());
		}

		Affiliation recipientAffiliation = room.getAffiliation(senderJID.getBareJID());
		boolean addRealJids = room.getConfig().getRoomAnonymity() == Anonymity.nonanonymous
				|| room.getConfig().getRoomAnonymity() == Anonymity.semianonymous
						&& (recipientAffiliation == Affiliation.owner || recipientAffiliation == Affiliation.admin);

		while (rs.next()) {
			String msgSenderNickname = rs.getString("sender_nickname");
			Date msgTimestamp = new Date(rs.getLong("timestamp"));
			String msgSenderJid = rs.getString("sender_jid");
			String body = rs.getString("body");
			String msg = rs.getString("msg");

			Packet m = createMessage(room.getRoomJID(), senderJID, msgSenderNickname, msg, body, msgSenderJid, addRealJids,
					msgTimestamp);
			writer.write(m);
		}
	}

	@Override
	public void removeHistory(Room room) {
		PreparedStatement st = null;
		try {
			st = this.dataRepository.getPreparedStatement(null, DELETE_MESSAGES_QUERY_KEY);

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
			dataRepository.release(null, null);
		}
	}

}
