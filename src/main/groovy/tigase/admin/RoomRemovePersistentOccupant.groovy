/**
 * Tigase MUC - Multi User Chat component for Tigase
 * Copyright (C) 2019 Tigase, Inc. (office@tigase.com)
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
import tigase.eventbus.EventBus
import tigase.kernel.core.Kernel
import tigase.muc.MUCComponent
import tigase.muc.Room
import tigase.muc.RoomAffiliation
import tigase.muc.repository.IMucRepository
import tigase.server.Command
import tigase.server.Iq
import tigase.server.Packet
import tigase.xmpp.jid.BareJID

// AS:Description: Remove persistent room occupant
// AS:CommandId: room-occupant-persistent-remove
// AS:Component: muc
// AS:ComponentClass: tigase.muc.MUCComponent

Kernel kernel = (Kernel) kernel;
MUCComponent component = (MUCComponent) component
packet = (Iq) packet
eventBus = (EventBus) eventBus

@CompileStatic
Packet process(Kernel kernel, MUCComponent component, Iq p, Set admins) {

	boolean isServiceAdmin = admins.contains(p.getStanzaFrom().getBareJID());

	if (isServiceAdmin || component.isAdmin(p.getStanzaFrom())) {
		String name = Command.getFieldValue(p, "room_name");
		String occupantStr = Command.getFieldValue(p, "occupant_jid");

		if (name == null || occupantStr == null) {
			Iq result = (Iq) p.commandResult(Command.DataType.form);

			Command.addTitle(result, "Remove persistent occupant from the room")
			Command.addInstructions(result, "Fill out this form to remove persistent occupant from the room.")

			Command.addFieldValue(result, "room_name", name ?: "", "text-single",
								  "Name of the room")

			Command.addFieldValue(result, "occupant_jid", occupantStr ?: "", "jid-single", "Occupant bare JID")

			return result
		}


		Iq result = (Iq) p.commandResult(Command.DataType.result)
		try {
			IMucRepository mucRepository = kernel.getInstance(IMucRepository.class);

			Room room = mucRepository.getRoom(BareJID.bareJIDInstanceNS(name, p.getStanzaTo().getDomain()));
			if (room == null) {
				Command.addTextField(result, "Error",
									 "There is no room named '" + name + "' for domain " + p.getStanzaTo().getDomain());
				return result;
			}

			BareJID occupant = BareJID.bareJIDInstance(occupantStr);
			room.addAffiliationByJid(occupant, RoomAffiliation.none);

			Command.addTextField(result, "Note", "Operation successful");
		} catch (Exception ex) {
			Command.addTextField(result, "Error", ex.getMessage())
		}
		return result;
	} else {
		Iq result = (Iq) p.commandResult(Command.DataType.result)
		Command.addTextField(result, "Error", "You do not have enough permissions to remote user from the room as the persistent occupant.");
		return result;
	}
}

return process(kernel, component, packet, (Set) adminsSet);