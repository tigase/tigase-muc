/*
 * Tigase Jabber/XMPP Multi-User Chat Component
 * Copyright (C) 2008 "Bartosz M. Małkowski" <bartosz.malkowski@tigase.org>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. Look for COPYING file in the top folder.
 * If not, see http://www.gnu.org/licenses/.
 *
 * $Rev$
 * Last modified by $Author$
 * $Date$
 */
package tigase.muc.modules;

import tigase.component.ElementWriter;
import tigase.component.modules.Module;
import tigase.criteria.Criteria;
import tigase.criteria.ElementCriteria;
import tigase.criteria.Or;
import tigase.muc.exceptions.MUCException;
import tigase.server.Packet;
import tigase.xml.Element;

/**
 * @author bmalkow
 * 
 */
public class XmppPingModule implements Module {

	private static final Criteria CRIT = ElementCriteria.nameType("iq", "get").add(
			new Or(ElementCriteria.name("query", "jabber:iq:ping"), ElementCriteria.name("ping",
					"http://www.xmpp.org/extensions/xep-0199.html#ns"), ElementCriteria.name("ping", "urn:xmpp:ping")));

	protected final ElementWriter writer;

	public XmppPingModule(final ElementWriter writer) {
		this.writer = writer;
	}

	@Override
	public String[] getFeatures() {
		return new String[] { "urn:xmpp:ping" };
	}

	@Override
	public Criteria getModuleCriteria() {
		return CRIT;
	}

	@Override
	public void process(Packet iq) throws MUCException {
		Packet response = iq.okResult((Element) null, 0);

		writer.write(response);
	}

}
