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
package tigase.muc.repository;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import tigase.db.TigaseDBException;
import tigase.db.UserNotFoundException;
import tigase.db.UserRepository;
import tigase.muc.Affiliation;
import tigase.muc.MucConfig;
import tigase.muc.Room;
import tigase.muc.RoomConfig;

/**
 * @author bmalkow
 * 
 */
public class MucDAO {

	private static final String CREATION_DATE_KEY = "creation-date";

	private static final String CREATOR_JID_KEY = "creator";

	private static final String LAST_ACCESS_DATE_KEY = "last-access-date";

	private static final String ROOMS_KEY = "rooms/";

	private static final String SUBJECT_CREATOR_NICK_KEY = "creator";

	private static final String SUBJECT_DATE_KEY = "date";

	private static final String SUBJECT_KEY = "subject";

	protected Logger log = Logger.getLogger(this.getClass().getName());

	private final MucConfig mucConfig;

	private final UserRepository repository;

	public MucDAO(final MucConfig config, final UserRepository repository) throws RepositoryException {
		this.mucConfig = config;
		this.repository = repository;

		try {
			this.repository.setData(this.mucConfig.getServiceName(), "last-start", String.valueOf(System.currentTimeMillis()));
		} catch (UserNotFoundException e) {
			try {
				this.repository.addUser(this.mucConfig.getServiceName());
				this.repository.setData(this.mucConfig.getServiceName(), "last-start", String.valueOf(System.currentTimeMillis()));
			} catch (Exception e1) {
				log.log(Level.SEVERE, "MUC repository initialization problem", e1);
				throw new RepositoryException("Cannot initialize MUC repository", e);
			}
		} catch (TigaseDBException e) {
			log.log(Level.SEVERE, "MUC repository initialization problem", e);
			throw new RepositoryException("Cannot initialize MUC repository", e);
		}

	}

	public void createRoom(Room room) throws RepositoryException {
		try {
			repository.setData(mucConfig.getServiceName(), ROOMS_KEY + room.getRoomId(), CREATION_DATE_KEY,
					String.valueOf(room.getCreationDate().getTime()));
			repository.setData(mucConfig.getServiceName(), ROOMS_KEY + room.getRoomId(), CREATOR_JID_KEY, room.getCreatorJid());
			repository.setData(mucConfig.getServiceName(), ROOMS_KEY + room.getRoomId(), LAST_ACCESS_DATE_KEY,
					String.valueOf((new Date()).getTime()));

			room.getConfig().write(repository, mucConfig, ROOMS_KEY + room.getRoomId() + "/config");

			for (String affJid : room.getAffiliations()) {
				final Affiliation a = room.getAffiliation(affJid);
				setAffiliation(room.getRoomId(), affJid, a);
			}

		} catch (Exception e) {
			e.printStackTrace();
			throw new RepositoryException("Room creation error", e);
		}
	}

	/**
	 * @param roomId
	 * @throws RepositoryException
	 */
	public void destroyRoom(String roomId) throws RepositoryException {
		try {
			repository.removeSubnode(mucConfig.getServiceName(), ROOMS_KEY + roomId);
		} catch (Exception e) {
			e.printStackTrace();
			throw new RepositoryException("Room destroing error", e);
		}
	}

	public Date getCreationDate(String roomId) throws RepositoryException {
		try {
			String creationDate = repository.getData(mucConfig.getServiceName(), ROOMS_KEY + roomId, CREATION_DATE_KEY);
			if (creationDate == null)
				return null;
			Date r = new Date(Long.valueOf(creationDate));
			return r;
		} catch (Exception e) {
			throw new RepositoryException("Creation Date reading error", e);
		}

	}

	/**
	 * @param jid
	 * @return
	 * @throws RepositoryException
	 */
	public String getRoomName(String jid) throws RepositoryException {
		try {
			return repository.getData(mucConfig.getServiceName(), ROOMS_KEY + jid + "/config", "muc#roomconfig_roomname");
		} catch (Exception e) {
			e.printStackTrace();
			throw new RepositoryException("Room name reading error", e);
		}
	}

	/**
	 * @return
	 * @throws RepositoryException
	 */
	public String[] getRoomsIdList() throws RepositoryException {
		try {
			return repository.getSubnodes(mucConfig.getServiceName(), ROOMS_KEY);
		} catch (Exception e) {
			e.printStackTrace();
			throw new RepositoryException("Room list reading error", e);
		}
	}

