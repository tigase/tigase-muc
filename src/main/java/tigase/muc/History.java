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
package tigase.muc;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.TimeZone;

import tigase.xml.Element;

/**
 * @author bmalkow
 * 
 */
public class History {

	private static class HItem {
		String message;
		String senderJid;
		String senderNickname;
		Date timestamp;
	}

	private final static SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");

	private int maxSize = 100;

	private LinkedList<HItem> stanzas = new LinkedList<HItem>();

	/**
	 * 
	 */
	public History() {
		sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
	}

	public void add(final String message, String senderJid, String senderNickname, Date time) {
		if (this.stanzas.size() >= this.maxSize) {
			this.stanzas.poll();
		}

		HItem item = new HItem();
		item.message = message;
		item.senderJid = senderJid;
		item.senderNickname = senderNickname;
		item.timestamp = time;

		this.stanzas.add(item);
	}

	/**
	 * @param recipientJid
	 */
	public List<Element> getMessages(String recipientJid, String roomId, boolean addRealJids) {
		List<Element> m = new ArrayList<Element>();
		for (HItem item : this.stanzas) {

			Element message = new Element("message", new String[] { "from", "to", "type" }, new String[] {
					roomId + "/" + item.senderNickname, recipientJid, "groupchat" });
			message.addChild(new Element("body", item.message));

			String from = addRealJids ? item.senderJid : roomId + "/" + item.senderNickname;
			String ts = sdf.format(item.timestamp);
			Element delay = new Element("delay", new String[] { "xmlns", "from", "stamp" }, new String[] { "urn:xmpp:delay", from,
					ts });

			Calendar now = Calendar.getInstance();
			now.setTimeZone(TimeZone.getTimeZone("GMT"));
			now.setTime(item.timestamp);
			Element x = new Element("x", new String[] { "xmlns", "from", "stamp" }, new String[] { "jabber:x:delay", from,
					String.format("%1$tY%1$tm%1$tdT%1$tH:%1$tM:%1$tS", now) });

			message.addChild(delay);
			message.addChild(x);
			m.add(message);
		}
		return m;
	}

}
