/*
 * RoomWithId.java
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
package tigase.muc;

import tigase.xmpp.jid.BareJID;

import java.util.Date;

/**
 * Created by andrzej on 14.10.2016.
 */
public class RoomWithId<ID> extends Room {

	private ID id;

	protected RoomWithId(ID id, RoomConfig rc, Date creationDate, BareJID creatorJid) {
		super(rc, creationDate, creatorJid);
		this.id = id;
	}

	public ID getId() {
		return id;
	}

	public void setId(ID id) {
		if (this.id != null) {
			throw new IllegalStateException("Room already has ID assigned!");
		}
		this.id = id;
	}

}
