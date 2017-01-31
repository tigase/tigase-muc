/*
 * Tigase Jabber/XMPP Multi-User Chat Component
 * Copyright (C) 2008 "Bartosz M. Małkowski" <bartosz.malkowski@tigase.org>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. Look for COPYING file in the top folder.
 * If not, see http://www.gnu.org/licenses/.
 *
 * $Rev$
 * Last modified by $Author$
 * $Date$
 */
package tigase.muc.history;

import java.util.Date;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import tigase.component.PacketWriter;
import tigase.db.DBInitException;
import tigase.db.Repository;
import tigase.muc.Affiliation;
import tigase.muc.Room;
import tigase.muc.RoomConfig.Anonymity;
import tigase.server.Packet;
import tigase.xml.Element;
import tigase.xmpp.BareJID;
import tigase.xmpp.JID;

/**
 * @author bmalkow
 * 
 */
@Repository.Meta( supportedUris = { "memory" } )
public class MemoryHistoryProvider extends AbstractHistoryProvider {

	private static class HItem {
		String body;
		/**
		 * whole stanza
		 */
		String msg;
		JID senderJid;
		String senderNickname;
		Date timestamp;
	}

	private final Map<BareJID, LinkedList<HItem>> history = new ConcurrentHashMap<BareJID, LinkedList<HItem>>();

	private int maxSize = 256;

	/**
	 * @param dataRepository
	 */
	public MemoryHistoryProvider() {
	}
	
	@Override
	public void initRepository(String resource_uri, Map<String,String> params) throws DBInitException {
	}

	@Override
	public void addJoinEvent(Room room, Date date, JID senderJID, String nickName) {
	}

	@Override
	public void addLeaveEvent(Room room, Date date, JID senderJID, String nickName) {
	}

	@Override
	public void addMessage(Room room, Element message, String body, JID senderJid, String senderNickname, Date time) {
		LinkedList<HItem> stanzas = this.history.get(room.getRoomJID());
		if (stanzas == null) {
			stanzas = new LinkedList<HItem>();
			this.history.put(room.getRoomJID(), stanzas);
		}

		if (stanzas.size() >= this.maxSize) {
			stanzas.poll();
		}

		HItem item = new HItem();
		item.body = body;
		item.senderJid = senderJid;
		item.senderNickname = senderNickname;
		item.timestamp = time;
		item.msg = message == null ? null : message.toString();

		stanzas.add(item);
	}

	@Override
	public void addSubjectChange(Room room, Element message, String subject, JID senderJid, String senderNickname, Date time) {
	}

	@Override
	public void destroy() {
		// nothing to do
	}	
	
	@Override
	public void getHistoryMessages(Room room, JID senderJID, Integer maxchars, Integer maxstanzas, Integer seconds, Date since,
			PacketWriter writer) {
		LinkedList<HItem> stanzas = this.history.get(room.getRoomJID());
		if (stanzas == null) {
			stanzas = new LinkedList<HItem>();
			this.history.put(room.getRoomJID(), stanzas);
		}

		int c = 0;
		final Date now = new Date();
		for (HItem item : stanzas) {

			if (since != null) {
				if (item.timestamp.before(since))
					continue;
			} else if (maxstanzas != null) {
				if (c >= maxstanzas)
					break;
			} else if (seconds != null) {
				if (item.timestamp.getTime() < now.getTime() - seconds * 1000)
					continue;
			} else {
				if (c >= 25)
					break;
			}

			Affiliation recipientAffiliation = room.getAffiliation(senderJID.getBareJID());
			boolean addRealJids = room.getConfig().getRoomAnonymity() == Anonymity.nonanonymous
					|| room.getConfig().getRoomAnonymity() == Anonymity.semianonymous
					&& (recipientAffiliation == Affiliation.owner || recipientAffiliation == Affiliation.admin);

			try {
				Packet message = createMessage(room.getRoomJID(), senderJID, item.senderNickname, item.msg, item.body,
						item.senderJid.toString(), addRealJids, item.timestamp);

				writer.write(message);
				++c;
			} catch (Exception e) {
				if (log.isLoggable(Level.SEVERE))
					log.log(Level.SEVERE, "Can't get history", e);
				throw new RuntimeException(e);
			}
		}
	}

	@Override
	public void init(Map<String, Object> props) {

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see tigase.muc.history.HistoryProvider#isPersistent()
	 */
	@Override
	public boolean isPersistent() {
		return false;
	}

	@Override
	public void removeHistory(Room room) {
		this.history.remove(room.getRoomJID());
	}

}
