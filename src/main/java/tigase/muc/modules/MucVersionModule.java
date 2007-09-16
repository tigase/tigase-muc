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

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import tigase.criteria.Criteria;
import tigase.criteria.ElementCriteria;
import tigase.muc.MucVersion;
import tigase.muc.RoomsContainer;
import tigase.muc.xmpp.stanzas.IQ;
import tigase.muc.xmpp.stanzas.IQType;
import tigase.xml.Element;

public class MucVersionModule implements MUCModule {

	private final static String LETTERS_TO_UNIQUE_NAME = "abcdefghijklmnopqrstuvwxyz0123456789";

	private static Random random = new SecureRandom();

	@Override
	public List<Element> process(RoomsContainer roomsContainer, Element element) {
		IQ iq = new IQ(element);

		IQ result = new IQ(IQType.RESULT);
		result.setTo(iq.getFrom());
		result.setFrom(iq.getTo());
		result.setId(iq.getId());

		Element query = new Element("query", new String[] { "xmlns" }, new String[] { "jabber:iq:version" });
		query.addChild(new Element("name", MucVersion.getImplementationTitle()));
		query.addChild(new Element("version", MucVersion.getVersion()));
		result.addChild(query);
		
		List<Element> resultArray = new ArrayList<Element>();
		resultArray.add(result);
		return resultArray;
	}

	private static final Criteria CRIT = ElementCriteria.name("iq", new String[] { "type" }, new String[] { "get" }).add(
			ElementCriteria.name("query", "jabber:iq:version"));

	@Override
	public Criteria getModuleCriteria() {
		return CRIT;
	}

}
