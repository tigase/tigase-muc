/*
 * StoredProcedures.java
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
package tigase.muc.repository.derby;

import tigase.util.Algorithms;

import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.*;

/**
 * Created by andrzej on 19.10.2016.
 */
public class StoredProcedures {

	private static final Charset UTF8 = Charset.forName("UTF-8");

	public static void tigMucCreateRoom(String roomJid, String creatorJid, java.sql.Timestamp creationDate,
										String roomName, String roomConfig, ResultSet[] data) throws SQLException {
		Connection conn = DriverManager.getConnection("jdbc:default:connection");

		conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

		try {
			PreparedStatement ps = conn.prepareStatement(
					"insert into tig_muc_rooms (jid, jid_sha1, name, config, creator, creation_date) " +
							"values (?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS);

			ps.setString(1, roomJid);
			ps.setString(2, sha1OfLower(roomJid));
			ps.setString(3, roomName);
			ps.setString(4, roomConfig);
			ps.setString(5, creatorJid);
			ps.setTimestamp(6, creationDate);

			ps.executeUpdate();
			data[0] = ps.getGeneratedKeys();
		} catch (SQLException e) {

			// e.printStackTrace();
			// log.log(Level.SEVERE, "SP error", e);
			throw e;
		} finally {
			conn.close();
		}
	}

	public static void tigMucDestroyRoom(String roomJid) throws SQLException {
		Connection conn = DriverManager.getConnection("jdbc:default:connection");

		conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

		try {
			String roomJidSha1 = sha1OfLower(roomJid);
			PreparedStatement ps = conn.prepareStatement("select room_id from tig_muc_rooms where jid_sha1 = ?");
			ps.setString(1, roomJidSha1);

			ResultSet rs = ps.executeQuery();
			if (!rs.next())
				return;

			long roomId = rs.getLong(1);

			ps = conn.prepareStatement("delete from tig_muc_room_affiliations where room_id = ?");
			ps.setLong(1, roomId);
			ps.executeUpdate();

			ps = conn.prepareStatement("delete from tig_muc_rooms where room_id = ?");
			ps.setLong(1, roomId);
			ps.executeUpdate();
		} catch (SQLException e) {

			// e.printStackTrace();
			// log.log(Level.SEVERE, "SP error", e);
			throw e;
		} finally {
			conn.close();
		}
	}

	public static void tigMucGetRoomAffiliations(Long roomId, ResultSet[] data) throws SQLException {
		Connection conn = DriverManager.getConnection("jdbc:default:connection");

		conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

		try {
			PreparedStatement ps = conn.prepareStatement(
					"select jid, affiliation from tig_muc_room_affiliations where room_id = ?");

			ps.setLong(1, roomId);
			data[0] = ps.executeQuery();
		} catch (SQLException e) {
			throw e;
		} finally {
			conn.close();
		}
	}

	public static void tigMucGetRoom(String roomJid, ResultSet[] data) throws SQLException {
		Connection conn = DriverManager.getConnection("jdbc:default:connection");

		conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

		try {
			PreparedStatement ps = conn.prepareStatement(
					"select room_id, creation_date, creator, config, subject, subject_creator_nick, subject_date" +
							" from tig_muc_rooms where jid_sha1 = ?");

			ps.setString(1, sha1OfLower(roomJid));
			data[0] = ps.executeQuery();
		} catch (SQLException e) {
			throw e;
		} finally {
			conn.close();
		}
	}

	public static void tigMucGetRoomsJids(ResultSet[] data) throws SQLException {
		Connection conn = DriverManager.getConnection("jdbc:default:connection");

		conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

		try {
			PreparedStatement ps = conn.prepareStatement(
					"select jid from tig_muc_rooms");

			data[0] = ps.executeQuery();
		} catch (SQLException e) {
			throw e;
		} finally {
			conn.close();
		}
	}

	public static void tigMucSetRoomAffiliation(Long roomId, String jid, String affiliation) throws SQLException {
		Connection conn = DriverManager.getConnection("jdbc:default:connection");

		conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

		try {
			String jidSha1 = sha1OfLower(jid);
			PreparedStatement ps = conn.prepareStatement(
					"select 1 from tig_muc_room_affiliations where room_id = ? and jid_sha1 = ?");
			ps.setLong(1, roomId);
			ps.setString(2, jidSha1);
			ResultSet rs = ps.executeQuery();

			if (rs.next()) {
				if (!"none".equals(affiliation)) {
					ps = conn.prepareStatement("update tig_muc_room_affiliations set affiliation = ?" +
													   " where room_id = ? and jid_sha1 = ?");
					ps.setString(1, affiliation);
					ps.setLong(2, roomId);
					ps.setString(3, jidSha1);
					ps.executeUpdate();
				} else {
					ps = conn.prepareStatement("delete from tig_muc_room_affiliations where room_id = ? and jid_sha1 = ?");
					ps.setLong(1, roomId);
					ps.setString(2, jidSha1);
					ps.executeUpdate();
				}
			} else {
				if (!"none".equals(affiliation)) {
					ps = conn.prepareStatement(
							"insert into tig_muc_room_affiliations (room_id, jid, jid_sha1, affiliation)" +
									" values (?, ?, ?, ?)");
					ps.setLong(1, roomId);
					ps.setString(2, jid);
					ps.setString(3, jidSha1);
					ps.setString(4, affiliation);
					ps.executeUpdate();
				}
			}
		} catch (SQLException e) {
			throw e;
		} finally {
			conn.close();
		}
	}

	public static void tigMucSetRoomSubject(Long roomId, String subject, String creator, Timestamp changeDate) throws SQLException {
		Connection conn = DriverManager.getConnection("jdbc:default:connection");

		conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

		try {
			PreparedStatement ps = conn.prepareStatement(
					"update tig_muc_rooms set subject = ?, subject_creator_nick = ?, subject_date = ? where room_id = ?");

			ps.setString(1, subject);
			ps.setString(2, creator);
			ps.setTimestamp(3, changeDate);
			ps.setLong(4, roomId);
			ps.executeUpdate();
		} catch (SQLException e) {
			throw e;
		} finally {
			conn.close();
		}
	}

	public static void tigMucSetRoomConfig(String roomJid, String name, String config) throws SQLException {
		Connection conn = DriverManager.getConnection("jdbc:default:connection");

		conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

		try {
			PreparedStatement ps = conn.prepareStatement(
					"update tig_muc_rooms set name = ?, config = ? where jid_sha1 = ?");

			ps.setString(1, name);
			ps.setString(2, config);
			ps.setString(3, sha1OfLower(roomJid));
			ps.executeUpdate();
		} catch (SQLException e) {
			throw e;
		} finally {
			conn.close();
		}
	}

	public static void tigMucAddMessage(String roomJid, Timestamp ts, String senderJid, String senderNick, String body,
										Boolean publicEvent, String msg) throws SQLException {
		Connection conn = DriverManager.getConnection("jdbc:default:connection");

		conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

		try {
			PreparedStatement ps = conn.prepareStatement(
					"insert into tig_muc_room_history (room_jid, room_jid_sha1, event_type, ts, sender_jid," +
							" sender_nickname, body, public_event, msg)" +
							" values (?, ?, 1, ?, ?, ?, ?, ?, ?)");

			ps.setString(1, roomJid);
			ps.setString(2, sha1OfLower(roomJid));
			ps.setTimestamp(3, ts);
			ps.setString(4, senderJid);
			ps.setString(5, senderNick);
			ps.setString(6, body);
			ps.setBoolean(7, publicEvent);
			ps.setString(8, msg);
			ps.executeUpdate();
		} catch (SQLException e) {
			throw e;
		} finally {
			conn.close();
		}
	}

	public static void tigMucDeleteMessages(String roomJid) throws SQLException {
		Connection conn = DriverManager.getConnection("jdbc:default:connection");

		conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

		try {
			PreparedStatement ps = conn.prepareStatement("delete from tig_muc_room_history where room_jid_sha1 = ?");

			ps.setString(1, sha1OfLower(roomJid));
			ps.executeUpdate();
		} catch (SQLException e) {
			throw e;
		} finally {
			conn.close();
		}
	}

	public static void tigMucGetMessages(String roomJid, Integer maxMessages, Timestamp since, ResultSet[] data) throws SQLException {
		Connection conn = DriverManager.getConnection("jdbc:default:connection");

		conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

		try {
			PreparedStatement ps = conn.prepareStatement(
					"select t.sender_nickname, t.ts, t.sender_jid, t.body, t.msg from (" +
							"select h.sender_nickname, h.ts, h.sender_jid, h.body, h.msg" +
							" from tig_muc_room_history h" +
							" where h.room_jid_sha1 = ?" +
							"	and (? is null or h.ts >= ?)" +
							" order by h.ts desc offset 0 rows fetch next ? rows only)"
					+ " AS t order by t.ts asc");

			ps.setString(1, sha1OfLower(roomJid));
			ps.setTimestamp(2, since);
			ps.setTimestamp(3, since);
			ps.setInt(4, maxMessages);
			data[0] = ps.executeQuery();
		} catch (SQLException e) {
			throw e;
		} finally {
			conn.close();
		}
	}

	protected static String sha1OfLower(String data) throws SQLException {
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-1");
			byte[] hash = md.digest(data.toLowerCase().getBytes(UTF8));
			return Algorithms.bytesToHex(hash);
		} catch (NoSuchAlgorithmException e) {
			throw new SQLException(e);
		}
	}

}
