/*
 * AbstractHistoryProviderTest.java
 *
 * Tigase Jabber/XMPP Multi-User Chat Component
 * Copyright (C) 2004-2017 "Tigase, Inc." <office@tigase.com>
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
package tigase.muc.history;

import org.junit.After;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import tigase.component.PacketWriter;
import tigase.component.exceptions.ComponentException;
import tigase.component.exceptions.RepositoryException;
import tigase.component.responses.AsyncCallback;
import tigase.db.*;
import tigase.kernel.core.Kernel;
import tigase.muc.Affiliation;
import tigase.muc.Room;
import tigase.muc.RoomConfig;
import tigase.server.Packet;
import tigase.xml.Element;
import tigase.xmpp.jid.BareJID;
import tigase.xmpp.jid.JID;
import tigase.xmpp.mam.MAMRepository;
import tigase.xmpp.mam.Query;

import java.util.*;
import java.util.stream.IntStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Created by andrzej on 16.10.2016.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public abstract class AbstractHistoryProviderTest<DS extends DataSource>
		extends AbstractDataSourceAwareTestCase<DS, HistoryProvider> {

	protected static Date creationDate = null;
	protected static JID creatorJID = JID.jidInstanceNS(UUID.randomUUID().toString(), "test.local",
														UUID.randomUUID().toString());
	protected static String emoji = "\uD83D\uDE97\uD83D\uDCA9\uD83D\uDE21";
	protected static Room room;
	protected static BareJID roomJID = BareJID.bareJIDInstanceNS(UUID.randomUUID().toString(), "muc.test.local");
	protected static List<Item> savedMessages = new ArrayList<>();
	protected static String uri = System.getProperty("testDbUri");
	protected boolean checkEmoji = true;
	protected HistoryProvider historyProvider;
	protected Room.RoomFactory roomFactory;

	@Before
	public void setup() throws RepositoryException, DBInitException, IllegalAccessException, InstantiationException {
		roomFactory = getInstance(Room.RoomFactory.class);
		historyProvider = getInstance(HistoryProvider.class);
	}

	@After
	public void tearDown() {
		historyProvider = null;
	}

	@Test
	public void test1_createRoom() throws RepositoryException {
		RoomConfig rc = new RoomConfig(roomJID);
		rc.setValue(RoomConfig.MUC_ROOMCONFIG_PERSISTENTROOM_KEY, Boolean.TRUE);
		creationDate = new Date();
		room = roomFactory.newInstance(null, rc, creationDate, creatorJID.getBareJID());
		room.addAffiliationByJid(creatorJID.getBareJID(), Affiliation.owner);
	}

	@Test
	public void test2_appendMessages() throws RepositoryException, InterruptedException {
		for (int i = 0; i < 10; i++) {
			Item item = new Item(checkEmoji ? emoji : "");
			historyProvider.addMessage(room, item.getMessage(room.getRoomJID()), item.body, item.sender, item.nick,
									   item.ts);
			savedMessages.add(item);
			Thread.sleep(1000);
		}

		QueueWriter writer = new QueueWriter();
		historyProvider.getHistoryMessages(room, creatorJID, 0, 10, null, null, writer);

		assertEquals(10, writer.queue.size());

		for (int i = 0; i < savedMessages.size(); i++) {
			Item item = savedMessages.get(i);
			Packet msg = writer.queue.poll();
			assertNotNull(msg);
			String body = msg.getElemCDataStaticStr(new String[]{"message", "body"});
			assertEquals(item.body, body);
			assertEquals(msg.getStanzaFrom().getResource(), item.nick);
		}
	}

	@Test
	public void test3_getMessagesSince() throws RepositoryException, InterruptedException {
		Date since = savedMessages.get(4).ts;

		QueueWriter writer = new QueueWriter();
		historyProvider.getHistoryMessages(room, creatorJID, 0, 10, null, since, writer);

		assertEquals(6, writer.queue.size());

		for (int i = 4; i < savedMessages.size(); i++) {
			Item item = savedMessages.get(i);
			Packet msg = writer.queue.poll();
			assertNotNull(msg);
			String body = msg.getElemCDataStaticStr(new String[]{"message", "body"});
			assertEquals(item.body, body);
			assertEquals(msg.getStanzaFrom().getResource(), item.nick);
		}
	}

	@Test
	public void test4_getMessagesMaxStanzas() throws RepositoryException, InterruptedException {
		QueueWriter writer = new QueueWriter();
		historyProvider.getHistoryMessages(room, creatorJID, 0, 5, null, null, writer);

		assertEquals(5, writer.queue.size());

		for (int i = 5; i < savedMessages.size(); i++) {
			Item item = savedMessages.get(i);
			Packet msg = writer.queue.poll();
			assertNotNull(msg);
			String body = msg.getElemCDataStaticStr(new String[]{"message", "body"});
			assertEquals(item.body, body);
			assertEquals(msg.getStanzaFrom().getResource(), item.nick);
		}
	}

	@Test
	public void test4_mam_retrieveAll() throws RepositoryException, ComponentException, TigaseDBException {
		if (historyProvider instanceof MAMRepository) {
			MAMRepository mamRepository = (MAMRepository) historyProvider;
			Query query = mamRepository.newQuery();

			query.setComponentJID(JID.jidInstance(roomJID));
			query.setQuestionerJID(creatorJID);

			List<MAMRepository.Item> items = new ArrayList<>();

			mamRepository.queryItems(query, (query1, item) -> {
				items.add(item);
			});

			assertEquals(savedMessages.size(), items.size());
			assertEquals(savedMessages.size(), query.getRsm().getCount().intValue());

			IntStream.range(0, savedMessages.size() - 1).forEach(pos -> {
				assertEquals(savedMessages.get(pos).body,
							 items.get(pos).getMessage().getChildCData(new String[]{"message", "body"}));
			});
		}
	}

	@Test
	public void test4_mam_retrieveBetween() throws RepositoryException, ComponentException, TigaseDBException {
		if (historyProvider instanceof MAMRepository) {
			Date since = savedMessages.get(4).ts;
			Date until = savedMessages.get(8).ts;
			MAMRepository mamRepository = (MAMRepository) historyProvider;
			Query query = mamRepository.newQuery();

			query.setComponentJID(JID.jidInstance(roomJID));
			query.setQuestionerJID(creatorJID);
			query.setStart(since);
			query.setEnd(until);
			query.getRsm().setMax(2);

			List<MAMRepository.Item> items = new ArrayList<>();

			mamRepository.queryItems(query, (query1, item) -> {
				items.add(item);
			});

			assertEquals(2, items.size());
			assertEquals(5, query.getRsm().getCount().intValue());

			Arrays.asList(0, 1).stream().forEach(pos -> {
				assertEquals(savedMessages.get(4 + pos).body,
							 items.get(pos).getMessage().getChildCData(new String[]{"message", "body"}));
			});
		}
	}

	@Test
	public void test4_mam_retrieveAfter() throws RepositoryException, ComponentException, TigaseDBException {
		if (historyProvider instanceof MAMRepository) {
			Date since = savedMessages.get(4).ts;
			Date until = savedMessages.get(8).ts;
			MAMRepository mamRepository = (MAMRepository) historyProvider;
			Query query = mamRepository.newQuery();

			query.setComponentJID(JID.jidInstance(roomJID));
			query.setQuestionerJID(creatorJID);
			query.setStart(since);
			query.setEnd(until);
			query.getRsm().setMax(2);

			List<MAMRepository.Item> items = new ArrayList<>();

			mamRepository.queryItems(query, (query1, item) -> {
				items.add(item);
			});

			assertEquals(2, items.size());
			assertEquals(5, query.getRsm().getCount().intValue());
			assertEquals(0, query.getRsm().getIndex().intValue());

			Arrays.asList(0, 1).stream().forEach(pos -> {
				assertEquals(savedMessages.get(4 + pos).body,
							 items.get(pos).getMessage().getChildCData(new String[]{"message", "body"}));
			});

			String id = items.get(0).getId();
			String expId = items.get(1).getId();
			items.clear();
			query.getRsm().setAfter(id);
			mamRepository.queryItems(query, (query1, item) -> {
				items.add(item);
			});

			assertEquals(2, items.size());
			assertEquals(5, query.getRsm().getCount().intValue());
			assertEquals(1, query.getRsm().getIndex().intValue());

			assertEquals(expId, items.get(0).getId());
		}
	}

	@Test
	public void test5_deleteMessages() throws RepositoryException {
		historyProvider.removeHistory(room);

		QueueWriter writer = new QueueWriter();
		historyProvider.getHistoryMessages(room, creatorJID, 0, 10, null, null, writer);

		assertEquals(0, writer.queue.size());
	}

	@Test
	public void test6_destroyRoom() {
		room = null;
	}

	@Override
	protected Class<? extends DataSourceAware> getDataSourceAwareIfc() {
		return HistoryProvider.class;
	}

	@Override
	protected void registerBeans(Kernel kernel) {
		super.registerBeans(kernel);
		kernel.registerBean(Room.RoomFactoryImpl.class).exec();
	}

	public static class Item {

		public final String body;
		public final String nick = UUID.randomUUID().toString();
		public final JID sender = JID.jidInstanceNS(UUID.randomUUID().toString(), "test.local",
													UUID.randomUUID().toString());
		public final Date ts = new Date();

		public Item(String suffix) {
			body = UUID.randomUUID().toString() + suffix;
		}

		public Element getMessage(BareJID roomJID) {
			Element message = new Element("message", new String[]{"type", "to", "from"},
										  new String[]{"groupchat", roomJID.toString(), sender.toString()});
			message.addChild(new Element("body", body));
			return message;
		}
	}

	public static class QueueWriter
			implements PacketWriter {

		public final Queue<Packet> queue = new ArrayDeque<>();

		@Override
		public void write(Collection<Packet> packets) {
			queue.addAll(packets);
		}

		@Override
		public void write(Packet packet) {
			queue.add(packet);
		}

		@Override
		public void write(Packet packet, AsyncCallback callback) {
			throw new UnsupportedOperationException();
		}
	}
}
