/*
 * Tigase Jabber/XMPP Multi User Chatroom Component
 * Copyright (C) 2007 "Bartosz M. Małkowski" <bartosz.malkowski@tigase.org>
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

import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;

import tigase.muc.MucInternalException;
import tigase.muc.Role;
import tigase.muc.RoomContext;
import tigase.muc.xmpp.JID;
import tigase.muc.xmpp.stanzas.Message;
import tigase.xml.Element;

public abstract class AbstractMessageModule extends AbstractModule {

	protected List<Element> broadCastToAll(RoomContext roomContext, Message element, String senderNick) {
		List<Element> result = new LinkedList<Element>();
		for (Entry<String, JID> entry : roomContext.getOccupantsByNick().entrySet()) {
			Element message = element.clone();
			message.setAttribute("from", roomContext.getId() + (senderNick == null ? "" : "/" + senderNick));
			message.setAttribute("to", entry.getValue().toString());
			result.add(message);
		}
		return result;
	}

	@Override
	protected List<Element> intProcess(RoomContext roomContext, Element el) throws MucInternalException {
		Message element = new Message(el);
		List<Element> result = new LinkedList<Element>();
		String senderNick = roomContext.getOccupantsByJID().get(element.getFrom());
		String recipentNick = element.getTo().getResource();

		// broadcast message
		if (roomContext.getRole(JID.fromString(element.getAttribute("from"))) == Role.VISITOR) {
			return result;
		}
		preProcess(roomContext, element, senderNick);

		result.addAll(broadCastToAll(roomContext, element, senderNick));

		return result;
	}

	protected void preProcess(RoomContext roomContext, Message element, String senderNick) throws MucInternalException {
	}
}