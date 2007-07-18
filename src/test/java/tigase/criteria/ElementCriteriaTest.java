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
package tigase.criteria;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import tigase.xml.Element;

/**
 * 
 * <p>
 * Created: 2007-06-19 21:14:10
 * </p>
 * 
 * @author bmalkow
 * @version $Rev$
 */
public class ElementCriteriaTest {

	/**
	 * Test method for
	 * {@link tigase.criteria.ElementCriteria#match(tigase.xml.Element)}.
	 */
	@Test
	public void testMatch1() {
		Element e = new Element("x", new String[] { "xmlns", "type" }, new String[] { "jabber:test", "chat" });

		Criteria crit1 = new ElementCriteria("x", new String[] {}, new String[] {});
		Criteria crit2 = new ElementCriteria("z", new String[] {}, new String[] {});
		Criteria crit3 = new ElementCriteria("x", new String[] { "type" }, new String[] { "chat" });
		Criteria crit4 = new ElementCriteria("x", new String[] { "type" }, new String[] { "normal" });
		Criteria crit5 = new ElementCriteria("x", new String[] { "notexist" }, new String[] { "normal" });
		Criteria crit6 = new ElementCriteria("x", new String[] { "type", "xmlns" }, new String[] { "chat", "jabber:test" });
		Criteria crit7 = new ElementCriteria("x", new String[] { "type", "xmlns" }, new String[] { "chat", "jabber" });

		assertTrue(crit1.match(e));
		assertFalse(crit2.match(e));
		assertTrue(crit3.match(e));
		assertFalse(crit4.match(e));
		assertFalse(crit5.match(e));
		assertTrue(crit6.match(e));
		assertFalse(crit7.match(e));
	}

	@Test
	public void testMatch2() {
		Element e1 = new Element("x", new String[] { "xmlns", "type" }, new String[] { "jabber:test", "chat" });
		Element e2 = new Element("reason", new String[] { "to", "type" }, new String[] { "test@tester.com", "normal" });
		e1.addChild(e2);

		Criteria crit1 = new ElementCriteria("x", new String[] {}, new String[] {});
		Criteria crit3 = new ElementCriteria("x", new String[] { "type" }, new String[] { "chat" });

		Criteria crit4 = (new ElementCriteria("x", new String[] {}, new String[] {})).add(new ElementCriteria("reason", null, null));

		Criteria crit5 = ElementCriteria.xmlns("jabber:test").add(ElementCriteria.name("reason"));

		Criteria crit6 = ElementCriteria.xmlns("jabber:test:no").add(ElementCriteria.name("reason"));
		Criteria crit7 = ElementCriteria.empty().add(ElementCriteria.xmlns("dupa"));
		Criteria crit8 = ElementCriteria.empty().add(new ElementCriteria("reason", new String[] { "to" }, new String[] { "inny@tester.com" }));

		assertTrue(crit1.match(e1));
		assertTrue(crit3.match(e1));
		assertTrue(crit4.match(e1));
		assertTrue(crit5.match(e1));
		assertFalse(crit6.match(e1));
		assertFalse(crit7.match(e1));
		assertFalse(crit8.match(e1));

	}

}