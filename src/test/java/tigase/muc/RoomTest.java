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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Before;

import tigase.component.ElementWriter;
import tigase.component.exceptions.RepositoryException;
import tigase.server.Packet;
import tigase.test.junit.JUnitXMLIO;
import tigase.test.junit.XMPPTestCase;
import tigase.util.TigaseStringprepException;
import tigase.xml.Element;
import tigase.xmpp.BareJID;
import tigase.xmpp.JID;

/**
 * @author bmalkow
 * 
 */
public class RoomTest extends XMPPTestCase {

	private final class ArrayWriter implements ElementWriter {

		private final ArrayList<Element> elements = new ArrayList<Element>();

		public void clear() {
			elements.clear();
		}

		@Override
		public void write(Collection<Packet> elements) {
			for (Packet packet : elements) {
				this.elements.add(packet.getElement());
			}
		}

		@Override
		public void write(Packet element) {
			this.elements.add(element.getElement());
		}

		@Override
		public void writeElement(Collection<Element> elements) {
			this.elements.addAll(elements);
		}

		@Override
		public void writeElement(Element element) {
			this.elements.add(element);
		}
	}

	private MUCComponent pubsub;

	private JUnitXMLIO xmlio;

	@Before
	public void init() throws RepositoryException, TigaseStringprepException {
		final ArrayWriter writer = new ArrayWriter();
		this.pubsub = new MUCComponent(writer) {
			@Override
			public JID getComponentId() {
				try {
					return JID.jidInstance("test.com");
				} catch (TigaseStringprepException e) {
					throw new RuntimeException(e);
				}
			}
		};
		this.pubsub.setName("xxx");

		Map<String, Object> props = new HashMap<String, Object>();
		props.put("multi-user-chat", BareJID.bareJIDInstance("multi-user-chat"));
		props.put(MUCComponent.MESSAGE_FILTER_ENABLED_KEY, Boolean.TRUE);
		props.put(MUCComponent.PRESENCE_FILTER_ENABLED_KEY, Boolean.FALSE);
		props.put(MUCComponent.LOG_DIR_KEY, "./");

		pubsub.getConfig().setProperties(props);
		this.pubsub.setMucRepository(new MockMucRepository(pubsub.getConfig()));

		xmlio = new JUnitXMLIO() {

			@Override
			public void close() {
				// TODO Auto-generated method stub

			}

			@Override
			public void setIgnorePresence(boolean arg0) {
				// TODO Auto-generated method stub

			}

			@Override
			public void write(Element data) throws IOException {
				try {
					writer.clear();
					Packet p = Packet.packetInstance(data);
					pubsub.processPacket(p);
					send(writer.elements);
				} catch (TigaseStringprepException e) {
					e.printStackTrace();
				}
			}
		};

		pubsub.init();
	}

	@org.junit.Test
	public void test_destroyRoom() {
		test("src/test/scripts/destroying-room.cor", xmlio);
		try {
			Room room = pubsub.getMucRepository().getRoom(BareJID.bareJIDInstance("darkcave@macbeth.shakespeare.lit"));
			Assert.assertNull("Room should be destroyed", room);
		} catch (Exception e) {
			e.printStackTrace();
			Assert.fail(e.getMessage());
		}
	}

	@org.junit.Test
	public void test_ghostUser() {
		test("src/test/scripts/ghostUser.cor", xmlio);
	}

	@org.junit.Test
	public void test_nonpersistentRoomProblem() {
		test("src/test/scripts/nonpersistent-room-problem.cor", xmlio);
		try {
			Room room = pubsub.getMucRepository().getRoom(BareJID.bareJIDInstance("darkcave@macbeth.shakespeare.lit"));
			Assert.assertNull("Room should be destroyed", room);
		} catch (Exception e) {
			e.printStackTrace();
			Assert.fail(e.getMessage());
		}
	}

	// @org.junit.Test
	// public void test_presences() {
	// test("src/test/scripts/processPresence-empty.cor", xmlio);
	// }

	@org.junit.Test
	public void test_pings() {
		test("src/test/scripts/ping.cor", xmlio);
	}

	@org.junit.Test
	public void test_presences2() {
		test("src/test/scripts/processPresence2.cor", xmlio);
	}

}
