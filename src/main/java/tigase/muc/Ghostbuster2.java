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

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import tigase.muc.modules.PresenceModule;
import tigase.server.Packet;
import tigase.server.ReceiverTimeoutHandler;
import tigase.util.TigaseStringprepException;
import tigase.xml.Element;
import tigase.xmpp.BareJID;
import tigase.xmpp.JID;

/**
 * @author bmalkow
 * 
 */
public class Ghostbuster2 {

	protected class MonitoredObject {

		private long lastActivity;

		private HashSet<BareJID> rooms = new HashSet<BareJID>();

		private final JID source;

		/**
		 * @param occupantJid
		 */
		public MonitoredObject(JID occupantJid) {
			this.source = occupantJid;
		}

	}

	private static long idCounter;

	private static final Set<String> intReasons = new HashSet<String>() {
		private static final long serialVersionUID = 1L;
		{
			add("gone");
			add("item-not-found");
			add("policy-violation");
			add("recipient-unavailable");
			add("redirect");
			add("remote-server-not-found");
			add("remote-server-timeout");
			add("service-unavailable");
		}
	};

	public static final Set<String> R = Collections.unmodifiableSet(intReasons);

	protected Logger log = Logger.getLogger(this.getClass().getName());

	protected final Map<JID, MonitoredObject> monitoredObjects = new ConcurrentHashMap<JID, MonitoredObject>();

	private final MUCComponent mucComponent;

	private final ReceiverTimeoutHandler pingHandler;
	private PresenceModule presenceModule;

	public Ghostbuster2(MUCComponent mucComponent) {
		this.mucComponent = mucComponent;
		this.pingHandler = new ReceiverTimeoutHandler() {
			@Override
			public void responseReceived(Packet data, Packet response) {
				try {
					onPingReceived(response);
				} catch (Exception e) {
					if (log.isLoggable(Level.WARNING)) {
						log.log(Level.WARNING, "Problem on handling ping response", e);
					}
				}
			}

			@Override
			public void timeOutExpired(Packet data) {
				try {
					if (log.isLoggable(Level.FINEST)) {
						log.finest("Received ping timeout for ping " + data.getElement().getAttributeStaticStr("id"));
					}
					onPingTimeout(data.getStanzaTo());
				} catch (Exception e) {
					if (log.isLoggable(Level.WARNING)) {
						log.log(Level.WARNING, "Problem on handling ping timeout", e);
					}
				}
			}
		};
	}

	public void add(JID occupantJid, Room room) {
		try {
			MonitoredObject o = monitoredObjects.get(occupantJid);

			if (log.isLoggable(Level.FINE))
				log.fine(occupantJid + " registered in room " + room.getRoomJID());

			if (o == null) {
				if (log.isLoggable(Level.FINE))
					log.fine("Start observing " + occupantJid);

				o = new MonitoredObject(occupantJid);
				o.lastActivity = System.currentTimeMillis();
				monitoredObjects.put(occupantJid, o);
			}
			synchronized (o.rooms) {
				o.rooms.add(room.getRoomJID());
			}
		} catch (Exception e) {
			log.log(Level.WARNING, "Problem on registering occupant", e);
		}

	}

	private String checkError(final Packet packet) {
		final String type = packet.getElement().getAttributeStaticStr(Packet.TYPE_ATT);

		if ((type == null) || !type.equals("error")) {
			return null;
		}

		final Element errorElement = packet.getElement().getChild("error");

		if (errorElement == null) {
			return null;
		}
		for (Element reason : errorElement.getChildren()) {
			if ((reason.getXMLNS() == null) || !reason.getXMLNS().equals("urn:ietf:params:xml:ns:xmpp-stanzas")) {
				continue;
			}
			if (R.contains(reason.getName())) {
				return reason.getName();
			}
		}

		return null;
	}

	public PresenceModule getPresenceModule() {
		return presenceModule;
	}

	/**
	 * @param response
	 * @throws TigaseStringprepException
	 */
	protected void onPingReceived(Packet packet) throws TigaseStringprepException {
		update(packet);
	}

	/**
	 * @param stanzaTo
	 * @throws TigaseStringprepException
	 */
	protected void onPingTimeout(JID stanzaTo) throws TigaseStringprepException {
		if (log.isLoggable(Level.FINEST))
			log.finest("Timeouted ping to: " + stanzaTo);

//		final MonitoredObject obj = monitoredObjects.get(stanzaTo);
//
//		if (obj == null)
//			return;
//
//		if ((presenceModule == null) || (mucComponent.getMucRepository() == null)) {
//			return;
//		}
//
//		if (log.isLoggable(Level.FINEST)) {
//			log.finest("Forced removal last activity of " + obj.source);
//		}
//
//		this.monitoredObjects.remove(obj);
//		for (Room r : mucComponent.getMucRepository().getActiveRooms().values()) {
//			if (obj.rooms.contains(r.getRoomJID()) && r.isOccupantInRoom(obj.source)) {
//				presenceModule.doQuit(r, obj.source);
//			}
//		}
	}

