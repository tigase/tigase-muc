/*
 * IMucDAO.java
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
package tigase.muc.repository;

import tigase.component.exceptions.RepositoryException;
import tigase.db.DataSource;
import tigase.db.DataSourceAware;
import tigase.muc.Affiliation;
import tigase.muc.RoomConfig;
import tigase.muc.RoomWithId;
import tigase.xmpp.BareJID;

import java.util.ArrayList;
import java.util.Date;
import java.util.Map;

/**
 * Created by andrzej on 14.10.2016.
 */
public interface IMucDAO<DS extends DataSource, ID> extends DataSourceAware<DS> {

	ID createRoom(RoomWithId<ID> room) throws RepositoryException;

	void destroyRoom(BareJID roomJID) throws RepositoryException;

	Map<BareJID, Affiliation> getAffiliations(RoomWithId<ID> room) throws RepositoryException;

	RoomWithId<ID> getRoom(BareJID roomJID) throws RepositoryException;

	ArrayList<BareJID> getRoomsJIDList() throws RepositoryException;

	void setAffiliation(RoomWithId<ID> room, BareJID jid, Affiliation affiliation) throws RepositoryException;

	void setSubject(RoomWithId<ID> room, String subject, String creatorNickname, Date changeDate) throws RepositoryException;

	void updateRoomConfig(RoomConfig roomConfig) throws RepositoryException;
}
