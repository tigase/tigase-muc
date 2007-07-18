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
 * Last modified by $Author: $
 * $Date: $
 */
package tigase.muc.modules;

import tigase.criteria.Criteria;
import tigase.criteria.ElementCriteria;
import tigase.muc.MucInternalException;
import tigase.muc.Role;
import tigase.muc.RoomContext;
import tigase.muc.xmpp.stanzas.Message;
import tigase.xml.Element;

public class ChangeSubjectModule extends AbstractMessageModule {

	private static final Criteria CRIT = (new ElementCriteria("message", new String[] { "type" }, new String[] { "groupchat" })).add(ElementCriteria.name("subject"));

	@Override
	public Criteria getModuleCriteria() {
		return CRIT;
	}

	protected void preProcess(RoomContext roomContext, Message element, String senderNick) throws MucInternalException {
		Element subject = element.getChild("subject");

		if (!roomContext.isRoomconfigChangeSubject() && roomContext.getRole(element.getFrom()) != Role.MODERATOR) {
			throw new MucInternalException(element, "forbidden", "403", "auth");
		}
		roomContext.setCurrentSubject(new Message(element.clone()));
		roomContext.getCurrentSubject().setAttribute("from", roomContext.getId() + "/" + senderNick);
	}

}