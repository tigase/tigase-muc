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
package tigase.muc.repository;

import tigase.component.exceptions.RepositoryException;
import tigase.db.TigaseDBException;
import tigase.db.UserNotFoundException;
import tigase.db.UserRepository;
import tigase.muc.*;
import tigase.xmpp.BareJID;
import tigase.xmpp.JID;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author bmalkow
 */
public class MucDAO {

	public static final String ROOMS_KEY = "rooms/";
	private static final String CREATION_DATE_KEY = "creation-date";
	private static final String CREATOR_JID_KEY = "creator";
	private static final String LAST_ACCESS_DATE_KEY = "last-access-date";
	private static final String SUBJECT_CREATOR_NICK_KEY = "creator";

	private static final String SUBJECT_DATE_KEY = "date";

	private static final String SUBJECT_KEY = "subject";
	private final MucContext mucConfig;
	private final UserRepository repository;
	protected Logger log = Logger.getLogger(this.getClass().getName());

	public MucDAO(final MucContext config, final UserRepository repository) throws RepositoryException {
		this.mucConfig = config;
		this.repository = repository;

		try {
			this.repository.setData(this.mucConfig.getServiceName(), "last-start",
									String.valueOf(System.currentTimeMillis()));
		} catch (UserNotFoundException e) {
			try {
				this.repository.addUser(this.mucConfig.getServiceName());
				this.repository.setData(this.mucConfig.getServiceName(), "last-start",
										String.valueOf(System.currentTimeMillis()));
			} catch (Exception e1) {
				if (log.isLoggable(Level.SEVERE)) {
					log.log(Level.SEVERE, "MUC repository initialization problem", e1);
				}
				throw new RepositoryException("Cannot initialize MUC repository", e);
			}
		} catch (TigaseDBException e) {
			if (log.isLoggable(Level.SEVERE)) {
				log.log(Level.SEVERE, "MUC repository initialization problem", e);
			}
			throw new RepositoryException("Cannot initialize MUC repository", e);
		}

	}

	public void createRoom(Room room) throws RepositoryException {
		try {
			repository.setData(mucConfig.getServiceName(), ROOMS_KEY + room.getRoomJID(), CREATION_DATE_KEY,
							   String.valueOf(room.getCreationDate().getTime()));
			repository.setData(mucConfig.getServiceName(), ROOMS_KEY + room.getRoomJID(), CREATOR_JID_KEY,
							   room.getCreatorJid().toString());
			repository.setData(mucConfig.getServiceName(), ROOMS_KEY + room.getRoomJID(), LAST_ACCESS_DATE_KEY,
							   String.valueOf((new Date()).getTime()));

			room.getConfig().write(repository, mucConfig, ROOMS_KEY + room.getRoomJID() + "/config");
			setSubject(room.getRoomJID(), room.getSubject(), room.getSubjectChangerNick(), room.getSubjectChangeDate());

			for (BareJID affJid : room.getAffiliations()) {
				final Affiliation a = room.getAffiliation(affJid);
				setAffiliation(room.getRoomJID(), affJid, a);
			}

		} catch (Exception e) {
			e.printStackTrace();
			throw new RepositoryException("Room creation error", e);
		}
	}

	/**
	 * @param roomJID
	 *
	 * @throws RepositoryException
	 */
	public void destroyRoom(BareJID roomJID) throws RepositoryException {
		try {
			repository.removeSubnode(mucConfig.getServiceName(), ROOMS_KEY + roomJID);
		} catch (Exception e) {
			e.printStackTrace();
			throw new RepositoryException("Room destroing error", e);
		}
	}

	public Date getCreationDate(BareJID roomJID) throws RepositoryException {
		try {
			String creationDate = repository.getData(mucConfig.getServiceName(), ROOMS_KEY + roomJID,
													 CREATION_DATE_KEY);
			if (creationDate == null) {
				return null;
			}
			Date r = new Date(Long.valueOf(creationDate));
			return r;
		} catch (Exception e) {
			throw new RepositoryException("Creation Date reading error", e);
		}

	}

	public UserRepository getRepository() {
		return repository;
	}

	/**
	 * @param jid
	 *
	 * @return
	 *
	 * @throws RepositoryException
	 */
	public String getRoomName(String jid) throws RepositoryException {
		try {
			return repository.getData(mucConfig.getServiceName(), ROOMS_KEY + jid + "/config",
									  RoomConfig.MUC_ROOMCONFIG_ROOMNAME_KEY);
		} catch (Exception e) {
			e.printStackTrace();
			throw new RepositoryException("Room name reading error", e);
		}
	}

	/**
	 * @return
	 *
	 * @throws RepositoryException
	 */
	public ArrayList<BareJID> getRoomsJIDList() throws RepositoryException {
		ArrayList<BareJID> jids = new ArrayList<BareJID>();
		BareJID serviceName = mucConfig.getServiceName();
		try {
			String[] ids = repository.getSubnodes(serviceName, ROOMS_KEY);
			if (ids != null) {
				for (String id : ids) {
					jids.add(BareJID.bareJIDInstance(id));
				}
			}
			return jids;
		} catch (Exception e) {
			e.printStackTrace();
			throw new RepositoryException("Room list reading error", e);
		}
	}

	public String getSubject(BareJID roomJID) throws RepositoryException {
		try {
			return repository.getData(mucConfig.getServiceName(), ROOMS_KEY + roomJID + "/subject", SUBJECT_KEY);
		} catch (Exception e) {
			e.printStackTrace();
			throw new RepositoryException("Subject saving error", e);
		}
	}

