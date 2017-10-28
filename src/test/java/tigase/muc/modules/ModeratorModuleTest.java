/*
 * ModeratorModuleTest.java
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

package tigase.muc.modules;

import org.junit.Assert;
import org.junit.Test;

import tigase.muc.Affiliation;
import tigase.muc.Role;
import tigase.muc.exceptions.MUCException;
import tigase.xml.Element;
import tigase.xmpp.Authorization;
import tigase.xmpp.jid.JID;

public class ModeratorModuleTest {

	private final ModeratorModule m = new ModeratorModule();

	@Test
	public void testCheckItem() throws Exception {
		Element item = new Element("item", new String[] { "jid" }, new String[] { "occupand@b.c" });

		try {
			// A:OWNER changes affiliation: none->admin
			m.checkItem(item, "occupant", Affiliation.none, null, Affiliation.admin, JID.jidInstanceNS("sender@b.c/res"), Role.none, Affiliation.owner);
		} catch (MUCException e) {
			Assert.fail("Invalid result: " + e.getMessage());
		}

		try {
			// A:ADMIN changes affiliation: none->member
			m.checkItem(item, "occupant", Affiliation.none, null, Affiliation.member, JID.jidInstanceNS("sender@b.c/res"), Role.none, Affiliation.admin);
		} catch (MUCException e) {
			Assert.fail("Invalid result: " + e.getMessage());
		}

		try {
			// A:ADMIN tries to change affiliation: none->admin
			m.checkItem(item, "occupant", Affiliation.none, null, Affiliation.admin, JID.jidInstanceNS("sender@b.c/res"), Role.none, Affiliation.admin);
			Assert.fail();
		} catch (MUCException e) {
			Assert.assertEquals(Authorization.NOT_ALLOWED, e.getErrorCondition());
			System.out.println("OK: " + e.getMessage());
		}

		try {
			// A:NONE tries to change affiliation: none->admin
			m.checkItem(item, "occupant", Affiliation.none, null, Affiliation.admin, JID.jidInstanceNS("sender@b.c/res"), Role.none, Affiliation.none);
			Assert.fail();
		} catch (MUCException e) {
			Assert.assertEquals(Authorization.NOT_ALLOWED, e.getErrorCondition());
			System.out.println("OK: " + e.getMessage());
		}

		try {
			// A:NONE tries to set role to: moderator
			m.checkItem(item, "occupant", Affiliation.none, Role.moderator, null, JID.jidInstanceNS("sender@b.c/res"), Role.none, Affiliation.none);
			Assert.fail();
		} catch (MUCException e) {
			Assert.assertEquals(Authorization.NOT_ALLOWED, e.getErrorCondition());
			System.out.println("OK: " + e.getMessage());
		}

	}
}