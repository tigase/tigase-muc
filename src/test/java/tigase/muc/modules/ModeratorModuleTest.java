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

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import tigase.component.DSLBeanConfigurator;
import tigase.component.exceptions.RepositoryException;
import tigase.conf.ConfigWriter;
import tigase.db.beans.DataSourceBean;
import tigase.eventbus.EventBusFactory;
import tigase.kernel.DefaultTypesConverter;
import tigase.kernel.beans.config.AbstractBeanConfigurator;
import tigase.kernel.core.Kernel;
import tigase.muc.*;
import tigase.muc.exceptions.MUCException;
import tigase.muc.repository.IMucRepository;
import tigase.muc.utils.ArrayWriter;
import tigase.util.stringprep.TigaseStringprepException;
import tigase.xml.Element;
import tigase.xmpp.Authorization;
import tigase.xmpp.jid.BareJID;
import tigase.xmpp.jid.JID;

import java.util.HashMap;
import java.util.Map;

public class ModeratorModuleTest extends AbstractMucTest {

	private final ModeratorModule m = new ModeratorModule();
	private JID admin;
	private JID member;
	private ModeratorModule moderatorModule;
	private MUCComponent mucComponent;
	private Room room;

	@Override
	protected void registerBeans(Kernel kernel) {
		super.registerBeans(kernel);
		Map<String, Object> props = new HashMap();
		props.put("muc/" + "multi-user-chat", BareJID.bareJIDInstanceNS("multi-user-chat"));
		props.put("muc/" + MUCConfig.MESSAGE_FILTER_ENABLED_KEY, Boolean.TRUE);
		props.put("muc/" + MUCConfig.PRESENCE_FILTER_ENABLED_KEY, Boolean.FALSE);
		props.put("muc/" + MUCConfig.LOG_DIR_KEY, "./");
		props = ConfigWriter.buildTree(props);
		kernel.getInstance(DSLBeanConfigurator.class).setProperties(props);

		admin = JID.jidInstanceNS("admin@example.com/res1");
		member = JID.jidInstanceNS("member@example.com/res1");

		try {
			room = getMucKernel().getInstance(IMucRepository.class)
					.createNewRoom(BareJID.bareJIDInstanceNS("darkcave@macbeth.shakespeare.lit"), admin);

			room.addAffiliationByJid(admin.getBareJID(), RoomAffiliation.admin);
			room.addAffiliationByJid(member.getBareJID(), RoomAffiliation.member);

		moderatorModule = new ModeratorModule();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}

	}


	@Test
	public void testCheckItem() throws Exception {
		Element item = new Element("item", new String[]{"jid"}, new String[]{"occupand@b.c"});

		try {
			// A:OWNER changes affiliation: none->admin
			m.checkItem(item, "occupant", Affiliation.none, null, Affiliation.admin,
						JID.jidInstanceNS("sender@b.c/res"), Role.none, Affiliation.owner);
		} catch (MUCException e) {
			Assert.fail("Invalid result: " + e.getMessage());
		}

		try {
			// A:ADMIN changes affiliation: none->member
			m.checkItem(item, "occupant", Affiliation.none, null, Affiliation.member,
						JID.jidInstanceNS("sender@b.c/res"), Role.none, Affiliation.admin);
		} catch (MUCException e) {
			Assert.fail("Invalid result: " + e.getMessage());
		}

		try {
			// A:ADMIN tries to change affiliation: none->admin
			m.checkItem(item, "occupant", Affiliation.none, null, Affiliation.admin,
						JID.jidInstanceNS("sender@b.c/res"), Role.none, Affiliation.admin);
			Assert.fail();
		} catch (MUCException e) {
			Assert.assertEquals(Authorization.NOT_ALLOWED, e.getErrorCondition());
			System.out.println("OK: " + e.getMessage());
		}

		try {
			// A:NONE tries to change affiliation: none->admin
			m.checkItem(item, "occupant", Affiliation.none, null, Affiliation.admin,
						JID.jidInstanceNS("sender@b.c/res"), Role.none, Affiliation.none);
			Assert.fail();
		} catch (MUCException e) {
			Assert.assertEquals(Authorization.NOT_ALLOWED, e.getErrorCondition());
			System.out.println("OK: " + e.getMessage());
		}

		try {
			// A:NONE tries to set role to: moderator
			m.checkItem(item, "occupant", Affiliation.none, Role.moderator, null, JID.jidInstanceNS("sender@b.c/res"),
						Role.none, Affiliation.none);
			Assert.fail();
		} catch (MUCException e) {
			Assert.assertEquals(Authorization.NOT_ALLOWED, e.getErrorCondition());
			System.out.println("OK: " + e.getMessage());
		}

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
	public void checkModifyVoiceItemRoleWithoutNickAndWithAffiliation() throws Exception {
		final Element item1 = new Element("item", new String[]{"role", "affiliation"},
										  new String[]{Role.none.toString(), Affiliation.owner.toString()});
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