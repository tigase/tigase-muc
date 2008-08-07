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

import tigase.criteria.Criteria;
import tigase.criteria.ElementCriteria;
import tigase.criteria.Or;
import tigase.muc.Affiliation;
import tigase.muc.MucInternalException;
import tigase.muc.RoomContext;
import tigase.muc.xmpp.JID;
import tigase.muc.xmpp.stanzas.Message;
import tigase.xml.Element;
import tigase.xmpp.Authorization;

/**
 * 
 * <p>
 * Created: 2007-06-20 09:23:51
 * </p>
 * 
 * @author bmalkow
 * @version $Rev$
 */
public class InvitationModule extends AbstractModule {

	private static final Criteria CRIT_DECLINE = ElementCriteria.name("message").add(
			ElementCriteria.name("x", "http://jabber.org/protocol/muc#user")).add(ElementCriteria.name("decline"));

	private static final Criteria CRIT_INVITE = ElementCriteria.name("message").add(
			ElementCriteria.name("x", "http://jabber.org/protocol/muc#user")).add(ElementCriteria.name("invite"));

	@Override
	public Criteria getModuleCriteria() {
		return new Or(CRIT_INVITE, CRIT_DECLINE);
	}

	@Override
	protected List<Element> intProcess(RoomContext roomContext, Element e) throws MucInternalException {
		Message element = new Message(e);
		List<Element> result = new LinkedList<Element>();
		String senderNick = roomContext.getOccupantsByJID().get(element.getFrom());
		String recipentNick = element.getTo().getResource();

		if (recipentNick != null) {
			throw new MucInternalException(element, Authorization.JID_MALFORMED);
		}

		Element x = element.getChild("x", "http://jabber.org/protocol/muc#user");
		Element decline = x.getChild("decline");
		Element invite = x.getChild("invite");

		if (decline != null) {
			Element reason = decline.getChild("reason");
			Message msg = new Message(JID.fromString(decline.getAttribute("to")), null);
			msg.setFrom(JID.fromString(roomContext.getId()));
			Element msgX = new Element("x", new String[] { "xmlns" }, new String[] { "http://jabber.org/protocol/muc#user" });
			msg.addChild(msgX);
			Element dec = new Element("decline");
			msgX.addChild(dec);
			dec.setAttribute("from", element.getFrom().toString());
			if (reason != null) {
				dec.addChild(reason.clone());
			}
			result.add(msg);
			return result;
		}

		if (senderNick == null) {
			throw new MucInternalException(element, Authorization.NOT_ACCEPTABLE);
		}

		if (invite != null && !roomContext.isRoomconfigAllowInvites()
				&& roomContext.getAffiliation(element.getFrom()).getWeight() < Affiliation.ADMIN.getWeight()) {
			throw new MucInternalException(element, Authorization.FORBIDDEN);
		} else if (invite != null) {
			Message invitingMessage = new Message(JID.fromString(invite.getAttribute("to")), "You have been invited to "
					+ roomContext.getId() + " by " + element.getFrom().toString() + ".");
			invitingMessage.setFrom(JID.fromString(roomContext.getId()));
			Element ix = new Element("x", new String[] { "xmlns" }, new String[] { "http://jabber.org/protocol/muc#user" });
			invitingMessage.addChild(ix);
			Element ii = new Element("invite", new String[] { "from" }, new String[] { element.getFrom().toString() });
			ix.addChild(ii);
			if (invite.getChild("reason") != null) {
				ii.addChild(invite.getChild("reason"));
			}
			if (roomContext.isRoomconfigPasswordProtectedRoom()) {
				ix.addChild(new Element("password", roomContext.getRoomconfigRoomSecret()));
			}

			// TODO modifing members list in members-only room

			result.add(invitingMessage);
		}

		return result;
	}

}
