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
package tigase.muc.modules.owner;

import java.util.LinkedList;
import java.util.List;

import tigase.criteria.Criteria;
import tigase.criteria.ElementCriteria;
import tigase.muc.Affiliation;
import tigase.muc.MucInternalException;
import tigase.muc.RoomContext;
import tigase.muc.modules.AbstractModule;
import tigase.muc.xmpp.stanzas.IQ;
import tigase.xml.Element;
import tigase.xmpp.Authorization;

public class OwnerGetModule extends AbstractModule {

	private static final Criteria CRIT = ElementCriteria.name("iq", new String[] { "type" }, new String[] { "get" }).add(
			ElementCriteria.name("query", "http://jabber.org/protocol/muc#owner"));

	private static final String XMLNS_MUC_OWNER = "http://jabber.org/protocol/muc#owner";

	@Override
	public Criteria getModuleCriteria() {
		return CRIT;
	}

	@Override
	protected List<Element> intProcess(RoomContext roomContext, Element element) throws MucInternalException {
		IQ iq = new IQ(element);
		if (Affiliation.OWNER != roomContext.getAffiliation(iq.getFrom())) {
			throw new MucInternalException(iq, Authorization.FORBIDDEN);
		}
		List<Element> result = new LinkedList<Element>();
		Element query = iq.getChild("query");

		if (query.getChildren() == null || query.getChildren().size() == 0) {
			Element answer = new Element("iq");
			answer.addAttribute("id", iq.getAttribute("id"));
			answer.addAttribute("type", "result");
			answer.addAttribute("to", iq.getAttribute("from"));
			answer.addAttribute("from", roomContext.getId());

			Element answerQuery = new Element("query", new String[] { "xmlns" },
					new String[] { "http://jabber.org/protocol/muc#owner" });
			answer.addChild(answerQuery);

			answerQuery.addChild(roomContext.getFormElement().getElement());

			result.add(answer);
		}

		return result;
	}

}
