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

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import tigase.component.exceptions.ComponentException;
import tigase.component.exceptions.RepositoryException;
import tigase.kernel.core.Kernel;
import tigase.muc.AbstractMucTest;
import tigase.muc.PermissionChecker;
import tigase.muc.TestMUCCompoent;
import tigase.server.Packet;
import tigase.util.stringprep.TigaseStringprepException;
import tigase.vhosts.DummyVHostManager;
import tigase.xml.Element;
import tigase.xmpp.jid.BareJID;
import tigase.xmpp.jid.JID;

import java.util.Optional;
import java.util.Set;

public class PresenceModuleImplTest
		extends AbstractMucTest {

	private PermissionChecker permissionChecker;

	private final String vhost = "tigase.org";
	private final String componentName = "muc";
	private final String mucDomain = componentName + "." + vhost;

	private final JID regularUserJid = JID.jidInstanceNS("wojtek", vhost, "test");
	private final JID domainAdminJID = JID.jidInstanceNS("domain_admin", vhost, "resource");
	private final JID adminJID = JID.jidInstanceNS("admin", vhost, "resource");
	final BareJID roomJID = BareJID.bareJIDInstanceNS("room", componentName + "." + vhost);
	private PresenceModule presenceModule;
	private TestMUCCompoent mucComponent;
	private final static String[] bodyPath = {"message", "body"};

	@Override
	protected void registerBeans(Kernel kernel) {
		super.registerBeans(kernel);
		kernel.registerBean(PresenceModuleImpl.class).exec();
		mucComponent = getKernel().getInstance(TestMUCCompoent.class);
		mucComponent.setAdmins(Set.of(adminJID.getBareJID()));

		final DummyVHostManager vHostManager = getKernel().getInstance(
				DummyVHostManager.class);
		vHostManager.addVhost(vhost);
		vHostManager.getVHostItem(vhost).setAdmins(new String[]{domainAdminJID.getBareJID().toString()});
		permissionChecker = getMucKernel().getInstance(PermissionChecker.class);
		presenceModule = getMucKernel().getInstance(PresenceModule.class);
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testDefaultPermissionsRegularUser()
			throws RepositoryException, ComponentException, TigaseStringprepException {
		var presence = new Element("presence");
		presence.setAttribute("to", roomJID + "/thirdwitch");
		presence.setAttribute("from", regularUserJid.toString());
		presence.addChild(new Element("x", new String[]{"xmlns"}, new String[]{"http://jabber.org/protocol/muc"}));
		var packet = Packet.packetInstance(presence);
		presenceModule.process(packet);
		final boolean new_hidden_room = writer.getElements()
				.stream()
				.map(message -> Optional.ofNullable(message.getCData(bodyPath)))
				.flatMap(Optional::stream)
				.anyMatch(body -> body.contains("You've created new hidden room"));
		Assert.assertTrue("Missing message about hidden room creation", new_hidden_room);

		var publicVisibleRooms = mucComponent.getMucRepository().getPublicVisibleRooms(mucDomain);
		Assert.assertEquals(Set.of(), publicVisibleRooms.keySet());

		var room = mucComponent.getMucRepository().getRoom(roomJID);
		Assert.assertNotNull(room);
		Assert.assertNotNull(room.getConfig());
		Assert.assertFalse(room.getConfig().isRoomconfigPublicroom());
	}

	@Test
	public void testDefaultPermissionsDomainAdmin()
			throws RepositoryException, ComponentException, TigaseStringprepException {
		var presence = new Element("presence");
		presence.setAttribute("to", roomJID + "/thirdwitch");
		presence.setAttribute("from", domainAdminJID.toString());
		presence.addChild(new Element("x", new String[]{"xmlns"}, new String[]{"http://jabber.org/protocol/muc"}));
		var packet = Packet.packetInstance(presence);
		presenceModule.process(packet);
		final boolean new_hidden_room = writer.getElements()
				.stream()
				.map(message -> Optional.ofNullable(message.getCData(bodyPath)))
				.flatMap(Optional::stream)
				.anyMatch(body -> body.contains("You've created new public room"));
		Assert.assertTrue("Missing message about public room creation", new_hidden_room);

		var publicVisibleRooms = mucComponent.getMucRepository().getPublicVisibleRooms(mucDomain);
		Assert.assertEquals(Set.of(roomJID), publicVisibleRooms.keySet());

		var room = mucComponent.getMucRepository().getRoom(roomJID);
		Assert.assertNotNull(room);
		Assert.assertNotNull(room.getConfig());
		Assert.assertTrue(room.getConfig().isRoomconfigPublicroom());
	}

	@Test
	public void testDefaultPermissionsSystemAdmin()
			throws RepositoryException, ComponentException, TigaseStringprepException {
		var presence = new Element("presence");
		presence.setAttribute("to", roomJID + "/thirdwitch");
		presence.setAttribute("from", adminJID.toString());
		presence.addChild(new Element("x", new String[]{"xmlns"}, new String[]{"http://jabber.org/protocol/muc"}));
		var packet = Packet.packetInstance(presence);
		presenceModule.process(packet);
		final boolean new_hidden_room = writer.getElements()
				.stream()
				.map(message -> Optional.ofNullable(message.getCData(bodyPath)))
				.flatMap(Optional::stream)
				.anyMatch(body -> body.contains("You've created new public room"));
		Assert.assertTrue("Missing message about public room creation", new_hidden_room);

		var publicVisibleRooms = mucComponent.getMucRepository().getPublicVisibleRooms(mucDomain);
		Assert.assertEquals(Set.of(roomJID), publicVisibleRooms.keySet());

		var room = mucComponent.getMucRepository().getRoom(roomJID);
		Assert.assertNotNull(room);
		Assert.assertNotNull(room.getConfig());
		Assert.assertTrue(room.getConfig().isRoomconfigPublicroom());
	}

}