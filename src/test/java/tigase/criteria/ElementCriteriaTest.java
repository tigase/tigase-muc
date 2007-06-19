/*  tigase-muc
 *  Copyright (C) 2007 by Bartosz M. Małkowski
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software Foundation,
 *  Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 *  $Id$
 */
package tigase.criteria;

import static org.junit.Assert.*;

import org.junit.Before;
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
     * @throws java.lang.Exception
     */
    @Before
    public void setUp() throws Exception {
    }

    /**
     * Test method for
     * {@link tigase.criteria.ElementCriteria#match(tigase.xml.Element)}.
     */
    @Test
    public void testMatch() {
        Element e = new Element("x", new String[] { "xmpns", "type" }, new String[] { "jabber:test", "chat" });

        ElementCriteria crit1 = new ElementCriteria("x", new String[] {}, new String[] {});

        assertTrue(crit1.match(e));
    }

}
