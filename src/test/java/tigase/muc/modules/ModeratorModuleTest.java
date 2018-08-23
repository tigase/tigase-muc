/*
 * Tigase Jabber/XMPP Server
 *  Copyright (C) 2004-2018 "Tigase, Inc." <office@tigase.com>
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published by
 *  the Free Software Foundation, either version 3 of the License,
 *  or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program. Look for COPYING file in the top folder.
 *  If not, see http://www.gnu.org/licenses/.
 *
 */

package tigase.muc.modules;

import org.junit.Before;
import org.junit.Test;
import tigase.component.exceptions.RepositoryException;
import tigase.muc.*;
import tigase.muc.exceptions.MUCException;
import tigase.muc.utils.ArrayWriter;
import tigase.util.TigaseStringprepException;
import tigase.xml.Element;
import tigase.xmpp.BareJID;
import tigase.xmpp.JID;

public class ModeratorModuleTest {

	private JID admin;
	private JID member;
	private ModeratorModule moderatorModule;
	private MUCComponent mucComponent;
	private Room room;

	@Before
	public void init() throws RepositoryException, TigaseStringprepException {
		final ArrayWriter writer = new ArrayWriter();
		this.mucComponent = new TestMUCCompoent(writer, new MockMucRepository());
		this.mucComponent.setName("muc");
		admin = JID.jidInstance("admin@example.com/res1");

		member = JID.jidInstance("member@example.com/res1");

		room = mucComponent.getMucRepository()
				.createNewRoom(BareJID.bareJIDInstance("darkcave@macbeth.shakespeare.lit"), admin);
		room.addAffiliationByJid(admin.getBareJID(), Affiliation.admin);
		room.addAffiliationByJid(member.getBareJID(), Affiliation.member);

		moderatorModule = new ModeratorModule();
	}

	@Test(expected = MUCException.class)
	public void checkEmptyItem() throws Exception {
		final Element item = new Element("item");
		moderatorModule.checkItem(room, item, admin, Affiliation.admin, Role.moderator);
	}

	// 8.5 Modifying the Voice List : https://xmpp.org/extensions/xep-0045.html#modifyvoice
	@Test(expected = MUCException.class)
	public void checkModifyVoiceItemRoleWithoutNick() throws Exception {
		final Element item1 = new Element("item", new String[]{"jid", "role"},
										  new String[]{"member@example.com", Role.participant.toString()});
		moderatorModule.checkItem(room, item1, admin, Affiliation.admin, Role.moderator);
	}

	@Test(expected = MUCException.class)
	public void checkModifyVoiceItemRoleOnly() throws Exception {
		final Element item1 = new Element("item", new String[]{"role"}, new String[]{Role.participant.toString()});
		moderatorModule.checkItem(room, item1, admin, Affiliation.admin, Role.moderator);
	}

	@Test(expected = MUCException.class)
	public void checkModifyOwnerItemAffiliationWithoutJid() throws Exception {
		final Element item1 = new Element("item", new String[]{"nick", "affiliation"},
										  new String[]{"member_user", Affiliation.owner.toString()});
		moderatorModule.checkItem(room, item1, admin, Affiliation.admin, Role.moderator);
	}

	@Test(expected = MUCException.class)
	public void checkModifyOwnerItemAffiliationOnly() throws Exception {
		final Element item1 = new Element("item", new String[]{"affiliation"},
										  new String[]{Affiliation.owner.toString()});
		moderatorModule.checkItem(room, item1, admin, Affiliation.admin, Role.moderator);
	}

	@Test
	public void checkModifyVoiceItem() throws Exception {
		final Element item1 = new Element("item", new String[]{"nick", "role"},
										  new String[]{"member_user", Role.participant.toString()});
		moderatorModule.checkItem(room, item1, admin, Affiliation.admin, Role.moderator);
	}

	@Test
	public void checkModifyOwnerItem() throws Exception {
		final Element item1 = new Element("item", new String[]{"jid", "affiliation"},
										  new String[]{"member@example.com", Affiliation.owner.toString()});
		moderatorModule.checkItem(room, item1, admin, Affiliation.admin, Role.moderator);
	}
}