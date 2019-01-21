/**
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

import org.junit.Before;
import tigase.component.DSLBeanConfigurator;
import tigase.component.PacketWriter;
import tigase.component.exceptions.RepositoryException;
import tigase.component.responses.AsyncCallback;
import tigase.conf.ConfigWriter;
import tigase.conf.ConfigurationException;
import tigase.db.beans.DataSourceBean;
import tigase.eventbus.EventBusFactory;
import tigase.kernel.DefaultTypesConverter;
import tigase.kernel.beans.config.AbstractBeanConfigurator;
import tigase.kernel.core.Kernel;
import tigase.server.Packet;
import tigase.test.junit.JUnitXMLIO;
import tigase.test.junit.XMPPTestCase;
import tigase.util.stringprep.TigaseStringprepException;
import tigase.xml.Element;
import tigase.xmpp.jid.BareJID;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * @author bmalkow
 */
public class PresenceTestNAMultiPresenceTest
		extends XMPPTestCase {

	private MUCComponent pubsub;
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

		final ArrayWriter writer = new ArrayWriter();
		kernel.registerBean("muc").asClass(TestMUCCompoent.class).exec();
		this.pubsub = kernel.getInstance(TestMUCCompoent.class);
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
					pubsub.processPacket(p);
					send(writer.elements);
				} catch (TigaseStringprepException e) {
					e.printStackTrace();
				}
			}
		};

	}

	@org.junit.Test
	public void test_presences2_non_anonymous() {
		test("src/test/scripts/processPresence2-nonanonymous-multipresence.cor", xmlio);
	}

	private final class ArrayWriter
			implements PacketWriter {

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
		public void write(Packet packet, AsyncCallback callback) {
			write(packet);
		}

	}
}
