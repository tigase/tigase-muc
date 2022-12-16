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
import tigase.kernel.core.Kernel
import tigase.muc.MUCComponent
import tigase.muc.modules.RoomConfigurationModule
import tigase.muc.repository.IMucRepository
import tigase.server.Command
import tigase.server.Iq
import tigase.server.Packet
import tigase.server.script.CommandIfc
import tigase.util.stringprep.TigaseStringprepException
import tigase.xmpp.jid.BareJID

// AS:Description: Remove room
// AS:CommandId: room-remove
// AS:Component: muc
// AS:ComponentClass: tigase.muc.MUCComponent

Kernel kernel = (Kernel) kernel;
MUCComponent component = (MUCComponent) component
packet = (Iq) packet

@CompileStatic
Packet process(Kernel kernel, MUCComponent component, Iq p, Set admins) {
	String roomName = Command.getFieldValue(p, "room-name");
	String reason = Command.getFieldValue(p, "reason")
	String alternateJid = Command.getFieldValue(p, "alternate-jid");

	final IMucRepository mucRepository = kernel.getInstance(IMucRepository.class);
	final RoomConfigurationModule ownerModule = kernel.getInstance(RoomConfigurationModule.class);

	if (roomName == null) {
		// No data to process, let's ask user to provide
		// a list of words
		Packet res = p.commandResult(Command.DataType.form)
		Command.addFieldValue(res, "room-name", "", "text-single", "Room name")
		Command.addFieldValue(res, "reason", "", "text-single", "Reason")
		Command.addFieldValue(res, "alternate-jid", "jid-single", "Alternate room")
		return res
	} else {
		BareJID jid;
		try {
			jid = BareJID.bareJIDInstance(roomName + "@" + p.getStanzaTo().getBareJID().getDomain());
		} catch (TigaseStringprepException e) {
			jid = BareJID.bareJIDInstance(roomName);
		}

		def room = mucRepository.getRoom(jid)
		if (room == null) {
			Packet result = p.commandResult(Command.DataType.result);
			Command.addFieldMultiValue(result, CommandIfc.SCRIPT_RESULT, Arrays.asList("Room " + jid + " doesn't exists"));
			return result;
		}
		ownerModule.destroy(room, alternateJid, reason);

		Packet result = p.commandResult(Command.DataType.result);
		Command.addFieldMultiValue(result, CommandIfc.SCRIPT_RESULT, Arrays.asList("Room " + room.getRoomJID() + " removed"));
		return result;
	}
}

return process(kernel, component, packet, (Set) adminsSet);