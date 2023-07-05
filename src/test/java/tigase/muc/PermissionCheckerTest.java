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

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import tigase.component.exceptions.RepositoryException;
import tigase.kernel.core.Kernel;
import tigase.muc.exceptions.MUCException;
import tigase.server.CmdAcl;
import tigase.vhosts.DummyVHostManager;
import tigase.xmpp.jid.BareJID;
import tigase.xmpp.jid.JID;

import java.lang.reflect.Field;
import java.util.Set;

import static tigase.muc.PermissionChecker.ROOM_VISIBILITY_PERMISSION;

public class PermissionCheckerTest
		extends AbstractMucTest {

	private PermissionChecker permissionChecker;

	private final String vhost = "tigase.org";
	private final String componentName = "muc";
	private final JID regularUserJid = JID.jidInstanceNS("wojtek", vhost, "test");
	private final JID domainAdminJID = JID.jidInstanceNS("domain_admin", vhost, "resource");
	private final JID adminJID = JID.jidInstanceNS("admin", vhost, "resource");
	final BareJID roomJID = BareJID.bareJIDInstanceNS("room", componentName + "." + vhost);

	@Override
	protected void registerBeans(Kernel kernel) {
		super.registerBeans(kernel);
		TestMUCCompoent mucComponent = kernel.getInstance(TestMUCCompoent.class);
		mucComponent.setAdmins(Set.of(adminJID.getBareJID()));

		final DummyVHostManager vHostManager = kernel.getInstance(DummyVHostManager.class);
		vHostManager.addVhost(vhost);
		vHostManager.getVHostItem(vhost).setAdmins(new String[]{domainAdminJID.getBareJID().toString()});
		permissionChecker = getMucKernel().getInstance(PermissionChecker.class);
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testDefaultPermissionsRegularUser() throws RepositoryException, MUCException {
		ROOM_VISIBILITY_PERMISSION permission = permissionChecker.getCreateRoomPermission(roomJID, regularUserJid);
		Assert.assertEquals(ROOM_VISIBILITY_PERMISSION.HIDDEN, permission);
	}

	@Test
	public void testDefaultPermissionsRegularDomainAdmin() throws RepositoryException, MUCException {
		ROOM_VISIBILITY_PERMISSION permission = permissionChecker.getCreateRoomPermission(roomJID, domainAdminJID);
		Assert.assertEquals(ROOM_VISIBILITY_PERMISSION.PUBLIC, permission);
	}

	@Test
	public void testDefaultPermissionsRegularAdmin() throws RepositoryException, MUCException {
		ROOM_VISIBILITY_PERMISSION permission = permissionChecker.getCreateRoomPermission(roomJID, adminJID);
		Assert.assertEquals(ROOM_VISIBILITY_PERMISSION.PUBLIC, permission);
	}

	@Test(expected = MUCException.class)
	public void testDisabledACLRegular() throws RepositoryException, MUCException {
		setPublicACL(CmdAcl.Type.NONE);
		setHiddenACL(CmdAcl.Type.NONE);

		ROOM_VISIBILITY_PERMISSION permission = permissionChecker.getCreateRoomPermission(roomJID, regularUserJid);
		Assert.assertEquals(ROOM_VISIBILITY_PERMISSION.PUBLIC, permission);
	}

	@Test(expected = MUCException.class)
	public void testDisabledACLDomainAdmin() throws RepositoryException, MUCException {
		setPublicACL(CmdAcl.Type.NONE);
		setHiddenACL(CmdAcl.Type.NONE);

		ROOM_VISIBILITY_PERMISSION permission = permissionChecker.getCreateRoomPermission(roomJID, domainAdminJID);
		Assert.assertEquals(ROOM_VISIBILITY_PERMISSION.PUBLIC, permission);
	}

	@Test(expected = MUCException.class)
	public void testDisabledACLAdmin() throws RepositoryException, MUCException {
		setPublicACL(CmdAcl.Type.NONE);
		setHiddenACL(CmdAcl.Type.NONE);

		ROOM_VISIBILITY_PERMISSION permission = permissionChecker.getCreateRoomPermission(roomJID, adminJID);
		Assert.assertEquals(ROOM_VISIBILITY_PERMISSION.PUBLIC, permission);
	}

	@Test
	public void testDisabledACLAdminHidden() throws RepositoryException, MUCException {
		setPublicACL(CmdAcl.Type.NONE);
		setHiddenACL(CmdAcl.Type.ADMIN);

		ROOM_VISIBILITY_PERMISSION permission = permissionChecker.getCreateRoomPermission(roomJID, adminJID);
		Assert.assertEquals(ROOM_VISIBILITY_PERMISSION.HIDDEN, permission);
	}

	private void setPublicACL(CmdAcl.Type acl) {
		MUCConfig config = getMucKernel().getInstance(MUCConfig.class);
		setACL("publicRoomCreationAcl", config, acl);
	}

	private void setHiddenACL(CmdAcl.Type acl) {
		MUCConfig config = getMucKernel().getInstance(MUCConfig.class);
		setACL("hiddenRoomCreationAcl", config, acl);
	}

	private void setACL(String field, MUCConfig config, CmdAcl.Type acl) {
		try {
			final Field publicRoomCreationAcl = config.getClass().getDeclaredField(field);
			publicRoomCreationAcl.setAccessible(true);
			publicRoomCreationAcl.set(config, acl);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
}