/*
 * AbstractHistoryProvider.java
 *
 * Tigase Multi User Chat Component
 * Copyright (C) 2004-2016 "Tigase, Inc." <office@tigase.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. Look for COPYING file in the top folder.
 * If not, see http://www.gnu.org/licenses/.
 */
package tigase.muc.history;

import java.util.Date;
import java.util.Queue;
import java.util.logging.Logger;

import tigase.muc.DateUtil;
import tigase.server.Packet;
import tigase.util.TigaseStringprepException;
import tigase.xml.DomBuilderHandler;
import tigase.xml.Element;
import tigase.xml.SimpleParser;
import tigase.xml.SingletonFactory;
import tigase.xmpp.BareJID;
import tigase.xmpp.JID;

/**
 * @author bmalkow
 * 
 */
public abstract class AbstractHistoryProvider implements HistoryProvider {

	protected static final SimpleParser parser = SingletonFactory.getParserInstance();

	protected final Logger log = Logger.getLogger(this.getClass().getName());

	protected Packet createMessage(BareJID roomJID, JID senderJID, String msgSenderNickname, String originalMessage,
			String body, String msgSenderJid, boolean addRealJids, Date msgTimestamp) throws TigaseStringprepException {

		Packet message = null;

		if (originalMessage != null) {
			DomBuilderHandler domHandler = new DomBuilderHandler();
			parser.parse(domHandler, originalMessage.toCharArray(), 0, originalMessage.length());
			Queue<Element> queue = domHandler.getParsedElements();

			Element m = queue.poll();
			if (m != null) {
				m.setAttribute("type", "groupchat");
				m.setAttribute("from", JID.jidInstance(roomJID, msgSenderNickname).toString());
				m.setAttribute("to", senderJID.toString());
				
				message = Packet.packetInstance(m);
				message.setXMLNS(Packet.CLIENT_XMLNS);
			}
		}

		if (message == null) {
			message = Packet.packetInstance(new Element("message", new String[] { "type", "from", "to" }, new String[] {
					"groupchat", JID.jidInstance(roomJID, msgSenderNickname).toString(), senderJID.toString() }));
			message.setXMLNS(Packet.CLIENT_XMLNS);
			message.getElement().addChild(new Element("body", body));
		}

		// The 'from' attribute MUST be set to the JID of the room itself.
		Element delay = new Element("delay", new String[] { "xmlns", "from", "stamp" },
				new String[] { "urn:xmpp:delay", roomJID.toString(), DateUtil.formatDatetime(msgTimestamp) });
		message.getElement().addChild(delay);

		return message;
	}

}
