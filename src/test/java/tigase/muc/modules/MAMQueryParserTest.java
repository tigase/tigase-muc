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

import org.junit.Test;
import tigase.component.exceptions.ComponentException;
import tigase.xmpp.mam.Query;
import tigase.xmpp.mam.QueryImpl;

import java.util.Date;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class MAMQueryParserTest {

	private final MAMQueryParser mamQueryParser = new MAMQueryParser();

	@Test
	public void testHandlingOldMessageIds_After() throws ComponentException {
		Query query = new QueryImpl();
		Date after = new Date();
		query.getRsm().setAfter(String.valueOf(after.getTime()));
		mamQueryParser.handleOldIds(query);
		assertNull(query.getRsm().getAfter());
		assertEquals(after, query.getStart());
	}

	@Test
	public void testHandlingOldMessageIds_AfterAndStart() throws ComponentException {
		Query query = new QueryImpl();
		Date after = new Date();
		Date start = new Date(after.getTime() - 10 * 1000);
		query.setStart(start);
		query.getRsm().setAfter(String.valueOf(after.getTime()));
		mamQueryParser.handleOldIds(query);
		assertNull(query.getRsm().getAfter());
		assertEquals(after, query.getStart());
	}

	@Test
	public void testHandlingOldMessageIds_AfterAndStart2() throws ComponentException {
		Query query = new QueryImpl();
		Date after = new Date();
		Date start = new Date(after.getTime() + 10 * 1000);
		query.setStart(start);
		query.getRsm().setAfter(String.valueOf(after.getTime()));
		mamQueryParser.handleOldIds(query);
		assertNull(query.getRsm().getAfter());
		assertEquals(start, query.getStart());
	}
	
	@Test
	public void testHandlingOldMessageIds_Before() throws ComponentException {
		Query query = new QueryImpl();
		Date before = new Date();
		query.getRsm().setBefore(String.valueOf(before.getTime()));
		mamQueryParser.handleOldIds(query);
		assertNull(query.getRsm().getBefore());
		assertEquals(before, query.getEnd());
	}

	@Test
	public void testHandlingOldMessageIds_BeforeAndEnd() throws ComponentException {
		Query query = new QueryImpl();
		Date before = new Date();
		Date end = new Date(before.getTime() + 10 * 1000);
		query.setEnd(end);
		query.getRsm().setBefore(String.valueOf(before.getTime()));
		mamQueryParser.handleOldIds(query);
		assertNull(query.getRsm().getBefore());
		assertEquals(before, query.getEnd());
	}

	@Test
	public void testHandlingOldMessageIds_BeforeAndEnd2() throws ComponentException {
		Query query = new QueryImpl();
		Date before = new Date();
		Date end = new Date(before.getTime() - 10 * 1000);
		query.setEnd(end);
		query.getRsm().setBefore(String.valueOf(before.getTime()));
		mamQueryParser.handleOldIds(query);
		assertNull(query.getRsm().getBefore());
		assertEquals(end, query.getEnd());
	}

}
