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
package tigase.muc.repository;

import tigase.component.exceptions.RepositoryException;
import tigase.db.DataSource;
import tigase.db.DataSourceAware;
import tigase.muc.RoomAffiliation;
import tigase.muc.RoomConfig;
import tigase.muc.RoomWithId;
import tigase.xmpp.jid.BareJID;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Created by andrzej on 14.10.2016.
 */
public interface IMucDAO<DS extends DataSource, ID>
		extends DataSourceAware<DS> {

	ID createRoom(RoomWithId<ID> room) throws RepositoryException;

	void destroyRoom(BareJID roomJID) throws RepositoryException;

	Map<BareJID, RoomAffiliation> getAffiliations(RoomWithId<ID> room) throws RepositoryException;

	RoomWithId<ID> getRoom(BareJID roomJID) throws RepositoryException;

	List<BareJID> getRoomsJIDList() throws RepositoryException;

	void setAffiliation(RoomWithId<ID> room, BareJID jid, RoomAffiliation affiliation) throws RepositoryException;

	String getRoomAvatar(RoomWithId<ID> room) throws RepositoryException;

	void updateRoomAvatar(RoomWithId<ID> room, String encodedAvatar, String hash) throws RepositoryException;

	void setSubject(RoomWithId<ID> room, String subject, String creatorNickname, Date changeDate)
			throws RepositoryException;

	void updateRoomConfig(RoomConfig roomConfig) throws RepositoryException;
}
