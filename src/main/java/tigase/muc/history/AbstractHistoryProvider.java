/*
 * Tigase MUC - Multi User Chat component for Tigase
 * Copyright (C) 2007 Tigase, Inc. (office@tigase.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, version 3 of the License.
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

import tigase.db.DataSource;
import tigase.muc.Affiliation;
import tigase.muc.Room;
import tigase.muc.RoomConfig;
import tigase.server.Packet;
import tigase.util.datetime.TimestampHelper;
import tigase.util.stringprep.TigaseStringprepException;
import tigase.xml.DomBuilderHandler;
import tigase.xml.Element;
import tigase.xml.SimpleParser;
import tigase.xml.SingletonFactory;
import tigase.xmpp.jid.BareJID;
import tigase.xmpp.jid.JID;
import tigase.xmpp.mam.Query;
import tigase.xmpp.rsm.RSM;

import java.util.Date;
import java.util.Queue;
import java.util.logging.Logger;

/**
 * @author bmalkow
 */
public abstract class AbstractHistoryProvider<DS extends DataSource>
		implements HistoryProvider<DS> {

	protected static final SimpleParser parser = SingletonFactory.getParserInstance();

	protected final Logger log = Logger.getLogger(this.getClass().getName());

	private final TimestampHelper timestampHelper = new TimestampHelper();

	protected static <Q extends Query> void calculateOffsetAndPosition(Q query, int count, Integer before,
																	   Integer after) {
		RSM rsm = query.getRsm();
		int index = rsm.getIndex() == null ? 0 : rsm.getIndex();
		int limit = rsm.getMax();

		if (after != null) {
			// it is ok, if we go out of range we will return empty result
			index = after + 1;
		} else if (before != null) {
			index = before - rsm.getMax();
			// if we go out of range we need to set index to 0 and reduce limit
			// to return proper results
			if (index < 0) {
				index = 0;
				limit = before;
			}
		} else if (rsm.hasBefore()) {
			index = count - rsm.getMax();
			if (index < 0) {
				index = 0;
			}
		}
		rsm.setIndex(index);
		rsm.setMax(limit);
		rsm.setCount(count);
	}

	public Packet createMessage(BareJID roomJID, JID senderJID, String msgSenderNickname, String originalMessage,
								String body, String msgSenderJid, boolean addRealJids, Date msgTimestamp)
			throws TigaseStringprepException {

		Packet message = Packet.packetInstance(
				createMessageElement(roomJID, senderJID, msgSenderNickname, originalMessage, body));

		// The 'from' attribute MUST be set to the JID of the room itself.
		Element delay = new Element("delay", new String[]{"xmlns", "from", "stamp"},
									new String[]{"urn:xmpp:delay", roomJID.toString(),
												 timestampHelper.formatWithMs(msgTimestamp)});
		message.getElement().addChild(delay);

		return message;
	}

	public Element createMessageElement(BareJID roomJID, JID senderJID, String msgSenderNickname,
										String originalMessage, String body) throws TigaseStringprepException {
		Element message = null;
		if (originalMessage != null) {
			DomBuilderHandler domHandler = new DomBuilderHandler();
			parser.parse(domHandler, originalMessage.toCharArray(), 0, originalMessage.length());
			Queue<Element> queue = domHandler.getParsedElements();

			message = queue.poll();
			if (message != null) {
				message.setAttribute("type", "groupchat");
				message.setAttribute("from", JID.jidInstance(roomJID, msgSenderNickname).toString());
				message.setAttribute("to", senderJID.toString());

				message.setXMLNS(Packet.CLIENT_XMLNS);
			}
		}

		if (message == null) {
			message = new Element("message", new String[]{"type", "from", "to", "xmlns"},
								  new String[]{"groupchat", JID.jidInstance(roomJID, msgSenderNickname).toString(),
											   senderJID.toString(), Packet.CLIENT_XMLNS});
			message.addChild(new Element("body", body));
		}

		return message;
	}

	protected boolean isAllowedToSeeJIDs(BareJID jid, Room room) {
		final Affiliation aff = room.getAffiliation(jid).getAffiliation();
		final RoomConfig.WhoisPrivilege whois = room.getConfig().getWhois();
		if (whois == RoomConfig.WhoisPrivilege.anyone) {
			return true;
		} else {
			return aff.isViewOccupantsJid();
		}
	}
}
