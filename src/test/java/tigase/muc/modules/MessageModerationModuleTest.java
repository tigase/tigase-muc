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
package tigase.muc.modules;

import org.junit.Before;
import org.junit.Test;
import tigase.component.exceptions.RepositoryException;
import tigase.eventbus.EventBusFactory;
import tigase.kernel.core.Kernel;
import tigase.muc.*;
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

public class MessageModerationModuleTest extends AbstractMucTest {

	private final String vhost = "tigase.org";
	private final String componentName = "muc";
	private final String mucDomain = componentName + "." + vhost;

	private final JID adminJID = JID.jidInstanceNS("admin", vhost, "resource");
	private final String adminNick = "thirdwitch";
	private final JID userJID = JID.jidInstanceNS("user", vhost, "resource");
	private final String userNick = "secondwitch";
	final BareJID roomJID = BareJID.bareJIDInstanceNS("room", componentName + "." + vhost);
	private String adminOccupantId;
	private PermissionChecker permissionChecker;
	private PresenceModule presenceModule;
	private TestMUCCompoent mucComponent;
	private String userOccupantId;
	private String messageId;

	protected void registerBeans(Kernel kernel) {
		super.registerBeans(kernel);
		getMucKernel().registerBean(PresenceModuleImpl.class).exec();
		try {
			getMucKernel().registerBean("eventBus").asInstance(EventBusFactory.getInstance()).exportable().exec();
			getMucKernel().registerBean("mucRepository").asInstance(new MockMucRepository()).exportable().exec();
			getMucKernel().registerBean(MessageModerationModule.class).exec();
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

	@Before
	public void setUp() throws Exception {
		// create room and join admin
		Packet packet = Packet.packetInstance(
				new Element("presence").withElement("x", "http://jabber.org/protocol/muc", (String) null), adminJID,
				JID.jidInstance(roomJID, adminNick));
		mucComponent.processPacket(packet);

		Element adminJoinedPresence = findByNameAndTo("presence", adminJID);
		adminOccupantId = extractOccupantId(adminJoinedPresence);
		assertNotNull(adminOccupantId);

		// unlock room
		Room room = getMucKernel().getInstance(MockMucRepository.class).getRoom(roomJID);
		assertEquals(room.generateOccupantId(adminJID.getBareJID()), adminOccupantId);

		room.getConfig().setValue(MUC_ROOMCONFIG_PUBLICROOM_KEY, "true");
		room.setRoomLocked(false);

		writer.clear();

		// join user
		packet = Packet.packetInstance(
				new Element("presence").withElement("x", "http://jabber.org/protocol/muc", (String) null), userJID,
				JID.jidInstance(roomJID, userNick));
		mucComponent.processPacket(packet);

		Element userJoinedPresence = findByNameAndToAndFrom("presence", userJID, userNick);
		userOccupantId = extractOccupantId(userJoinedPresence);
		assertNotNull(userOccupantId);
		writer.clear();

		// send message
		String messageBody = "Message " + UUID.randomUUID().toString();
		packet = Packet.packetInstance(
				new Element("message").withAttribute("type", "groupchat").withElement("body", null, messageBody),
				userJID, JID.jidInstance(roomJID));
		mucComponent.processPacket(packet);

		Element liveMessage = findByNameAndFrom("message", userNick);
		assertEquals(messageBody, extractBody(liveMessage));
		assertEquals(userOccupantId, extractOccupantId(liveMessage));
		messageId = liveMessage.getChildAttributeStaticStr("stanza-id", "id");
		assertNotNull(messageId);
		writer.clear();
	}

	@Test
	public void test() throws RepositoryException, TigaseStringprepException {
		String reason = "This message contains inappropriate content for this forum";
		Packet packet = Packet.packetInstance(new Element("iq")
			.withAttribute("type", "set")
			.withAttribute("id", UUID.randomUUID().toString())
			.withElement("moderate", "urn:xmpp:message-moderate:1", moderateEl -> {
				moderateEl.setAttribute("id", messageId);
				moderateEl.withElement("retract", "urn:xmpp:message-retract:1", (String) null);
				moderateEl.withElement("reason", "urn:xmpp:message-retract:1", reason);
			}), adminJID, JID.jidInstance(roomJID));
		mucComponent.processPacket(packet);

		Element message = findByNameAndFrom("message", null);
		assertNotNull(message);
		assertNull(extractBody(message));
		Element retract = message.getChild("retract", "urn:xmpp:message-retract:1");
		assertNotNull(retract);
		assertEquals(messageId, retract.getAttribute("id"));
		Element moderated = retract.getChild("moderated", "urn:xmpp:message-moderate:1");
		assertNotNull(moderated);
		assertEquals(JID.jidInstance(roomJID, adminNick).toString(), moderated.getAttribute("by"));
		assertEquals(adminOccupantId, extractOccupantId(moderated));
		assertEquals(reason, retract.getChildCData(el -> "reason".equals(el.getName())));

		writer.clear();

		packet = Packet.packetInstance(new Element("iq").withAttribute("type", "set")
											   .withElement("query", "urn:xmpp:mam:1",
															query -> query.setAttribute("queryid", "f27")), adminJID,
									   JID.jidInstance(roomJID));
		mucComponent.processPacket(packet);

		assertEquals(2, writer.getElements().size());

		List<Element> messages = writer.getElements()
				.stream()
				.filter(it -> "message".equals(it.getName()) && adminJID.toString().equals(it.getAttribute("to")))
				.map(it -> it.findChild(el -> "result".equals(el.getName())))
				.filter(Objects::nonNull)
				.map(it -> it.findChild(el -> "forwarded".equals(el.getName())))
				.filter(Objects::nonNull)
				.map(it -> it.findChild(el -> "message".equals(el.getName())))
				.filter(Objects::nonNull).toList();
		assertEquals(1, messages.size());
		message = messages.get(0);
		assertNull(extractBody(message));
		Element retracted = message.getChild("retracted", "urn:xmpp:message-retract:1");
		assertNotNull(retracted.getAttribute("stamp"));
		assertNotNull(retracted);
		assertNull(retracted.getAttribute("id"));
		moderated = retracted.getChild("moderated", "urn:xmpp:message-moderate:1");
		assertNotNull(moderated);
		assertEquals(adminJID.getBareJID().toString(), moderated.getAttribute("by"));
		assertEquals(adminOccupantId, extractOccupantId(moderated));
		assertEquals(reason, retract.getChildCData(el -> "reason".equals(el.getName())));
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
				.filter(el -> fromNick == null ? (!el.getAttribute("from").contains("/")) : el.getAttribute("from").endsWith("/" + fromNick))
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
		Element body = element.findChild(el -> "body".equals(el.getName()));
		return body == null ? null : body.getCData();
	}
}
