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

import java.util.List;
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
public class ElementCriteria implements Criteria {

	public static final ElementCriteria empty() {
		return new ElementCriteria(null, null, null);
	}

	public static final ElementCriteria name(String name) {
		return new ElementCriteria(name, null, null);
	}

	public static final ElementCriteria name(String name, String xmlns) {
		return new ElementCriteria(name, new String[] { "xmlns" }, new String[] { xmlns });
	}

	public static final ElementCriteria xmlns(String xmlns) {
		return new ElementCriteria(null, new String[] { "xmlns" }, new String[] { xmlns });
	}

	private Map<String, String> attrs = new TreeMap<String, String>();

	private String name;

	private Criteria nextCriteria;

	public ElementCriteria(String name, String[] attname, String[] attValue) {
		this.name = name;
		if (attname != null && attValue != null) {
			for (int i = 0; i < attname.length; i++) {
				attrs.put(attname[i], attValue[i]);
			}
		}
	}

	public Criteria add(Criteria criteria) {
		if (this.nextCriteria == null) {
			this.nextCriteria = criteria;
		} else {
			Criteria c = this.nextCriteria;
			c.add(criteria);
		}
		return this;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see tigase.criteria.Criteria#match(tigase.xml.Element)
	 */
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

		if (this.nextCriteria != null) {
			List<Element> children = element.getChildren();
			boolean subres = false;
			for (Element sub : children) {
				if (this.nextCriteria.match(sub)) {
					subres = true;
					break;
				}
			}
			result &= subres;
		}

		return result;
	}

}
