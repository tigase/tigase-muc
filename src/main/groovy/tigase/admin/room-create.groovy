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
package tigase.admin

import groovy.transform.CompileStatic
import tigase.form.Form
import tigase.kernel.core.Kernel
import tigase.muc.MUCComponent
import tigase.muc.PermissionChecker
import tigase.muc.Room
import tigase.muc.RoomAffiliation
import tigase.muc.RoomConfig
import tigase.muc.repository.IMucRepository
import tigase.server.Command
import tigase.server.Iq
import tigase.server.Packet
import tigase.server.script.CommandIfc
import tigase.util.stringprep.TigaseStringprepException
import tigase.xml.Element
import tigase.xmpp.jid.BareJID

// AS:Description: Create room
// AS:CommandId: room-create
// AS:Component: muc
// AS:ComponentClass: tigase.muc.MUCComponent

Kernel kernel = (Kernel) kernel;
MUCComponent component = (MUCComponent) component
packet = (Iq) packet

@CompileStatic
Packet process(Kernel kernel, MUCComponent component, Iq p, Set admins) {
	String roomName = Command.getFieldValue(p, "room-name");

	final IMucRepository mucRepository = kernel.getInstance(IMucRepository.class)
	final PermissionChecker permissionChecker = kernel.getInstance(PermissionChecker.class)

	if (roomName == null) {
		RoomConfig roomConfig = mucRepository.getDefaultRoomConfig()

		// No data to process, let's ask user to provide
		// a list of words
		Packet res = p.commandResult(Command.DataType.form)
		Command.addFieldValue(res, "room-name", "", "text-single", "Room name")

		Element command = res.getElement().getChild("command")
		Element x = command.getChild("x", "jabber:x:data")

		x.addChildren(roomConfig.getConfigForm().getElement().getChildren())

		return res
	} else {
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
		room.addAffiliationByJid(p.getStanzaFrom().getBareJID(), RoomAffiliation.owner)
		room.getConfig().copyFrom(f)
		room.setRoomLocked(false)
		room.getConfig().notifyConfigUpdate(true)

		Packet result = p.commandResult(Command.DataType.result);

		Command.addFieldMultiValue(result, CommandIfc.SCRIPT_RESULT, Arrays.asList("Room " + room.getRoomJID() + " created"));
		return result;
	}
}

return process(kernel, component, packet, (Set) adminsSet);