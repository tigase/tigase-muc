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
package tigase.muc.history;

import java.util.Date;
import java.util.logging.Logger;

import tigase.muc.DateUtil;
import tigase.xml.Element;

/**
 * @author bmalkow
 * 
 */
public abstract class AbstractHistoryProvider implements HistoryProvider {

	protected final Logger log = Logger.getLogger(this.getClass().getName());

	protected Element createMessage(String roomJID, String senderJID, String msgSenderNickname, String msg,
			String msgSenderJid, boolean addRealJids, Date msgTimestamp) {
		Element message = new Element("message", new String[] { "from", "to", "type" }, new String[] {
				roomJID + "/" + msgSenderNickname, senderJID, "groupchat" });
		message.addChild(new Element("body", msg));
		String from = addRealJids ? msgSenderJid : roomJID + "/" + msgSenderNickname;
		Element delay = new Element("delay", new String[] { "xmlns", "from", "stamp" }, new String[] { "urn:xmpp:delay", from,
				DateUtil.formatDatetime(msgTimestamp) });
		Element x = new Element("x", new String[] { "xmlns", "from", "stamp" }, new String[] { "jabber:x:delay", from,
				DateUtil.formatOld(msgTimestamp) });
		message.addChild(delay);
		message.addChild(x);

		return message;
	}

}
