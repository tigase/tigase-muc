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
 * If not, see http://www.gnu.org/licenses/.*/
package tigase.admin

import tigase.form.Form

// AS:Description: Create room
// AS:CommandId: room-create
// AS:Component: muc
// AS:ComponentClass: tigase.muc.MUCComponent

import tigase.muc.Affiliation
import tigase.muc.PermissionChecker
import tigase.muc.Room
import tigase.muc.RoomConfig
import tigase.muc.repository.IMucRepository
import tigase.server.Command
import tigase.server.Iq
import tigase.server.Packet
import tigase.util.stringprep.TigaseStringprepException
import tigase.xml.Element
import tigase.xmpp.jid.BareJID

def ROOM_NAME_KEY = "room-name"

Iq p = (Iq) packet
def roomName = Command.getFieldValue(p, ROOM_NAME_KEY)

final IMucRepository mucRepository = kernel.getInstance(IMucRepository.class)
final PermissionChecker permissionChecker = kernel.getInstance(PermissionChecker.class)

if (roomName == null) {
	RoomConfig roomConfig = mucRepository.getDefaultRoomConfig()

	// No data to process, let's ask user to provide
	// a list of words
	Packet res = p.commandResult(Command.DataType.form)
	Command.addFieldValue(res, ROOM_NAME_KEY, "", "text-single", "Room name")

	Element command = res.getElement().getChild("command")
	Element x = command.getChild("x", "jabber:x:data")

	x.addChildren(roomConfig.getConfigForm().getElement().getChildren())

	return res
}

if (roomName != null) {
	BareJID jid
	try {
		jid = BareJID.bareJIDInstance(roomName + "@" + p.getStanzaTo().getBareJID().getDomain())
	} catch (TigaseStringprepException e) {
		jid = BareJID.bareJIDInstance(roomName)
	}

	Element command = p.getElement().getChild("command")
	Element x = command.getChild("x", "jabber:x:data")
	Form f = new Form(x)

	permissionChecker.checkCreatePermission(jid, p.getStanzaFrom(), f)

	Room room = mucRepository.createNewRoom(jid, p.getStanzaFrom())
	room.addAffiliationByJid(p.getStanzaFrom().getBareJID(), Affiliation.owner)
	room.getConfig().copyFrom(f)
	room.setRoomLocked(false)
	room.getConfig().notifyConfigUpdate(true)

	return "Room " + room.getRoomJID() + " created"
}