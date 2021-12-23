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

import org.junit.Assert;
import org.junit.Before;
import tigase.component.DSLBeanConfigurator;
import tigase.component.PacketWriter;
import tigase.component.exceptions.RepositoryException;
import tigase.component.responses.AsyncCallback;
import tigase.component.responses.ResponseManager;
import tigase.conf.ConfigWriter;
import tigase.conf.ConfigurationException;
import tigase.db.beans.DataSourceBean;
import tigase.eventbus.EventBusFactory;
import tigase.kernel.DefaultTypesConverter;
import tigase.kernel.beans.config.AbstractBeanConfigurator;
import tigase.kernel.core.Kernel;
import tigase.server.CmdAcl;
import tigase.server.Packet;
import tigase.test.junit.JUnitXMLIO;
import tigase.test.junit.XMPPTestCase;
import tigase.util.stringprep.TigaseStringprepException;
import tigase.xml.Element;
import tigase.xmpp.jid.BareJID;
import tigase.xmpp.jid.JID;

import java.io.IOException;
import java.util.*;

/**
 * @author bmalkow
 */
public class RoomTest
		extends XMPPTestCase {

	private TestMUCCompoent muc;
	private JUnitXMLIO xmlio;

	@Before
	public void init() throws RepositoryException, TigaseStringprepException, ConfigurationException {
		final Kernel kernel = new Kernel();
		kernel.registerBean(DefaultTypesConverter.class).exportable().exec();
		kernel.registerBean(AbstractBeanConfigurator.DEFAULT_CONFIGURATOR_NAME)
				.asClass(DSLBeanConfigurator.class)
				.exportable()
				.exec();
		Map<String, Object> props = new HashMap();
		props.put("muc/" + "multi-user-chat", BareJID.bareJIDInstance("multi-user-chat"));
		props.put("muc/" + MUCConfig.MESSAGE_FILTER_ENABLED_KEY, Boolean.TRUE);
		props.put("muc/" + MUCConfig.PRESENCE_FILTER_ENABLED_KEY, Boolean.FALSE);
		props.put("muc/" + MUCConfig.LOG_DIR_KEY, "./");
		props = ConfigWriter.buildTree(props);
		kernel.getInstance(DSLBeanConfigurator.class).setProperties(props);
		kernel.registerBean("eventBus").asInstance(EventBusFactory.getInstance()).exportable().exec();
		kernel.registerBean("dataSourceBean").asClass(DataSourceBean.class).exportable().exec();
		kernel.registerBean("mucRepository").asInstance(new MockMucRepository()).exportable().exec();

		kernel.registerBean("muc").asClass(TestMUCCompoent.class).exec();
		kernel.registerBean("mucConfig").asInstance(new MUCConfig()).exportable().exec();
		final MUCConfig config = kernel.getInstance(MUCConfig.class);
		config.setHiddenRoomCreationAcl(CmdAcl.Type.ALL);
		config.setPublicRoomCreationAcl(CmdAcl.Type.ALL);
		this.muc = kernel.getInstance(TestMUCCompoent.class);

		ResponseManager rm = ((Kernel) kernel.getInstance("muc#KERNEL")).getInstance(ResponseManager.class);

		final ArrayWriter writer = new ArrayWriter(rm);
		((Kernel) kernel.getInstance("muc#KERNEL")).registerBean("writer").asInstance(writer).exec();

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
					p.setXMLNS(Packet.CLIENT_XMLNS);
					muc.processPacket(p);
					send(writer.getElements());
				} catch (TigaseStringprepException e) {
					e.printStackTrace();
				}
			}
		};

	}

	@org.junit.Test
	public void test_destroyRoom() {
		test("src/test/scripts/destroying-room.cor", xmlio);
		try {
			Room room = muc.getMucRepository().getRoom(BareJID.bareJIDInstance("darkcave@macbeth.shakespeare.lit"));
			Assert.assertNull("Room should be destroyed", room);
		} catch (Exception e) {
			Assert.fail(e.getMessage());
		}
	}

	@org.junit.Test
	public void test_getting_members_list() {
		test("src/test/scripts/gettingMembersList-ext.cor", xmlio);
	}

	@org.junit.Test
	public void test_ghostUser() {
		test("src/test/scripts/ghostUser.cor", xmlio);
	}

	@org.junit.Test
	public void test_mucSelfPing() {
		test("src/test/scripts/selfPing.cor", xmlio);
	}

	@org.junit.Test
	public void test_hiddenRoomProblem() {
		test("src/test/scripts/hidden-room-problem.cor", xmlio);
	}

	@org.junit.Test
	public void test_invitations_allowed() {
		test("src/test/scripts/invitation_allowed.cor", xmlio);
	}

	@org.junit.Test
	public void test_invitations_allowed_membersonly() {
		test("src/test/scripts/invitation_allowed_membersonly.cor", xmlio);
	}

	@org.junit.Test
	public void test_invitations_notallowed() {
		test("src/test/scripts/invitation_not_allowed.cor", xmlio);
	}

	@org.junit.Test
	public void test_invitations_notallowed_membersonly() {
		test("src/test/scripts/invitation_not_allowed_membersonly.cor", xmlio);
	}

	@org.junit.Test
	public void test_members_only_subject() {
		test("src/test/scripts/members-only-subject.cor", xmlio);
	}

	@org.junit.Test
	public void test_messages() {
		test("src/test/scripts/messagesGroupchat.cor", xmlio);
	}

	@org.junit.Test
	public void test_nonpersistentRoomProblem() {
		test("src/test/scripts/nonpersistent-room-problem.cor", xmlio);
		try {
			Room room = muc.getMucRepository().getRoom(BareJID.bareJIDInstance("darkcave@macbeth.shakespeare.lit"));
			Assert.assertNull("Room should be destroyed", room);
		} catch (Exception e) {
			e.printStackTrace();
			Assert.fail(e.getMessage());
		}
	}

	@org.junit.Test
	public void test_pings() {
		test("src/test/scripts/ping.cor", xmlio);
	}

	@org.junit.Test
	public void test_presences2() {
		test("src/test/scripts/processPresence2.cor", xmlio);
	}

	@org.junit.Test
	public void test_presences2_exiting() throws Exception {
		test("src/test/scripts/processPresence2-exiting.cor", xmlio);
		Room room = muc.getMucRepository().getRoom(BareJID.bareJIDInstanceNS("darkcave@macbeth.shakespeare.lit"));
		Assert.assertNotNull(room);

		Collection<String> nicknames = room.getOccupantsNicknames(false);
		Assert.assertEquals(1, nicknames.size());
		Assert.assertTrue(nicknames.contains("firstwitch"));

		Assert.assertEquals("firstwitch",
							room.getOccupantsNickname(JID.jidInstanceNS("crone1@shakespeare.lit/desktop")));

		Assert.assertEquals(BareJID.bareJIDInstance("crone1@shakespeare.lit"),
							room.getOccupantsJidByNickname("firstwitch"));

	}

	@org.junit.Test
	public void test_presences2_non_anonymous() {
		test("src/test/scripts/processPresence2-nonanonymous.cor", xmlio);
	}

	@org.junit.Test
	public void test_room_config() {
		test("src/test/scripts/room-configuration.cor", xmlio);
	}

	static class ArrayWriter
			implements PacketWriter {

		private final ArrayList<Element> elements = new ArrayList<Element>();
		private final ResponseManager responseManager;

		public ArrayWriter(ResponseManager responseManager) {
			this.responseManager = responseManager;
		}

		public void clear() {
			elements.clear();
		}

		public ArrayList<Element> getElements() {
			return elements;
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
		public void write(Packet packet, AsyncCallback callback) {
			responseManager.registerResponseHandler(packet, ResponseManager.DEFAULT_TIMEOUT, callback);
			write(packet);
		}

	}
}
