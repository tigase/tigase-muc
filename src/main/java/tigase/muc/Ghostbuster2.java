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
package tigase.muc;

import tigase.component.ScheduledTask;
import tigase.kernel.beans.Bean;
import tigase.kernel.beans.Inject;
import tigase.muc.modules.PresenceModule;
import tigase.muc.repository.IMucRepository;
import tigase.server.Packet;
import tigase.server.ReceiverTimeoutHandler;
import tigase.util.stringprep.TigaseStringprepException;
import tigase.xml.Element;
import tigase.xmpp.jid.BareJID;
import tigase.xmpp.jid.JID;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author bmalkow
 */
@Bean(name = "ghostbuster", parent = MUCComponent.class, active = true)
public class Ghostbuster2
		extends ScheduledTask {

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
	private static long idCounter;
	protected final Map<JID, MonitoredObject> monitoredObjects = new ConcurrentHashMap<JID, MonitoredObject>();
	private final ReceiverTimeoutHandler pingHandler;
	protected Logger log = Logger.getLogger(this.getClass().getName());
	@Inject
	private MUCConfig config;
	@Inject
	private MUCComponent mucComponent;
	@Inject
	private PresenceModule presenceModule;
	@Inject
	private IMucRepository repository;

	public Ghostbuster2() {
		super(Duration.ofMinutes(10), Duration.ofMinutes(5));

		this.pingHandler = new ReceiverTimeoutHandler() {
			@Override
			public void responseReceived(Packet data, Packet response) {
				try {
					if (log.isLoggable(Level.FINEST)) {
						log.log(Level.FINEST, "Received ping response for ping, data: {0}, response: {1}",
								new Object[]{data, response});
					}
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

			if (log.isLoggable(Level.FINE)) {
				log.fine(occupantJid + " registered in room " + room.getRoomJID());
			}

			if (o == null) {
				if (log.isLoggable(Level.FINE)) {
					log.fine("Start observing " + occupantJid);
				}

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

	public PresenceModule getPresenceModule() {
		return presenceModule;
	}

	public void setPresenceModule(PresenceModule presenceModule) {
		this.presenceModule = presenceModule;
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
				if (r != null) {
					ping(r, entry.source);
				}
			}
		}
	}

	public void remove(Collection<JID> occupantJids, Room room) {
		for (JID jid : occupantJids) {
			remove(jid, room);
		}
	}

	public void remove(JID occupantJid, Room room) {
		try {
			MonitoredObject o = monitoredObjects.get(occupantJid);
			if (o == null) {
				return;
			}

			if (log.isLoggable(Level.FINE)) {
				log.fine(occupantJid + " unregisterd from room " + room.getRoomJID());
			}

			synchronized (o.rooms) {
				o.rooms.remove(room.getRoomJID());

				if (o.rooms.isEmpty()) {
					if (log.isLoggable(Level.FINE)) {
						log.fine("Stop observing " + occupantJid);
					}

					monitoredObjects.remove(occupantJid);

				}
			}
		} catch (Exception e) {
			log.log(Level.WARNING, "Problem on unregistering occupant", e);
		}
	}

	@Override
	public void run() {
		if (config.isGhostbusterEnabled()) {
			try {
				ping();
			} catch (Exception e) {
				log.log(Level.WARNING, "Problem on executing ghostbuster", e);
			}
		}
	}

	public void update(Packet packet) throws TigaseStringprepException {
		if (packet.getStanzaFrom() == null) {
			return;
		}

		final MonitoredObject o = monitoredObjects.get(packet.getStanzaFrom());

		if (o == null) {
			return;
		}

		if (checkError(packet) != null) {
			if (log.isLoggable(Level.FINEST)) {
				log.finest("Received presence error: " + packet.getElement().toString());
			}
			processError(o, packet);
		} else {
			// update last activity
			if (log.isLoggable(Level.FINER)) {
				log.finer("Update activity of " + o.source);
			}

			o.lastActivity = System.currentTimeMillis();
		}
	}

	public void kickJIDFromRooms(JID jid, Collection<BareJID> rooms) throws TigaseStringprepException {
		if (log.isLoggable(Level.FINEST)) {
			log.log(Level.FINEST, "Kicking out JID: {0}, from rooms: {1}", new Object[]{jid, rooms});
		}
		this.monitoredObjects.remove(jid);
		for (Room r : repository.getActiveRooms().values()) {
			if ((rooms == null || rooms.contains(r.getRoomJID())) && r.isOccupantInRoom(jid)) {
				presenceModule.doQuit(r, jid, StatusCodes.REMOVED_FROM_ROOM);
			}
		}
	}

	protected void onPingReceived(Packet packet) throws TigaseStringprepException {
		update(packet);
	}

	protected void onPingTimeout(JID stanzaTo) throws TigaseStringprepException {
		if (log.isLoggable(Level.FINEST)) {
			log.finest("Timeouted ping to: " + stanzaTo);
		}

		// final MonitoredObject obj = monitoredObjects.get(stanzaTo);
		//
		// if (obj == null)
		// return;
		//
		// if ((presenceModule == null) || (mucComponent.getMucRepository() ==
		// null)) {
		// return;
		// }
		//
		// if (log.isLoggable(Level.FINEST)) {
		// log.finest("Forced removal last activity of " + obj.source);
		// }
		//
		// this.monitoredObjects.remove(obj);
		// for (Room r :
		// mucComponent.getMucRepository().getActiveRooms().values()) {
		// if (obj.rooms.contains(r.getRoomJID()) &&
		// r.isOccupantInRoom(obj.source)) {
		// presenceModule.doQuit(r, obj.source);
		// }
		// }
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

	private void ping(BareJID room, JID occupantJID) throws TigaseStringprepException {
		final String id = "png-" + (++idCounter);

		if (log.isLoggable(Level.FINER)) {
			log.log(Level.FINER, "Pinging " + occupantJID + ". id=" + id);
		}

		Element ping = new Element("iq", new String[]{"type", "id", "from", "to"},
								   new String[]{"get", id, room.toString(), occupantJID.toString()});

		ping.addChild(new Element("ping", new String[]{"xmlns"}, new String[]{"urn:xmpp:ping"}));

		Packet packet = Packet.packetInstance(ping);
		packet.setXMLNS(Packet.CLIENT_XMLNS);

		mucComponent.addOutPacketWithTimeout(packet, pingHandler, 1, TimeUnit.MINUTES);

		if (log.isLoggable(Level.FINER)) {
			log.log(Level.FINER, "Pinged " + occupantJID);
		}
	}

	private void processError(MonitoredObject obj, Packet packet) throws TigaseStringprepException {
		if ((presenceModule == null) || (repository == null)) {
			return;
		}

		if (log.isLoggable(Level.FINEST)) {
			log.finest("Forced removal last activity of " + obj.source);
		}

		kickJIDFromRooms(obj.source, obj.rooms);
	}

	protected class MonitoredObject {

		private final JID source;
		private long lastActivity;
		private HashSet<BareJID> rooms = new HashSet<BareJID>();

		public MonitoredObject(JID occupantJid) {
			this.source = occupantJid;
		}

	}

}
