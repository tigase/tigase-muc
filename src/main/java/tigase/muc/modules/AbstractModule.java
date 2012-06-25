/*
 * Tigase Jabber/XMPP Multi-User Chat Component
 * Copyright (C) 2008 "Bartosz M. Małkowski" <bartosz.malkowski@tigase.org>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. Look for COPYING file in the top folder.
 * If not, see http://www.gnu.org/licenses/.
 *
 * $Rev$
 * Last modified by $Author$
 * $Date$
 */
package tigase.muc.modules;

import java.util.Collection;
import java.util.logging.Logger;

import tigase.muc.ElementWriter;
import tigase.muc.Module;
import tigase.muc.MucConfig;
import tigase.muc.Room;
import tigase.muc.repository.IMucRepository;
import tigase.server.Packet;
import tigase.util.TigaseStringprepException;
import tigase.xml.Element;
import tigase.xmpp.JID;

/**
 * @author bmalkow
 * 
 */
public abstract class AbstractModule implements Module {

	public static Element createResultIQ(Element iq) {
		return new Element("iq", new String[] { "type", "from", "to", "id" }, new String[] { "result", iq.getAttribute("to"),
				iq.getAttribute("from"), iq.getAttribute("id") });
	}

	public static String getNicknameFromJid(JID jid) {
		if (jid != null) {
			return jid.getResource();
		} else
			return null;
	}

	protected final MucConfig config;

	protected Logger log = Logger.getLogger(this.getClass().getName());

	protected final IMucRepository repository;

	protected final ElementWriter writer;

	public AbstractModule(final MucConfig config, ElementWriter writer, final IMucRepository mucRepository) {
		this.config = config;
		this.repository = mucRepository;
		this.writer = writer;
	}

	@Override
	public boolean isProcessedByModule(Element element) {
		return true;
	}

	protected void sendMucMessage(Room room, String recipientNickame, String message) throws TigaseStringprepException {
		Collection<JID> occupantJids = room.getOccupantsJidsByNickname(recipientNickame);

		for (JID jid : occupantJids) {
			Element msg = new Element("message", new String[] { "from", "to", "type" }, new String[] {
					room.getRoomJID().toString(), jid.toString(), "groupchat" });

			msg.addChild(new Element("body", message));
			writer.write(Packet.packetInstance(msg));
		}

	}
}
