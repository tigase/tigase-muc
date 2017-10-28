/*
 * MAMQueryModule.java
 *
 * Tigase Jabber/XMPP Multi-User Chat Component
 * Copyright (C) 2004-2017 "Tigase, Inc." <office@tigase.com>
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
package tigase.muc.modules;

import tigase.component.exceptions.ComponentException;
import tigase.component.exceptions.RepositoryException;
import tigase.kernel.beans.Bean;
import tigase.kernel.beans.Inject;
import tigase.muc.Room;
import tigase.muc.repository.IMucRepository;
import tigase.server.Packet;
import tigase.util.stringprep.TigaseStringprepException;
import tigase.xmpp.Authorization;
import tigase.xmpp.jid.JID;
import tigase.xmpp.mam.modules.QueryModule;

/**
 * Created by andrzej on 20.12.2016.
 */
@Bean(name = "mamQueryModule", active = true)
public class MAMQueryModule extends QueryModule {

	@Inject
	private IMucRepository repository;

	@Override
	public void process(Packet packet) throws ComponentException, TigaseStringprepException {
		JID toJid = packet.getStanzaTo();

		if (toJid == null || toJid.getLocalpart() == null) {
			throw new ComponentException(Authorization.FEATURE_NOT_IMPLEMENTED);
		}

		try {
			Room room = repository.getRoom(toJid.getBareJID());
			if (room == null) {
				throw new ComponentException(Authorization.ITEM_NOT_FOUND, "There is no such room.");
			}

			JID fromJid = packet.getStanzaFrom();

			if (!room.isOccupantInRoom(fromJid)) {
				throw new ComponentException(Authorization.FORBIDDEN, "You need to be a room occupant");
			}
		} catch (RepositoryException ex) {
			throw new RuntimeException("Exception loading room " + toJid.getBareJID() + " from repository", ex);
		}

		super.process(packet);
	}
}
