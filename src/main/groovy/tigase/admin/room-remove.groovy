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
package tigase.admin

import tigase.kernel.core.Kernel
import tigase.muc.modules.RoomConfigurationModule
import tigase.muc.repository.IMucRepository

// AS:Description: Remove room
// AS:CommandId: room-remove
// AS:Component: muc
// AS:ComponentClass: tigase.muc.MUCComponent

import tigase.server.Command
import tigase.server.Iq
import tigase.server.Packet
import tigase.util.stringprep.TigaseStringprepException
import tigase.xmpp.jid.BareJID

def ROOM_NAME_KEY = "room-name"
def REASON_KEY = "reason"
def ALTERNATE_JID_KEY = "alternate-jid"

def Iq p = (Iq) packet
def roomName = Command.getFieldValue(p, ROOM_NAME_KEY)
def reason = Command.getFieldValue(p, REASON_KEY)
def alternateJid = Command.getFieldValue(p, ALTERNATE_JID_KEY)

final IMucRepository mucRepository = kernel.getInstance(IMucRepository.class);
final RoomConfigurationModule ownerModule = kernel.getInstance(RoomConfigurationModule.class);

if (roomName == null) {
	// No data to process, let's ask user to provide
	// a list of words
	def res = (Packet) p.commandResult(Command.DataType.form)
	Command.addFieldValue(res, ROOM_NAME_KEY, "", "text-single", "Room name")
	Command.addFieldValue(res, REASON_KEY, "", "text-single", "Reason")
	Command.addFieldValue(res, ALTERNATE_JID_KEY, "", "jid-single", "Alternate room")
	return res
}

if (roomName != null) {
	BareJID jid;
	try {
		jid = BareJID.bareJIDInstance(roomName + "@" + p.getStanzaTo().getBareJID().getDomain());
	} catch (TigaseStringprepException e) {
		jid = BareJID.bareJIDInstance(roomName);
	}

	def room = mucRepository.getRoom(jid)
	if (room == null) {
		return "Room " + jid + " doesn't exists"
	}
	ownerModule.destroy(room, alternateJid, reason);
	return "Room " + room.getRoomJID() + " removed";
}