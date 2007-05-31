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
package tigase.muc;

import tigase.xml.Element;

/**
 * 
 * <p>
 * Created: 2007-05-25 11:55:48
 * </p>
 * 
 * @author bmalkow
 * @version $Rev$
 */
public class MucInternalException extends Exception {

    private static final long serialVersionUID = 1L;

    private String code;

    private Element item;

    private String name;

    private String type;

    private String xmlns = "urn:ietf:params:xml:ns:xmpp-stanzas";

    /**
     * @param item
     * @param string
     * @param string2
     * @param string3
     */
    public MucInternalException(Element item, String name, String code, String type) {
        this.item = item;
        this.name = name;
        this.code = code;
        this.type = type;
    }

    /**
     * @return Returns the code.
     */
    public String getCode() {
        return code;
    }

    /**
     * @return Returns the item.
     */
    public Element getItem() {
        return item;
    }

    /**
     * @return Returns the name.
     */
    public String getName() {
        return name;
    }

    /**
     * @return Returns the type.
     */
    public String getType() {
        return type;
    }

    /**
     * @return
     */
    public Element makeErrorElement() {
        Element error = new Element("error");
        error.setAttribute("code", code);
        error.setAttribute("type", type);
        error.addChild(new Element(name, new String[] { "xmlns" }, new String[] { xmlns }));
        return error;
    }

}