	public String getSubject(String roomId) throws RepositoryException {
		try {
			return repository.getData(mucConfig.getServiceName(), ROOMS_KEY + roomId + "/subject", SUBJECT_KEY);
		} catch (Exception e) {
			e.printStackTrace();
			throw new RepositoryException("Subject saving error", e);
		}
	}

	public String getSubjectCreatorNickname(String roomId) throws RepositoryException {
		try {
			return repository.getData(mucConfig.getServiceName(), ROOMS_KEY + roomId + "/subject", SUBJECT_CREATOR_NICK_KEY);
		} catch (Exception e) {
			e.printStackTrace();
			throw new RepositoryException("Subject saving error", e);
		}
	}

	public Room readRoom(String roomId) throws RepositoryException {
		try {
			final String tmpDate = repository.getData(mucConfig.getServiceName(), ROOMS_KEY + roomId, CREATION_DATE_KEY);
			final String creatorJid = repository.getData(mucConfig.getServiceName(), ROOMS_KEY + roomId, CREATOR_JID_KEY);

			if (tmpDate != null && creatorJid != null) {
				Date date = new Date(Long.valueOf(tmpDate));
				RoomConfig rc = new RoomConfig(roomId);
				rc.read(repository, mucConfig, ROOMS_KEY + roomId + "/config");

				final Room room = new Room(rc, date, creatorJid);

				String subject = getSubject(roomId);
				String subjectCreator = getSubjectCreatorNickname(roomId);

				room.setNewSubject(subject, subjectCreator);

				Map<String, Affiliation> affiliations = new HashMap<String, Affiliation>();

				String[] affJids = repository.getKeys(mucConfig.getServiceName(), ROOMS_KEY + roomId + "/affiliations");
				for (final String jid : affJids) {
					String t = repository.getData(mucConfig.getServiceName(), ROOMS_KEY + roomId + "/affiliations", jid);

					Affiliation affiliation = Affiliation.valueOf(t);
					affiliations.put(jid, affiliation);

				}

				room.setAffiliations(affiliations);

				return room;
			}
			return null;
		} catch (Exception e) {
			e.printStackTrace();
			throw new RepositoryException("Room reading error", e);
		}
	}

	/**
	 * @param roomId
	 * @param affiliation
	 * @param jid
	 * @param affiliations
	 * @throws RepositoryException
	 */
	public void setAffiliation(String roomId, String jid, Affiliation affiliation) throws RepositoryException {
		try {
			if (affiliation == Affiliation.none) {
				repository.removeData(mucConfig.getServiceName(), ROOMS_KEY + roomId + "/affiliations", jid);
			} else {
				repository.setData(mucConfig.getServiceName(), ROOMS_KEY + roomId + "/affiliations", jid, affiliation.name());
			}
		} catch (Exception e) {
			e.printStackTrace();
			throw new RepositoryException("Affiliation saving error", e);
		}
	}

	/**
	 * @param roomId
	 * @param msg
	 * @throws RepositoryException
	 */
	public void setSubject(String roomId, String subject, String creatorNickname) throws RepositoryException {
		try {
			repository.setData(mucConfig.getServiceName(), ROOMS_KEY + roomId + "/subject", SUBJECT_CREATOR_NICK_KEY,
					creatorNickname);
			repository.setData(mucConfig.getServiceName(), ROOMS_KEY + roomId + "/subject", SUBJECT_KEY, subject);
			repository.setData(mucConfig.getServiceName(), ROOMS_KEY + roomId + "/subject", SUBJECT_DATE_KEY,
					String.valueOf((new Date()).getTime()));
			// TODO Auto-generated method stub
		} catch (Exception e) {
			e.printStackTrace();
			throw new RepositoryException("Subject saving error", e);
		}

	}

	/**
	 * @param roomId
	 * @throws RepositoryException
	 */
	public void updateLastAccessDate(String roomId) throws RepositoryException {
		try {
			repository.setData(mucConfig.getServiceName(), ROOMS_KEY + roomId, LAST_ACCESS_DATE_KEY,
					String.valueOf((new Date()).getTime()));
		} catch (Exception e) {
			e.printStackTrace();
			throw new RepositoryException("Last access date writing error", e);
		}
	}

	/**
	 * @param roomConfig
	 * @throws RepositoryException
	 */
	public void updateRoomConfig(RoomConfig roomConfig) throws RepositoryException {
		try {
			roomConfig.write(repository, mucConfig, ROOMS_KEY + roomConfig.getRoomId() + "/config");
		} catch (Exception e) {
			e.printStackTrace();
			throw new RepositoryException("Room config writing error", e);
		}
	}

}