	public Date getSubjectCreationDate(BareJID roomJID) throws RepositoryException {
		try {
			String tmp = repository.getData(mucConfig.getServiceName(), ROOMS_KEY + roomJID + "/subject",
											SUBJECT_DATE_KEY);
			return tmp == null ? null : new Date(Long.valueOf(tmp));
		} catch (Exception e) {
			e.printStackTrace();
			throw new RepositoryException("Subject change date reading error", e);
		}
	}

	public String getSubjectCreatorNickname(BareJID roomJID) throws RepositoryException {
		try {
			return repository.getData(mucConfig.getServiceName(), ROOMS_KEY + roomJID + "/subject",
									  SUBJECT_CREATOR_NICK_KEY);
		} catch (Exception e) {
			e.printStackTrace();
			throw new RepositoryException("Subject saving error", e);
		}
	}

	public Room readRoom(BareJID roomJID) throws RepositoryException {

		try {
			final String tmpDate = repository.getData(mucConfig.getServiceName(), ROOMS_KEY + roomJID,
													  CREATION_DATE_KEY);

			final String creatorJid = repository.getData(mucConfig.getServiceName(), ROOMS_KEY + roomJID,
														 CREATOR_JID_KEY);

			if (tmpDate != null && creatorJid != null) {

				JID creatorJID = JID.jidInstance(creatorJid);

				Date date = new Date(Long.valueOf(tmpDate));
				RoomConfig rc = new RoomConfig(roomJID);
				rc.read(repository, mucConfig, ROOMS_KEY + roomJID + "/config");

				final Room room = Room.newInstance(rc, date, creatorJID.getBareJID());

				String subject = getSubject(roomJID);
				String subjectCreator = getSubjectCreatorNickname(roomJID);
				Date subjectChangeDate = getSubjectCreationDate(roomJID);

				room.setNewSubject(subject, subjectCreator);
				room.setSubjectChangeDate(subjectChangeDate);

				Map<BareJID, Affiliation> affiliations = new HashMap<BareJID, Affiliation>();

				String[] affJids = repository.getKeys(mucConfig.getServiceName(),
													  ROOMS_KEY + roomJID + "/affiliations");
				if (affJids != null) {
					for (final String jid : affJids) {
						String t = repository.getData(mucConfig.getServiceName(), ROOMS_KEY + roomJID + "/affiliations",
													  jid);

						Affiliation affiliation = Affiliation.valueOf(t);
						affiliations.put(JID.jidInstance(jid).getBareJID(), affiliation);

					}
				}

				room.setAffiliations(affiliations);

				return room;
			}
			return null;
		} catch (Exception e) {
			if (log.isLoggable(Level.WARNING)) {
				log.log(Level.WARNING, "Room reading error", e);
			}
			throw new RepositoryException("Room reading error", e);
		}
	}

	/**
	 * @param roomJID
	 * @param affiliation
	 * @param jid
	 * @param affiliations
	 *
	 * @throws RepositoryException
	 */
	public void setAffiliation(BareJID roomJID, BareJID jid, Affiliation affiliation) throws RepositoryException {
		try {
			if (affiliation == Affiliation.none) {
				repository.removeData(mucConfig.getServiceName(), ROOMS_KEY + roomJID + "/affiliations",
									  jid.toString());
			} else {
				repository.setData(mucConfig.getServiceName(), ROOMS_KEY + roomJID + "/affiliations", jid.toString(),
								   affiliation.name());
			}
		} catch (Exception e) {
			e.printStackTrace();
			throw new RepositoryException("Affiliation saving error", e);
		}
	}

	/**
	 * @param roomJID
	 * @param changeDate
	 * @param msg
	 *
	 * @throws RepositoryException
	 */
	public void setSubject(BareJID roomJID, String subject, String creatorNickname, Date changeDate)
			throws RepositoryException {
		if (changeDate != null) {
			try {
				repository.setData(mucConfig.getServiceName(), ROOMS_KEY + roomJID + "/subject",
								   SUBJECT_CREATOR_NICK_KEY, creatorNickname);
				repository.setData(mucConfig.getServiceName(), ROOMS_KEY + roomJID + "/subject", SUBJECT_KEY, subject);
				repository.setData(mucConfig.getServiceName(), ROOMS_KEY + roomJID + "/subject", SUBJECT_DATE_KEY,
								   String.valueOf(changeDate.getTime()));
				// TODO Auto-generated method stub
			} catch (Exception e) {
				e.printStackTrace();
				throw new RepositoryException("Subject saving error", e);
			}
		}
	}

	/**
	 * @param roomJID
	 *
	 * @throws RepositoryException
	 */
	public void updateLastAccessDate(BareJID roomJID) throws RepositoryException {
		try {
			repository.setData(mucConfig.getServiceName(), ROOMS_KEY + roomJID, LAST_ACCESS_DATE_KEY,
							   String.valueOf((new Date()).getTime()));
		} catch (Exception e) {
			e.printStackTrace();
			throw new RepositoryException("Last access date writing error", e);
		}
	}

	/**
	 * @param roomConfig
	 *
	 * @throws RepositoryException
	 */
	public void updateRoomConfig(RoomConfig roomConfig) throws RepositoryException {
		try {
			String roomJID = roomConfig.getRoomJID() != null ? roomConfig.getRoomJID().toString() : null;
			if (roomJID == null) {
				roomJID = MUCComponent.DEFAULT_ROOM_CONFIG_KEY;
			}
			roomConfig.write(repository, mucConfig, ROOMS_KEY + roomJID + "/config");
		} catch (Exception e) {
			e.printStackTrace();
			throw new RepositoryException("Room config writing error", e);
		}
	}

}