	public void ping() throws TigaseStringprepException {
		if (log.isLoggable(Level.FINE)) {
			log.log(Level.FINE, "Pinging up to 1000 known JIDs with 1h of inactivity");
		}

		int c = 0;
		final long now = System.currentTimeMillis();
		final long border = now - 1000 * 60 * 60;
		Iterator<MonitoredObject> it = this.monitoredObjects.values().iterator();

		while (it.hasNext() && (c < 1000)) {
			MonitoredObject entry = it.next();

			if (entry.lastActivity < border) {
				++c;

				BareJID r = null;
				synchronized (entry.rooms) {
					if (!entry.rooms.isEmpty()) {
						r = entry.rooms.iterator().next();
					}
				}
				if (r != null)
					ping(r, entry.source);
			}
		}
	}

	private void ping(BareJID room, JID occupantJID) throws TigaseStringprepException {
		final String id = "png-" + (++idCounter);

		if (log.isLoggable(Level.FINER)) {
			log.log(Level.FINER, "Pinging " + occupantJID + ". id=" + id);
		}

		Element ping = new Element("iq", new String[] { "type", "id", "from", "to" }, new String[] { "get", id,
				room.toString(), occupantJID.toString() });

		ping.addChild(new Element("ping", new String[] { "xmlns" }, new String[] { "urn:xmpp:ping" }));

		Packet packet = Packet.packetInstance(ping);
		packet.setXMLNS(Packet.CLIENT_XMLNS);

		mucComponent.addOutPacketWithTimeout(packet, pingHandler, 1, TimeUnit.MINUTES);

		if (log.isLoggable(Level.FINER))
			log.log(Level.FINER, "Pinged " + occupantJID);
	}

	/**
	 * @param obj
	 * @param packet
	 * @throws TigaseStringprepException
	 */
	private void processError(MonitoredObject obj, Packet packet) throws TigaseStringprepException {
		if ((presenceModule == null) || (mucComponent.getMucRepository() == null)) {
			return;
		}

		if (log.isLoggable(Level.FINEST)) {
			log.finest("Forced removal last activity of " + obj.source);
		}

		this.monitoredObjects.remove(obj.source);
		for (Room r : mucComponent.getMucRepository().getActiveRooms().values()) {
			if (obj.rooms.contains(r.getRoomJID()) && r.isOccupantInRoom(obj.source)) {
				presenceModule.doQuit(r, obj.source);
			}
		}
	}

	/**
	 * @param occupantJids
	 * @param room
	 */
	public void remove(Collection<JID> occupantJids, Room room) {
		for (JID jid : occupantJids) {
			remove(jid, room);
		}
	}

	public void remove(JID occupantJid, Room room) {
		try {
			MonitoredObject o = monitoredObjects.get(occupantJid);
			if (o == null)
				return;

			if (log.isLoggable(Level.FINE))
				log.fine(occupantJid + " unregisterd from room " + room.getRoomJID());

			synchronized (o.rooms) {
				o.rooms.remove(room.getRoomJID());

				if (o.rooms.isEmpty()) {
					if (log.isLoggable(Level.FINE))
						log.fine("Stop observing " + occupantJid);

					monitoredObjects.remove(occupantJid);

				}
			}
		} catch (Exception e) {
			log.log(Level.WARNING, "Problem on unregistering occupant", e);
		}
	}

	public void setPresenceModule(PresenceModule presenceModule) {
		this.presenceModule = presenceModule;
	}

	public void update(Packet packet) throws TigaseStringprepException {
		if (packet.getStanzaFrom() == null)
			return;

		final MonitoredObject o = monitoredObjects.get(packet.getStanzaFrom());

		if (o == null)
			return;

		if (checkError(packet) != null) {
			if (log.isLoggable(Level.FINEST)) {
				log.finest("Received presence error: " + packet.getElement().toString());
			}
			processError(o, packet);
		} else {
			// update last activity
			if (log.isLoggable(Level.FINER))
				log.finer("Update activity of " + o.source);

			o.lastActivity = System.currentTimeMillis();
		}
	}

}
