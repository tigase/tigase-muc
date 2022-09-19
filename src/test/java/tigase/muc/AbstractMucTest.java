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

import tigase.component.DSLBeanConfigurator;
import tigase.component.exceptions.RepositoryException;
import tigase.conf.ConfigWriter;
import tigase.db.DataSourceHelper;
import tigase.db.Repository;
import tigase.db.beans.DataSourceBean;
import tigase.db.xml.XMLDataSource;
import tigase.db.xml.XMLRepository;
import tigase.eventbus.EventBusFactory;
import tigase.kernel.AbstractKernelTestCase;
import tigase.kernel.DefaultTypesConverter;
import tigase.kernel.beans.config.AbstractBeanConfigurator;
import tigase.kernel.core.Kernel;
import tigase.muc.repository.AbstractMucDAO;
import tigase.muc.repository.IMucDAO;
import tigase.muc.utils.ArrayWriter;
import tigase.server.xmppserver.S2SConnManTest;
import tigase.xml.db.DBElement;
import tigase.xml.db.NodeNotFoundException;
import tigase.xml.db.XMLDB;
import tigase.xmpp.jid.BareJID;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public class AbstractMucTest
		extends AbstractKernelTestCase {

	protected ArrayWriter writer;

	protected final Kernel getMucKernel() {
		return ((Kernel) getKernel().getInstance("muc#KERNEL"));
	}

	@Override
	protected void registerBeans(Kernel kernel) {
		super.registerBeans(kernel);
		kernel.registerBean(DefaultTypesConverter.class).exportable().exec();
		kernel.registerBean(AbstractBeanConfigurator.DEFAULT_CONFIGURATOR_NAME)
				.asClass(DSLBeanConfigurator.class)
				.exportable()
				.exec();

		DSLBeanConfigurator config = kernel.getInstance(DSLBeanConfigurator.class);
		Map<String, Object> props = new HashMap<>();
		props.put("name", "muc");
		props.put("statistics", "false");
		props = ConfigWriter.buildTree(props);
		kernel.getInstance(DSLBeanConfigurator.class).setProperties(props);

		kernel.registerBean("eventBus").asInstance(EventBusFactory.getInstance()).exportable().exec();
		kernel.registerBean("vHostManager")
				.asClass(S2SConnManTest.DummyVHostManager.class)
				.exportable()
				.setActive(true)
				.exec();
		try {
			String xmlRepositoryURI = "memory://xmlRepo?autoCreateUser=true";
			kernel.registerBean("default").asClass(XMLDataSource.class).exportable().exec();
			XMLDataSource xmlDataSource = kernel.getInstance(XMLDataSource.class);
			xmlDataSource.initialize(xmlRepositoryURI);

			kernel.registerBean("instance").asClass(XMLRepository.class).exportable().exec();
			XMLRepository repository = kernel.getInstance(XMLRepository.class);
			repository.setDataSource(xmlDataSource);

			Class<IMucDAO> daoClass = DataSourceHelper.getDefaultClass(IMucDAO.class, xmlRepositoryURI);
			kernel.registerBean("muc-dao").asClass(daoClass).exportable().exec();
			kernel.registerBean(DataSourceBean.class).exportable().exec();
			DataSourceBean dataSourceBean = kernel.getInstance(DataSourceBean.class);
			dataSourceBean.addRepo("default", xmlDataSource);
			IMucDAO<XMLDataSource, Long> iMucDAO = kernel.getInstance("muc-dao");
			iMucDAO.setDataSource(xmlDataSource);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}

		kernel.registerBean("muc").asClass(TestMUCCompoent.class).exportable().exec();
		kernel.getInstance(TestMUCCompoent.class);
		getMucKernel().registerBean(PermissionChecker.class).exec();

		writer = new ArrayWriter();
		getMucKernel().registerBean("writer").asInstance(writer).exec();

	}

	@Repository.Meta(supportedUris = {"memory://.*"}, isDefault = true)
	public static class MucAbstractMucDAO
			extends AbstractMucDAO<XMLDataSource, Long> {

		AtomicLong id = new AtomicLong(0);
		XMLDB xmldb;

		public MucAbstractMucDAO() {
		}

		@Override
		public Long createRoom(RoomWithId<Long> room) throws RepositoryException {
			try {
				xmldb.addNode1(room.getRoomJID().toString());
				xmldb.setData(room.getRoomJID().toString(), "room", room);
				xmldb.setData(room.getRoomJID().toString(), "config", room.getConfig());
				return id.getAndIncrement();
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}

		@Override
		public void destroyRoom(BareJID roomJID) throws RepositoryException {
			try {
				xmldb.removeNode1(roomJID.toString());
			} catch (NodeNotFoundException e) {
				throw new RuntimeException(e);
			}
		}

		final String affiliations_key = "affiliations";

		@Override
		public Map<BareJID, RoomAffiliation> getAffiliations(RoomWithId<Long> room) throws RepositoryException {
			final DBElement node1 = xmldb.findNode1(room.getRoomJID().toString());
			try {
				final String[] JIDS = xmldb.getKeys(room.getRoomJID().toString(), affiliations_key);
				final HashMap<BareJID, RoomAffiliation> affiliations = new HashMap<>(JIDS.length);
				for (String jid : JIDS) {
					final RoomAffiliation affiliation = (RoomAffiliation) xmldb.getData(room.getRoomJID().toString(),
																						affiliations_key, jid);
					affiliations.put(BareJID.bareJIDInstanceNS(jid), affiliation);
				}
				return affiliations;
			} catch (NodeNotFoundException e) {
				throw new RuntimeException(e);
			}

		}

		@Override
		public RoomWithId<Long> getRoom(BareJID roomJID) throws RepositoryException {
			try {
				return (RoomWithId<Long>) xmldb.getData(roomJID.toString(), "room");
			} catch (NodeNotFoundException e) {
				return null;
			}
		}

		@Override
		public List<BareJID> getRoomsJIDList() throws RepositoryException {
			return xmldb.getAllNode1s().stream().map(BareJID::bareJIDInstanceNS).toList();
		}

		@Override
		public void setAffiliation(RoomWithId<Long> room, BareJID jid, RoomAffiliation affiliation)
				throws RepositoryException {
			try {
				xmldb.setData(room.getRoomJID().toString(), affiliations_key, jid.toString(), affiliation);
			} catch (NodeNotFoundException e) {
				throw new RuntimeException(e);
			}

		}

		@Override
		public String getRoomAvatar(RoomWithId<Long> room) throws RepositoryException {
			throw new UnsupportedOperationException();
		}

		@Override
		public void setDataSource(XMLDataSource dataSource) throws RepositoryException {
			xmldb = dataSource.getXMLDB();
		}

		@Override
		public void updateRoomAvatar(RoomWithId<Long> room, String encodedAvatar, String hash)
				throws RepositoryException {
			throw new UnsupportedOperationException();
		}

		@Override
		public void setSubject(RoomWithId<Long> room, String subject, String creatorNickname, Date changeDate)
				throws RepositoryException {
			throw new UnsupportedOperationException();
		}

		@Override
		public void updateRoomConfig(RoomConfig roomConfig) throws RepositoryException {
			throw new UnsupportedOperationException();
		}
	}
}
