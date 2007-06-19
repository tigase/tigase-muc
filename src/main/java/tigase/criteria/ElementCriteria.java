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

import java.util.Map;
import java.util.TreeMap;
import java.util.Map.Entry;

import tigase.xml.Element;

/**
 * 
 * <p>
 * Created: 2007-06-19 20:34:57
 * </p>
 * 
 * @author bmalkow
 * @version $Rev$
 */
public class ElementCriteria {

    private Map<String, String> attrs = new TreeMap<String, String>();

    private String name;

    public ElementCriteria(String name, String[] attname, String[] attValue) {
        this.name = name;
    }

    public boolean match(Element element) {
        if (name != null && !name.equals(element.getName())) {
            return false;
        }
        boolean result = true;
        for (Entry<String, String> entry : this.attrs.entrySet()) {
            String x = element.getAttribute(entry.getKey());
            if (x == null || !x.equals(entry.getValue())) {
                result = false;
                break;
            }
        }

        return result;
    }

}
