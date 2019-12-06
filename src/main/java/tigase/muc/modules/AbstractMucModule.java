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

import tigase.component.modules.AbstractModule;
import tigase.muc.Room;
import tigase.server.Iq;
import tigase.server.Message;
import tigase.server.Packet;
import tigase.util.stringprep.TigaseStringprepException;
import tigase.xml.Element;
import tigase.xmpp.StanzaType;
import tigase.xmpp.jid.JID;

import java.util.Collection;

/**
 * @author bmalkow
 */
public abstract class AbstractMucModule
		extends AbstractModule {

	public static Element createResultIQ(Element iq) {
		return new Element(Iq.ELEM_NAME, new String[]{Packet.TYPE_ATT, Packet.FROM_ATT, Packet.TO_ATT, Packet.ID_ATT},
						   new String[]{"result", iq.getAttributeStaticStr(Packet.TO_ATT),
										iq.getAttributeStaticStr(Packet.FROM_ATT),
										iq.getAttributeStaticStr(Packet.ID_ATT)});
	}

	public static String getNicknameFromJid(JID jid) {
		if (jid != null) {
			return jid.getResource();
		} else {
			return null;
		}
	}

	public AbstractMucModule() {
	}

	protected void sendMucMessage(Room room, String recipientNickame, String message) throws TigaseStringprepException {
		Collection<JID> occupantJids = room.getOccupantsJidsByNickname(recipientNickame);

		for (JID jid : occupantJids) {
			Packet msg = Message.getMessage(JID.jidInstance(room.getRoomJID()), jid, StanzaType.groupchat, message,
											null, null, null);
			msg.setXMLNS(Packet.CLIENT_XMLNS);
			writer.write(msg);
		}
	}

}