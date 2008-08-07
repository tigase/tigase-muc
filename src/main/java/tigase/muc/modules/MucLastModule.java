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

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import tigase.criteria.Criteria;
import tigase.criteria.ElementCriteria;
import tigase.muc.RoomsContainer;
import tigase.muc.xmpp.stanzas.IQ;
import tigase.muc.xmpp.stanzas.IQType;
import tigase.xml.Element;

public class MucLastModule implements MUCModule {

	private static final Criteria CRIT = ElementCriteria.name("iq", new String[] { "type" }, new String[] { "get" }).add(
			ElementCriteria.name("query", "jabber:iq:last"));

	private Calendar calendar = Calendar.getInstance();

	@Override
	public Criteria getModuleCriteria() {
		return CRIT;
	}

	@Override
	public List<Element> process(RoomsContainer roomsContainer, Element element) {
		IQ iq = new IQ(element);

		IQ response = new IQ(IQType.RESULT);
		response.setTo(iq.getFrom());
		response.setFrom(iq.getTo());
		response.setId(iq.getId());

		Calendar now = Calendar.getInstance();

		long seconds = (now.getTimeInMillis() - calendar.getTimeInMillis()) / 1000l;

		Element query = new Element("query", new String[] { "xmlns" }, new String[] { "jabber:iq:last" });
		query.setAttribute("seconds", String.valueOf(seconds));
		response.addChild(query);

		List<Element> resultArray = new ArrayList<Element>();
		resultArray.add(response);
		return resultArray;
	}

}
