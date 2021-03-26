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

import org.junit.Test;
import tigase.xmpp.jid.BareJID;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RoomConfigTest {

	@Test
	public void testCompareAndStatusGeneration() throws Exception {
		RoomConfig rc1 = new RoomConfig(BareJID.bareJIDInstance("a@b"));
		RoomConfig rc2 = new RoomConfig(BareJID.bareJIDInstance("a@b"));
		assertEquals(0, rc2.calculateStatusCodesByDiff(rc1).length);

		rc2.setValues(RoomConfig.MUC_ROOMCONFIG_WHOIS_KEY, new String[]{"anyone"});
		assertEquals(1, rc2.calculateStatusCodesByDiff(rc1).length);
		assertEquals(Integer.valueOf(172), rc2.calculateStatusCodesByDiff(rc1)[0]);

		rc1.copyFrom(rc2);

		rc2.setValues(RoomConfig.MUC_ROOMCONFIG_WHOIS_KEY, new String[]{"moderators"});
		assertEquals(1, rc2.calculateStatusCodesByDiff(rc1).length);
		assertEquals(Integer.valueOf(173), rc2.calculateStatusCodesByDiff(rc1)[0]);

		rc2.setValues(RoomConfig.MUC_ROOMCONFIG_ENABLELOGGING_KEY, new String[]{"1"});
		List<Integer> codes = Arrays.asList(rc2.calculateStatusCodesByDiff(rc1));
		assertEquals(2, codes.size());
		assertTrue(codes.contains(173));
		assertTrue(codes.contains(170));
	}

}