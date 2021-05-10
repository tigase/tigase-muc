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
/*
 Sets default room configuration.
 AS:Description: Default room config
 AS:CommandId: default-room-config
 AS:Component: muc
 AS:ComponentClass: tigase.muc.MUCComponent
*/

package tigase.admin

import groovy.transform.CompileStatic
import tigase.form.Form
import tigase.kernel.core.Kernel
import tigase.muc.MUCComponent
import tigase.muc.RoomConfig
import tigase.muc.repository.IMucRepository
import tigase.server.Command
import tigase.server.Iq
import tigase.server.Packet
import tigase.server.script.CommandIfc
import tigase.xml.Element
import tigase.xmpp.jid.BareJID

Kernel kernel = (Kernel) kernel;
MUCComponent component = (MUCComponent) component
packet = (Iq) packet

@CompileStatic
Packet process(Kernel kernel, MUCComponent component, Iq p, Set admins) {
	final IMucRepository mucRepository = kernel.getInstance(IMucRepository.class)

	BareJID stanzaFromBare = p.getStanzaFrom().getBareJID()
	boolean isServiceAdmin = admins.contains(stanzaFromBare)
	if (!isServiceAdmin) {
		def result = p.commandResult(Command.DataType.result)
		Command.addTextField(result, "Error", "You do not have enough permissions to access this data.");
		return result
	}

	Element xform = Command.getData(p, "x", "jabber:x:data");
	boolean submit = xform == null ? false : xform.getAttributeStaticStr("type") == "submit";

	if (submit) {
		Element command = p.getElement().getChild("command", "http://jabber.org/protocol/commands");
		Element x = command.getChild("x", "jabber:x:data");

		Form frm = new Form(x);
		RoomConfig roomConfig = mucRepository.getDefaultRoomConfig();

		roomConfig.copyFrom(frm);

		mucRepository.updateDefaultRoomConfig(roomConfig)

		Packet result = p.commandResult(Command.DataType.result);
		Command.addFieldMultiValue(result, CommandIfc.SCRIPT_RESULT, Arrays.asList("Default configurtation updated"));
		return result;
	} else {
		Packet res = p.commandResult(Command.DataType.form)
		Element command = res.getElement().getChild("command", "http://jabber.org/protocol/commands");
		Element x = command.getChild("x", "jabber:x:data");
		command.removeChild(x);

		Form df = mucRepository.getDefaultRoomConfig().getConfigForm();

		def frm = new Form(x);

		for (fld in df.getAllFields()) {
			frm.addField(fld);
		}

		frm.copyValuesFrom(df);

		command.addChild(frm.getElement());

		return res;
	}
}

return process(kernel, component, packet, (Set) adminsSet);