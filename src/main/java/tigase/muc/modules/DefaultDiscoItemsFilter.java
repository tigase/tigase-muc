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
package tigase.muc.modules;

import tigase.kernel.beans.Bean;
import tigase.muc.Affiliation;
import tigase.muc.Room;
import tigase.xmpp.jid.JID;

@Bean(name = "discoItemsFilter", active = true)
public class DefaultDiscoItemsFilter
		implements DiscoItemsFilter {

	@Override
	public boolean allowed(JID senderJID, Room room) {
		if (!room.getConfig().isRoomconfigPublicroom()) {
			Affiliation senderAff = room.getAffiliation(senderJID.getBareJID()).getAffiliation();
			if (!room.isOccupantInRoom(senderJID) &&
					(senderAff == Affiliation.none || senderAff == Affiliation.outcast)) {
				return false;
			}
		}

		return true;
	}

}
