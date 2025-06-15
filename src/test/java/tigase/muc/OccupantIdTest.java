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

import org.junit.After;
import org.junit.Test;
import tigase.component.exceptions.RepositoryException;
import tigase.eventbus.EventBusFactory;
import tigase.kernel.core.Kernel;
import tigase.muc.modules.MAMQueryModule;
import tigase.muc.modules.MAMQueryParser;
import tigase.muc.modules.PresenceModule;
import tigase.muc.modules.PresenceModuleImpl;
import tigase.server.Packet;
import tigase.util.stringprep.TigaseStringprepException;
import tigase.vhosts.DummyVHostManager;
import tigase.xml.Element;
import tigase.xmpp.jid.BareJID;
import tigase.xmpp.jid.JID;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static org.junit.Assert.*;
import static tigase.muc.RoomConfig.MUC_ROOMCONFIG_PUBLICROOM_KEY;

public class OccupantIdTest
		extends AbstractMucTest{

	private final String vhost = "tigase.org";
	private final String componentName = "muc";
	private final String mucDomain = componentName + "." + vhost;
	private final JID adminJID = JID.jidInstanceNS("admin", vhost, "resource");
	private final String adminNick = "thirdwitch";
	private final JID userJID = JID.jidInstanceNS("user", vhost, "resource");
	private final String userNick = "secondwitch";
	final BareJID roomJID = BareJID.bareJIDInstanceNS("room", componentName + "." + vhost);
	private PermissionChecker permissionChecker;
	private PresenceModule presenceModule;
	private TestMUCCompoent mucComponent;

	protected void registerBeans(Kernel kernel) {
		super.registerBeans(kernel);
		getMucKernel().registerBean(PresenceModuleImpl.class).exec();
		try {
			getMucKernel().registerBean("eventBus").asInstance(EventBusFactory.getInstance()).exportable().exec();
			getMucKernel().registerBean("mucRepository").asInstance(new MockMucRepository()).exportable().exec();
			getMucKernel().registerBean(MAMQueryParser.class).exec();
			getMucKernel().registerBean(MAMQueryModule.class).exec();
		} catch (RepositoryException e) {
			throw new RuntimeException(e);
		}
		mucComponent = getKernel().getInstance(TestMUCCompoent.class);
		mucComponent.setAdmins(Set.of(adminJID.getBareJID()));

		final DummyVHostManager vHostManager = getKernel().getInstance(
				DummyVHostManager.class);
		vHostManager.addVhost(vhost);
		vHostManager.getVHostItem(vhost).setAdmins(new String[]{adminJID.getBareJID().toString()});
		permissionChecker = getMucKernel().getInstance(PermissionChecker.class);
		presenceModule = getMucKernel().getInstance(PresenceModule.class);
	}

	@After
	public void tearDown() throws Exception {
	}
	
	@Test
	public void test() throws RepositoryException, TigaseStringprepException {
		MUCComponent component = this.getInstance(MUCComponent.class);

		Packet packet = Packet.packetInstance(
				new Element("presence").withElement("x", "http://jabber.org/protocol/muc", (String) null), adminJID,
				JID.jidInstance(roomJID, adminNick));
		component.processPacket(packet);

		Element adminJoinedPresence = findByNameAndTo("presence", adminJID);
		String adminOccupantId = extractOccupantId(adminJoinedPresence);
		assertNotNull(adminOccupantId);

		Room room = getMucKernel().getInstance(MockMucRepository.class).getRoom(roomJID);
		assertEquals(room.generateOccupantId(adminJID.getBareJID()), adminOccupantId);

		room.getConfig().setValue(MUC_ROOMCONFIG_PUBLICROOM_KEY, "true");
		room.setRoomLocked(false);

		writer.getElements().clear();

		packet = Packet.packetInstance(
				new Element("presence").withElement("x", "http://jabber.org/protocol/muc", (String) null), userJID,
				JID.jidInstance(roomJID, userNick));
		component.processPacket(packet);

		Element userJoinedPresence = findByNameAndToAndFrom("presence", userJID, userNick);
		String userOccupantId = extractOccupantId(userJoinedPresence);
		assertEquals(room.generateOccupantId(userJID.getBareJID()), userOccupantId);

		Element adminPresence = findByNameAndToAndFrom("presence", userJID, adminNick);
		assertEquals(room.generateOccupantId(adminJID.getBareJID()), extractOccupantId(adminPresence));
		assertEquals(adminOccupantId, extractOccupantId(adminPresence));

		writer.clear();

		String messageBody1 = "Message " + UUID.randomUUID().toString();
		packet = Packet.packetInstance(new Element("message").withAttribute("type", "groupchat")
											   .withElement("body", body -> body.setCData(messageBody1)), adminJID, JID.jidInstance(roomJID));
		component.processPacket(packet);

		Element messageToAdmin = findByNameAndTo("message", adminJID);
		assertEquals(adminOccupantId, extractOccupantId(messageToAdmin));
		assertEquals(messageBody1, extractBody(messageToAdmin));
		Element messageToUser = findByNameAndTo("message", userJID);
		assertEquals(adminOccupantId, extractOccupantId(messageToUser));
		assertEquals(messageBody1, extractBody(messageToUser));

		writer.clear();

		String messageBody2 = "Message " + UUID.randomUUID().toString();
		packet = Packet.packetInstance(new Element("message").withAttribute("type", "groupchat")
											   .withElement("body", body -> body.setCData(messageBody2)), adminJID, JID.jidInstance(roomJID));
		component.processPacket(packet);

		messageToAdmin = findByNameAndTo("message", adminJID);
		assertEquals(adminOccupantId, extractOccupantId(messageToAdmin));
		assertEquals(messageBody2, extractBody(messageToAdmin));
		messageToUser = findByNameAndTo("message", userJID);
		assertEquals(adminOccupantId, extractOccupantId(messageToUser));
		assertEquals(messageBody2, extractBody(messageToUser));

		writer.clear();

		packet = Packet.packetInstance(new Element("iq").withAttribute("type", "set")
											   .withElement("query", "urn:xmpp:mam:1",
															query -> query.setAttribute("queryid", "f27")), adminJID,
									   JID.jidInstance(roomJID));
		component.processPacket(packet);

		assertEquals(3, writer.getElements().size());
		Element iq = findByNameAndTo("iq", adminJID);
		assertEquals("result", iq.getAttribute("type"));
		
		List<Element> messages = writer.getElements()
				.stream()
				.filter(it -> "message".equals(it.getName()) && adminJID.toString().equals(it.getAttribute("to")))
				.map(it -> it.findChild(el -> "result".equals(el.getName())))
				.filter(Objects::nonNull)
				.map(it -> it.findChild(el -> "forwarded".equals(el.getName())))
				.filter(Objects::nonNull)
				.map(it -> it.findChild(el -> "message".equals(el.getName())))
				.filter(Objects::nonNull).toList();

		assertEquals(2, messages.size());

		for (Element message : messages) {
			String senderNick = JID.jidInstance(message.getAttribute("from")).getResource();
			assertNotNull(senderNick);
			if (senderNick.equals(userNick)) {
				assertEquals(userOccupantId, extractOccupantId(message));
			} else if (senderNick.equals(adminNick)) {
				assertEquals(adminOccupantId, extractOccupantId(message));
			} else {
				fail();
			}
		}

		writer.clear();

		packet = Packet.packetInstance(
				new Element("presence").withAttribute("type", "unavailable"), userJID,
				JID.jidInstance(roomJID, userNick));
		component.processPacket(packet);

		assertEquals(2, writer.getElements().size());
		assertEquals(userOccupantId, extractOccupantId(findByNameAndTo("presence", adminJID)));
		assertEquals(userOccupantId, extractOccupantId(findByNameAndTo("presence", userJID)));

		writer.clear();

		packet = Packet.packetInstance(new Element("iq").withAttribute("type", "set").withElement("query", "http://jabber.org/protocol/muc#owner", query -> {
			query.withElement("destroy", null, (String) null);
		}), adminJID, JID.jidInstance(roomJID));
		component.processPacket(packet);

		assertEquals(2, writer.getElements().size());
		assertEquals(adminOccupantId, extractOccupantId(findByNameAndTo("presence", adminJID)));
		assertNotNull(findByNameAndTo("iq", adminJID));
	}

	private Element findByNameAndToAndFrom(String name, JID toJid, String fromNick) {
		return writer.getElements()
				.stream()
				.filter(el -> el.getName().equals(name))
				.filter(el -> toJid.toString().equals(el.getAttribute("to")))
				.filter(el -> el.getAttribute("from").endsWith("/" + fromNick))
				.findFirst()
				.orElseThrow();
	}

	private Element findByNameAndFrom(String name, String fromNick) {
		return writer.getElements()
				.stream()
				.filter(el -> el.getName().equals(name))
				.filter(el -> el.getAttribute("from").endsWith("/" + fromNick))
				.findFirst()
				.orElseThrow();
	}

	private Element findByNameAndTo(String name, JID toJid) {
		return writer.getElements()
				.stream()
				.filter(el -> el.getName().equals(name))
				.filter(el -> toJid.toString().equals(el.getAttribute("to")))
				.findFirst()
				.orElseThrow();
	}

	private String extractOccupantId(Element element) {
		return element.findChild(
						el -> "occupant-id".equals(el.getName()) && "urn:xmpp:occupant-id:0".equals(el.getXMLNS()))
				.map(el -> el.getAttribute("id"));
	}

	private String extractBody(Element element) {
		return element.findChild(el -> "body".equals(el.getName())).getCData();
	}
}
