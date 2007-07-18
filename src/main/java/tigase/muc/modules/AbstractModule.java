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

import java.util.LinkedList;
import java.util.List;
import java.util.logging.Logger;

import tigase.muc.MucInternalException;
import tigase.muc.RoomContext;
import tigase.xml.Element;

public abstract class AbstractModule implements RoomModule {

	protected Logger log = Logger.getLogger(this.getClass().getName());

    protected abstract List<Element> intProcess(final RoomContext roomContext, final Element element) throws MucInternalException;

	@Override
	public final List<Element> process(final RoomContext roomContext, final Element element) {
		try {
			return intProcess(roomContext, element);
		} catch (MucInternalException e) {
			List<Element> result = new LinkedList<Element>();
			Element answer = e.makeElement(true);
			answer.setAttribute("from", roomContext.getId());
			return result;
		}

	}
}