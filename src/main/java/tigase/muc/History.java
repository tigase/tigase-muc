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
package tigase.muc;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Locale;
import java.util.TimeZone;

import tigase.xml.Element;

/**
 * 
 * <p>
 * Created: 2007-05-31 09:54:34
 * </p>
 * 
 * @author bmalkow
 * @version $Rev$
 */
public class History {

    private static final long serialVersionUID = 1L;

    private int maxSize;

    private LinkedList<Element> stanzas = new LinkedList<Element>();

    public History(int maxSize) {
        this.maxSize = maxSize;
    }

    public Iterator<Element> iterator() {
        return this.stanzas.iterator();
    }

    public boolean add(final Element e, String from, String roomId) {
        Calendar now = Calendar.getInstance();
        now.setTimeZone(TimeZone.getTimeZone("GMT"));
        Element message = e.clone();

        message.setAttribute("from", from);
        message.removeAttribute("to");

        if (this.stanzas.size() >= this.maxSize) {
            this.stanzas.poll();
        }

        Element delay = new Element("delay", new String[] { "xmlns", "stamp" }, new String[] { "urn:xmpp:delay",
                String.format("%1$tY-%1$tm-%1$tdT%1$tH:%1$tM:%1$tSZ", now) });
        message.addChild(delay);
        message.addChild(new Element("x", new String[] { "xmlns", "stamp", "from" }, new String[] { "jabber:x:delay",
                String.format("%1$tY%1$tm%1$tdT%1$tH:%1$tM:%1$tS", now), roomId }));

        return this.stanzas.add(message);
    }

}