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
import tigase.muc.Room
import tigase.muc.modules.RoomConfigurationModule
import tigase.muc.repository.IMucRepository
import tigase.server.Command
import tigase.server.Iq
import tigase.server.Packet
import tigase.util.stringprep.TigaseStringprepException
import tigase.xmpp.jid.BareJID

import java.util.logging.Level
import java.util.logging.Logger

// AS:Description: Mass room remove
// AS:CommandId: mass-room-remove
// AS:Component: muc
// AS:ComponentClass: tigase.muc.MUCComponent

Kernel kernel = (Kernel) kernel;
MUCComponent component = (MUCComponent) component
packet = (Iq) packet

@CompileStatic
Packet process(Kernel kernel, MUCComponent component, Iq p, Set admins) {
	String[] roomNames = Command.getFieldValues(p, "room-name")
	String reason = Command.getFieldValue(p, "reason")
	String alternateJid = Command.getFieldValue(p, "alternate-jid")
	Packet res = p.commandResult(Command.DataType.form)

	final IMucRepository mucRepository = kernel.getInstance(IMucRepository.class);
	final RoomConfigurationModule ownerModule = kernel.getInstance(RoomConfigurationModule.class);

	Logger log = Logger.getLogger("tigase.admin");

	try {
		if (roomNames == null) {
			Command.addFieldMultiValue(res, "room-name", [ ], "Names of the rooms to delete")
			Command.addFieldValue(res, "reason", "", "text-single", "Reason")
		}

		if (roomNames != null) {
			log.log(Level.FINEST, "Removing ${roomNames.length} rooms")
			List<String> results = new ArrayList<>(roomNames.length);
			for (String roomName : roomNames) {
				log.log(Level.FINEST, "Processing room: ${roomName}")
				BareJID jid = null;
				try {
					if (roomName.contains("@")) {
						jid = BareJID.bareJIDInstance(roomName);
					} else {
						jid = BareJID.bareJIDInstance(roomName + "@" + p.getStanzaTo().getBareJID().getDomain());
					}
				} catch (TigaseStringprepException e) {
					results.add(roomName + ": failed with exception: " + e.getMessage())
				}
				if (jid != null) {
					log.log(Level.FINEST, "Getting room for JID: ${jid}");
					Room room = mucRepository.getRoom(jid)
					if (room == null) {
						results.add(jid.toString() + ": doesn't exists")
					} else {
						ownerModule.destroy(room, alternateJid, reason);
						log.log(Level.FINEST, "Room removed: ${room.getRoomJID().toString()}");
						results.add(room.getRoomJID().toString() + ": removed")
					}
				}
			}
			Command.addTitle(res, "Room processing result")
			Command.addFieldMultiValue(res, "Result", results, "Result")
		}
	} catch (Exception e) {
		log.log(Level.WARNING, "Error while mass-removing MUC rooms",e);
		Command.addNote(res, "Room removal error" + e.getMessage())
	}
	return res;
}

return process(kernel, component, packet, (Set) adminsSet);