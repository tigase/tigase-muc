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
package tigase.muc.repository;

import tigase.component.exceptions.RepositoryException;
import tigase.db.DataRepository;
import tigase.db.Repository;
import tigase.db.util.RepositoryVersionAware;
import tigase.kernel.beans.Inject;
import tigase.muc.*;
import tigase.util.stringprep.TigaseStringprepException;
import tigase.xmpp.jid.BareJID;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.*;
import java.util.logging.Logger;

/**
 * @author bmalkow
 */
@Repository.Meta(supportedUris = {"jdbc:.*"}, isDefault = true)
@Repository.SchemaId(id = Schema.MUC_SCHEMA_ID, name = Schema.MUC_SCHEMA_NAME)
public class JDBCMucDAO
		extends AbstractMucDAO<DataRepository, Long>
		implements RepositoryVersionAware {

	private static final Logger log = Logger.getLogger(JDBCMucDAO.class.getName());

	private static final String CREATE_ROOM_QUERY = "{ call Tig_MUC_CreateRoom(?,?,?,?,?) }";
	private static final String DESTROY_ROOM_QUERY = "{ call Tig_MUC_DestroyRoom(?) }";
	private static final String GET_ROOM_AFFILIATIONS_QUERY = "{ call Tig_MUC_GetRoomAffiliations(?) }";
	private static final String GET_ROOM_QUERY = "{ call Tig_MUC_GetRoom(?) }";
	private static final String GET_ROOM_AVATAR_QUERY = "{ call Tig_MUC_GetRoomAvatar(?) }";
	private static final String SET_ROOM_AVATAR_QUERY = "{ call Tig_MUC_SetRoomAvatar(?,?,?) }";
	private static final String GET_ROOMS_JIDS_QUERY = "{ call Tig_MUC_GetRoomsJids() }";
	private static final String SET_ROOM_AFFILIATION_QUERY = "{ call Tig_MUC_SetRoomAffiliation(?,?,?,?,?) }";
	private static final String SET_ROOM_SUBJECT_QUERY = "{ call Tig_MUC_SetRoomSubject(?,?,?,?) }";
	private static final String SET_ROOM_CONFIG_QUERY = "{ call Tig_MUC_SetRoomConfig(?,?,?) }";
	protected DataRepository data_repo;
	@Inject
	private MUCConfig mucConfig;
	@Inject
	private Room.RoomFactory roomFactory;

	@Override
	public Long createRoom(RoomWithId<Long> room) throws RepositoryException {
		try {
			String roomName = room.getConfig().getRoomName();

			ResultSet rs = null;
			PreparedStatement stmt = data_repo.getPreparedStatement(room.getRoomJID(), CREATE_ROOM_QUERY);
			synchronized (stmt) {
				try {
					stmt.setString(1, room.getRoomJID().toString());
					stmt.setString(2, room.getCreatorJid().toString());
					data_repo.setTimestamp(stmt, 3, new Timestamp(room.getCreationDate().getTime()));
					stmt.setString(4, (roomName != null && !roomName.isEmpty()) ? roomName : null);
					stmt.setString(5, room.getConfig().getAsElement().toString());

					rs = stmt.executeQuery();

					if (rs.next()) {
						room.setId(rs.getLong(1));
					}
				} finally {
					data_repo.release(null, rs);
				}
			}
		} catch (SQLException ex) {
			throw new RepositoryException("Error while saving room " + room.getRoomJID() + " to database", ex);
		}
		if (room.getId() == null) {
			throw new RepositoryException(
					"Failed to save room " + room.getRoomJID() + " to database, did not get room id");
		}

		for (BareJID affJid : room.getAffiliations()) {
			final RoomAffiliation a = room.getAffiliation(affJid);
			setAffiliation(room, affJid, a);
		}

		return room.getId();
	}

	@Override
	public void destroyRoom(BareJID roomJID) throws RepositoryException {
		try {
			PreparedStatement stmt = data_repo.getPreparedStatement(roomJID, DESTROY_ROOM_QUERY);
			synchronized (stmt) {
				stmt.setString(1, roomJID.toString());
				stmt.execute();
			}
		} catch (SQLException ex) {
			throw new RepositoryException("Error while removing room " + roomJID + " from database", ex);
		}
	}

	@Override
	public Map<BareJID, RoomAffiliation> getAffiliations(RoomWithId<Long> room) throws RepositoryException {
		Map<BareJID, RoomAffiliation> affiliations = new HashMap<>();

		try {
			ResultSet rs = null;
			PreparedStatement stmt = data_repo.getPreparedStatement(room.getRoomJID(), GET_ROOM_AFFILIATIONS_QUERY);
			synchronized (stmt) {
				try {
					stmt.setLong(1, room.getId());
					rs = stmt.executeQuery();

					while (rs.next()) {
						String affStr = rs.getString(2);
						boolean persistent = rs.getBoolean(3);
						if (affStr.endsWith("-persistent")) {
							persistent = true;
							affStr = affStr.substring(0, affStr.length() - "-persistent".length());
						}
						Affiliation affiliation = Affiliation.valueOf(affStr);

						RoomAffiliation roomAffiliation = RoomAffiliation.from(affiliation, persistent,
																			   rs.getString(4));
						affiliations.put(BareJID.bareJIDInstance(rs.getString(1)), roomAffiliation);
					}
				} finally {
					data_repo.release(null, rs);
				}
			}
		} catch (SQLException | TigaseStringprepException ex) {
			throw new RepositoryException(
					"Error while reading room " + room.getRoomJID() + " affiliations from database", ex);
		}

		return affiliations;
	}

	@Override
	public RoomWithId<Long> getRoom(BareJID roomJID) throws RepositoryException {
		try {
			ResultSet rs = null;
			PreparedStatement stmt = data_repo.getPreparedStatement(roomJID, GET_ROOM_QUERY);
			synchronized (stmt) {
				try {
					stmt.setString(1, roomJID.toString());
					rs = stmt.executeQuery();

					if (rs.next()) {
						long roomId = rs.getLong(1);
						Date date = data_repo.getTimestamp(rs, 2);
						BareJID creator = BareJID.bareJIDInstance(rs.getString(3));
						RoomConfig roomConfig = new RoomConfig(roomJID);
						roomConfig.readFromElement(parseConfigElement(rs.getString(4)));

						RoomWithId room = roomFactory.newInstance(roomId, roomConfig, date, creator);

						String subject = rs.getString(5);
						String subjectCreator = rs.getString(6);

						room.setNewSubject(subject, subjectCreator);
						Date subjectDate = data_repo.getTimestamp(rs, 7);
						room.setSubjectChangeDate(subjectDate);

						room.setAvatarHash(rs.getString(8));

						return room;
					}

					return null;
				} finally {
					data_repo.release(null, rs);
				}
			}
		} catch (SQLException | TigaseStringprepException ex) {
			throw new RepositoryException("Error while reading room " + roomJID + " from database", ex);
		}
	}

	@Override
	public List<BareJID> getRoomsJIDList() throws RepositoryException {
		ArrayList<BareJID> jids = new ArrayList<>();
		try {
			ResultSet rs = null;
			PreparedStatement stmt = data_repo.getPreparedStatement(mucConfig.getServiceName(), GET_ROOMS_JIDS_QUERY);
			synchronized (stmt) {
				try {
					rs = stmt.executeQuery();

					while (rs.next()) {
						BareJID jid = BareJID.bareJIDInstance(rs.getString(1));
						jids.add(jid);
					}
				} finally {
					data_repo.release(null, rs);
				}
			}
		} catch (SQLException | TigaseStringprepException ex) {
			throw new RepositoryException("Error while reading list of rooms jids from database", ex);
		}
		return jids;
	}

	@Override
	public void setAffiliation(RoomWithId<Long> room, BareJID jid, RoomAffiliation affiliation)
			throws RepositoryException {
		try {
			PreparedStatement stmt = data_repo.getPreparedStatement(mucConfig.getServiceName(),
																	SET_ROOM_AFFILIATION_QUERY);
			synchronized (stmt) {
				stmt.setLong(1, room.getId());
				stmt.setString(2, jid.toString());
				stmt.setString(3, affiliation.getAffiliation().name());
				if (data_repo.getDatabaseType() == DataRepository.dbTypes.postgresql) {
					stmt.setInt(4, affiliation.isPersistentOccupant() ? 1 : 0);
				} else {
					stmt.setBoolean(4, affiliation.isPersistentOccupant());
				}
				stmt.setString(5, affiliation.getRegisteredNickname());
				stmt.execute();
			}
		} catch (SQLException ex) {
			throw new RepositoryException(
					"Error while setting affiliation for room " + room.getRoomJID() + " for jid " + jid + " to " +
							affiliation.toString(), ex);
		}
	}

	@Override
	public String getRoomAvatar(RoomWithId<Long> room) throws RepositoryException {
		try {
			ResultSet rs = null;
			PreparedStatement stmt = data_repo.getPreparedStatement(mucConfig.getServiceName(), GET_ROOM_AVATAR_QUERY);
			synchronized (stmt) {
				try {
					stmt.setLong(1, room.getId());
					rs = stmt.executeQuery();

					if (rs.next()) {
						return rs.getString(1);
					} else {
						return null;
					}
				} finally {
					data_repo.release(null, rs);
				}
			}
		} catch (SQLException ex) {
			throw new RepositoryException("Error while reading room avatar", ex);
		}
	}

	@Override
	public void updateRoomAvatar(RoomWithId<Long> room, String encodedAvatar, String hash) throws RepositoryException {
		try {
			final PreparedStatement stmt = data_repo.getPreparedStatement(mucConfig.getServiceName(),
																		  SET_ROOM_AVATAR_QUERY);
			synchronized (stmt) {
				stmt.setLong(1, room.getId());
				stmt.setString(2, encodedAvatar);
				stmt.setString(3, hash);

				stmt.execute();
			}
		} catch (SQLException ex) {
			throw new RepositoryException("Error while setting avatar for room " + room.getRoomJID(), ex);
		}
	}

	@Override
	public void setSubject(RoomWithId<Long> room, String subject, String creatorNickname, Date changeDate)
			throws RepositoryException {
		try {
			PreparedStatement stmt = data_repo.getPreparedStatement(mucConfig.getServiceName(), SET_ROOM_SUBJECT_QUERY);
			synchronized (stmt) {
				stmt.setLong(1, room.getId());
				stmt.setString(2, subject);
				stmt.setString(3, creatorNickname);
				data_repo.setTimestamp(stmt, 4, new Timestamp(changeDate.getTime()));

				stmt.execute();
			}
		} catch (SQLException ex) {
			throw new RepositoryException(
					"Error while setting subject for room " + room.getRoomJID() + " to " + subject + " by " +
							creatorNickname, ex);
		}
	}

	@Override
	public void updateRoomConfig(RoomConfig roomConfig) throws RepositoryException {
		try {
			String roomName = roomConfig.getRoomName();
			PreparedStatement stmt = data_repo.getPreparedStatement(mucConfig.getServiceName(), SET_ROOM_CONFIG_QUERY);
			synchronized (stmt) {
				stmt.setString(1, roomConfig.getRoomJID().toString());
				stmt.setString(2, (roomName != null && !roomName.isEmpty()) ? roomName : null);
				stmt.setString(3, roomConfig.getAsElement().toString());

				stmt.execute();
			}
		} catch (SQLException ex) {
			throw new RepositoryException("Error updating configuration of room " + roomConfig.getRoomJID(), ex);
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
		repo.initPreparedStatement(CREATE_ROOM_QUERY, CREATE_ROOM_QUERY);
		repo.initPreparedStatement(DESTROY_ROOM_QUERY, DESTROY_ROOM_QUERY);
		repo.initPreparedStatement(GET_ROOM_AFFILIATIONS_QUERY, GET_ROOM_AFFILIATIONS_QUERY);
		repo.initPreparedStatement(GET_ROOM_QUERY, GET_ROOM_QUERY);
		repo.initPreparedStatement(GET_ROOMS_JIDS_QUERY, GET_ROOMS_JIDS_QUERY);
		repo.initPreparedStatement(SET_ROOM_AFFILIATION_QUERY, SET_ROOM_AFFILIATION_QUERY);
		repo.initPreparedStatement(SET_ROOM_SUBJECT_QUERY, SET_ROOM_SUBJECT_QUERY);
		repo.initPreparedStatement(SET_ROOM_CONFIG_QUERY, SET_ROOM_CONFIG_QUERY);
		repo.initPreparedStatement(GET_ROOM_AVATAR_QUERY, GET_ROOM_AVATAR_QUERY);
		repo.initPreparedStatement(SET_ROOM_AVATAR_QUERY, SET_ROOM_AVATAR_QUERY);
	}

}
