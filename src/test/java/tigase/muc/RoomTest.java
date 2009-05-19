/*
 * Tigase Jabber/XMPP Multi-User Chat Component
 * Copyright (C) 2008 "Bartosz M. Małkowski" <bartosz.malkowski@tigase.org>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. Look for COPYING file in the top folder.
 * If not, see http://www.gnu.org/licenses/.
 *
 * $Rev$
 * Last modified by $Author$
 * $Date$
 */
package tigase.muc;

import java.io.IOException;

import org.junit.Before;

import tigase.muc.repository.RepositoryException;
import tigase.test.junit.JUnitXMLIO;
import tigase.test.junit.XMPPTestCase;
import tigase.xml.Element;
import tigase.xmpp.PacketErrorTypeException;

/**
 * @author bmalkow
 * 
 */
public class RoomTest extends XMPPTestCase {

	private MUCComponent muc;

	private JUnitXMLIO xmlio;

	@Before
	public void init() {
		muc = new MUCComponent();

		xmlio = new JUnitXMLIO() {

			@Override
			public void close() {
				System.out.println("Closed");
				// TODO Auto-generated method stub

			}

			@Override
			public void setIgnorePresence(boolean ignore) {
				System.out.println("Set ignore presence");
			}

			@Override
			public void write(Element data) throws IOException {
				try {
					send(muc.process(data));
				} catch (PacketErrorTypeException e) {
					throw new RuntimeException("", e);
				}
			}
		};

		MucConfig config = new MucConfig();
		muc.setConfig(config);
		config.setServiceName("multi-user-chat");
		config.setLogDirectory("./");
		MockMucRepository mockRepo;
		try {
			mockRepo = new MockMucRepository(config);
			muc.setMucRepository(mockRepo);
			muc.init();

		} catch (RepositoryException e) {
			e.printStackTrace();
		}

	}

	@org.junit.Test
	public void test_pings() {
		test("src/test/scripts/ping.cor", xmlio);
	}

	@org.junit.Test
	public void test_presences() {
		test("src/test/scripts/processPresence-empty.cor", xmlio);
	}
}
