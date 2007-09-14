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
package tigase.criteria;

import tigase.xml.Element;

/**
 * 
 * <p>
 * Created: 2007-06-20 09:32:29
 * </p>
 * 
 * @author bmalkow
 * @version $Rev$
 */
public class Or implements Criteria {

	@Override
	public Criteria add(Criteria criteria) {
		throw new RuntimeException("Or.add() is not implemented!");
	}

	private Criteria[] crits;

	public Or(Criteria... criteria) {
		this.crits = criteria;
	}

	@Override
	public boolean match(Element element) {
		for (Criteria c : this.crits) {
			if (c.match(element)) {
				return true;
			}
		}
		return false;
	}

}