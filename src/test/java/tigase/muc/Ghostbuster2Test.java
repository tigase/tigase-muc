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
package tigase.muc;

import org.junit.Test;
import tigase.component.exceptions.RepositoryException;
import tigase.muc.modules.PresenceModule;
import tigase.muc.modules.PresenceModuleImpl;
import tigase.server.Packet;
import tigase.server.ReceiverTimeoutHandler;
import tigase.util.TigaseStringprepException;
import tigase.xml.Element;
import tigase.xmpp.Authorization;
import tigase.xmpp.BareJID;
import tigase.xmpp.JID;
import tigase.xmpp.PacketErrorTypeException;

import java.lang.reflect.Field;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;

/**
 * Created by andrzej on 27.12.2016.
 */
public class Ghostbuster2Test {

	@Test
	public void testPingForOver1000() throws Exception {
		MUCComponent mucComponent = new MUCComponent();
		AtomicBoolean timeouts = new AtomicBoolean(true);
		PresenceModule presenceModule = new PresenceModuleImpl() {
			@Override
			public void doQuit(Room room, JID senderJID) throws TigaseStringprepException {
				room.removeOccupant(senderJID);
			}
		};

		Ghostbuster2 ghostbuster2 = new Ghostbuster2(mucComponent);
		ghostbuster2.setPresenceModule(presenceModule);

		Map<BareJID, Room> activeRooms = new ConcurrentHashMap<>();

		for (int i = 0; i < 4000; i++) {
			JID jid = JID.jidInstance("user-" + i + "@test");
			Room room = new Room(new RoomConfig(BareJID.bareJIDInstance("room-" + i + "@muc.test")), new Date(),
								 jid.getBareJID());
			room.addOccupantByJid(jid, jid.getLocalpart(), Role.moderator,
								  new Element("presence", new String[]{"from", "to"},
											  new String[]{jid.toString(), room.getRoomJID().toString()}));

			activeRooms.put(room.getRoomJID(), room);
			ghostbuster2.add(jid, room);
		}

		mucComponent.setActiveRooms(activeRooms);

		AtomicInteger packetCounter = new AtomicInteger();
		mucComponent.handler = new PacketHandler() {
			@Override
			public void handle(Packet packet, ReceiverTimeoutHandler handler) throws PacketErrorTypeException {
				int value = packetCounter.incrementAndGet();
				if (value % 2 == 0) {
					if (timeouts.get() && value % 3 == 0) {
						handler.timeOutExpired(packet);
					} else {
						Packet response = Authorization.SERVICE_UNAVAILABLE.getResponseMessage(packet, "Service not available.", true);
						handler.responseReceived(packet, response);
					}
				} else {
					if (value % 3 == 0) {
						Packet response = Authorization.FEATURE_NOT_IMPLEMENTED.getResponseMessage(packet, "Feature not implemented", true);
						handler.responseReceived(packet, response);
					} else {
						handler.responseReceived(packet, packet.okResult((Element) null, 0));
					}
				}
			}
		};

		ghostbuster2.ping();

		assertEquals(0, packetCounter.get());

		for (Ghostbuster2.MonitoredObject monitor : ghostbuster2.monitoredObjects.values()) {
			Field f = Ghostbuster2.MonitoredObject.class.getDeclaredField("lastActivity");
			f.setAccessible(true);
			long lastActivity = f.getLong(monitor);
			f.setLong(monitor, lastActivity - 65 * 60 * 1000);
		}

		ghostbuster2.ping();

		assertEquals(1000, packetCounter.get());
		assertEquals(3666, ghostbuster2.monitoredObjects.size());

		packetCounter.set(0);

		ghostbuster2.ping();

		assertEquals(1000, packetCounter.get());
		assertEquals(3332, ghostbuster2.monitoredObjects.size());

		packetCounter.set(0);

		ghostbuster2.ping();

		assertEquals(1000, packetCounter.get());
		assertEquals(2998, ghostbuster2.monitoredObjects.size());

		packetCounter.set(0);

		ghostbuster2.ping();

		assertEquals(1000, packetCounter.get());
		assertEquals(2664, ghostbuster2.monitoredObjects.size());

		packetCounter.set(0);

		timeouts.set(false);

		ghostbuster2.ping();

		assertEquals(664, packetCounter.get());
		assertEquals(2332, ghostbuster2.monitoredObjects.size());

		packetCounter.set(0);

		ghostbuster2.ping();

		assertEquals(0, packetCounter.get());
	}

	private class MUCComponent
			extends tigase.muc.MUCComponent {

		private PacketHandler handler;

		@Override
		public boolean addOutPacketWithTimeout(Packet packet, ReceiverTimeoutHandler handler, long delay,
											   TimeUnit unit) {

			try {
				this.handler.handle(packet, handler);
			} catch (Exception ex) {
				throw new RuntimeException(ex);
			}
			return true;
		}

		public void setActiveRooms(final Map<BareJID, Room> activeRooms) throws RepositoryException {
			mucRepository = new MockMucRepository() {
				@Override
				public Map<BareJID, Room> getActiveRooms() {
					return activeRooms;
				}
			};
		}
	}

	private interface PacketHandler {

		void handle(Packet packet, ReceiverTimeoutHandler handler) throws PacketErrorTypeException;
	}
}
