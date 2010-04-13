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

import java.util.List;

import tigase.criteria.Criteria;
import tigase.criteria.ElementCriteria;
import tigase.muc.Module;
import tigase.muc.MucVersion;
import tigase.muc.exceptions.MUCException;
import tigase.util.TigaseStringprepException;
import tigase.xml.Element;
import tigase.xmpp.JID;

/**
 * @author bmalkow
 * 
 */
public class SoftwareVersionModule implements Module {

	private static final Criteria CRIT = ElementCriteria.nameType("iq", "get").add(
			ElementCriteria.name("query", "jabber:iq:version"));

	@Override
	public String[] getFeatures() {
		return new String[] { "jabber:iq:version" };
	}

	@Override
	public Criteria getModuleCriteria() {
		return CRIT;
	}

	@Override
	public boolean isProcessedByModule(Element element) {
		try {
			JID jid = JID.jidInstance(element.getAttribute("to"));
			return jid != null && jid.getResource() == null;
		} catch (TigaseStringprepException e) {
			return false;
		}
	}

	@Override
	public List<Element> process(Element iq) throws MUCException {
		Element response = AbstractModule.createResultIQ(iq);

		Element query = new Element("query", new String[] { "xmlns" }, new String[] { "jabber:iq:version" });
		query.addChild(new Element("name", "Tigase Multi-User Chat Component"));
		query.addChild(new Element("version", MucVersion.getVersion()));
		query.addChild(new Element("os", System.getProperty("os.name") + "-" + System.getProperty("os.arch") + "-"
				+ System.getProperty("os.version") + ", " + System.getProperty("java.vm.name") + "-"
				+ System.getProperty("java.version") + " " + System.getProperty("java.vm.vendor")));

		response.addChild(query);
		return AbstractModule.makeArray(response);
	}

}
