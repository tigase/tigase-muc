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
import org.junit.Test;
import tigase.component.exceptions.ComponentException;
import tigase.criteria.Criteria;
import tigase.db.TigaseDBException;
import tigase.kernel.core.Kernel;
import tigase.muc.AbstractMucTest;
import tigase.muc.Affiliation;
import tigase.muc.exceptions.MUCException;
import tigase.server.Packet;
import tigase.server.rtbl.RTBLRepository;
import tigase.util.Algorithms;
import tigase.util.stringprep.TigaseStringprepException;
import tigase.xmpp.jid.BareJID;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

public class AbstractMucModuleTest extends AbstractMucTest {

	private static final BareJID RTBL_JID = BareJID.bareJIDInstanceNS("test@rtbl-test.tigase");
	private static final String RTBL_NODE = "muc_ban";

	private MUCModuleTest module;
	private RTBLRepository rtblRepository;

	@Override
	protected void registerBeans(Kernel kernel) {
		super.registerBeans(kernel);
		kernel.registerBean(RTBLRepository.class).exec();
		getMucKernel().registerBean("mucModuleTest").asClass(MUCModuleTest.class).exec();

		rtblRepository = kernel.getInstance(RTBLRepository.class);
		try {
			rtblRepository.add(RTBL_JID, RTBL_NODE, "SHA-256");
		} catch (TigaseDBException ex) {
			throw new RuntimeException(ex);
		}

		module = getMucKernel().getInstance(MUCModuleTest.class);
	}

	@Test
	public void testEmptyList() throws MUCException {
		BareJID userJid = BareJID.bareJIDInstanceNS(UUID.randomUUID().toString(), "test.tigase");
		module.validateRTBL(userJid, Affiliation.none);
	}

	@Test
	public void testBlockedJid() throws MUCException, InterruptedException {
		BareJID userJid = BareJID.bareJIDInstanceNS(UUID.randomUUID().toString(), "test.tigase");
		rtblRepository.update(RTBL_JID, RTBL_NODE, RTBLRepository.Action.add, sha256(userJid.toString()));
		Thread.sleep(10);
		Assert.assertThrows(MUCException.class, () -> module.validateRTBL(userJid, Affiliation.none));
		rtblRepository.update(RTBL_JID, RTBL_NODE, RTBLRepository.Action.remove, sha256(userJid.toString()));
		Thread.sleep(10);
		module.validateRTBL(userJid, Affiliation.none);
	}

	@Test
	public void testBlockedDomain() throws MUCException, InterruptedException {
		BareJID userJid = BareJID.bareJIDInstanceNS(UUID.randomUUID().toString(), "test.tigase");
		rtblRepository.update(RTBL_JID, RTBL_NODE, RTBLRepository.Action.add, sha256(userJid.getDomain()));
		Thread.sleep(10);
		Assert.assertThrows(MUCException.class, () -> module.validateRTBL(userJid, Affiliation.none));
		rtblRepository.update(RTBL_JID, RTBL_NODE, RTBLRepository.Action.remove, sha256(userJid.getDomain()));
		Thread.sleep(10);
		module.validateRTBL(userJid, Affiliation.none);
	}

	private static String sha256(String text) {
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			return Algorithms.bytesToHex(md.digest(text.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		}
	}

	public static class MUCModuleTest
			extends AbstractMucModule {

		@Override
		public Criteria getModuleCriteria() {
			return null;
		}

		@Override
		public void process(Packet packet) throws ComponentException, TigaseStringprepException {

		}
	}
}
