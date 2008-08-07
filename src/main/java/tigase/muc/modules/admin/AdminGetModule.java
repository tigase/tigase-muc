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
package tigase.muc.modules.admin;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import tigase.criteria.Criteria;
import tigase.criteria.ElementCriteria;
import tigase.muc.Affiliation;
import tigase.muc.MucInternalException;
import tigase.muc.Role;
import tigase.muc.RoomContext;
import tigase.muc.modules.AbstractModule;
import tigase.muc.modules.PresenceModule;
import tigase.muc.xmpp.JID;
import tigase.muc.xmpp.stanzas.IQ;
import tigase.muc.xmpp.stanzas.IQType;
import tigase.xml.Element;

public class AdminGetModule extends AbstractModule {

	private static final Criteria CRIT = ElementCriteria.name("iq", new String[] { "type" }, new String[] { "get" }).add(
			ElementCriteria.name("query", "http://jabber.org/protocol/muc#admin"));

	private static final String XMLNS_MUC_ADMIN = "http://jabber.org/protocol/muc#admin";

	@Override
	public Criteria getModuleCriteria() {
		return CRIT;
	}

	@Override
	protected List<Element> intProcess(RoomContext roomContext, Element element) throws MucInternalException {
		IQ iq = new IQ(element);

		List<Element> result = new LinkedList<Element>();
		Element query = iq.getChild("query", XMLNS_MUC_ADMIN);
		List<Element> items = query.getChildren("/query");

		// Service Informs Admin or Owner of Success
		IQ answer = new IQ(IQType.RESULT);
		answer.setId(iq.getId());
		answer.setTo(iq.getFrom());
		answer.setFrom(JID.fromString(roomContext.getId()));

		Element answerQuery = new Element("query");
		answer.addChild(answerQuery);
		answerQuery.setAttribute("query", XMLNS_MUC_ADMIN);

		Set<JID> occupantsJid = new HashSet<JID>();
		for (Element item : items) {

			try {
				Affiliation reqAffiliation = Affiliation.valueOf(item.getAttribute("affiliation").toUpperCase());
				occupantsJid.addAll(roomContext.findBareJidsByAffiliations(reqAffiliation));
				if (reqAffiliation == Affiliation.NONE) {
					occupantsJid.addAll(roomContext.findBareJidsWithoutAffiliations());
				}
			} catch (Exception e) {
				;
			}

			try {
				Role reqRole = Role.valueOf(item.getAttribute("role").toUpperCase());
				occupantsJid.addAll(roomContext.findJidsByRole(reqRole));
			} catch (Exception e) {
				;
			}

		}

		for (JID jid : occupantsJid) {
			Element answerItem = PresenceModule.preparePresenceSubItem(roomContext, jid, iq.getFrom());
			answerQuery.addChild(answerItem);
		}

		result.add(answer);
		return result;
	}

}