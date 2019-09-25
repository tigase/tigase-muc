/*
 *  Tigase MUC - Multi User Chat component for Tigase
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
package tigase.muc.modules.selfping;

import org.junit.Assert;
import org.junit.Test;
import tigase.xmpp.jid.JID;

public class SelfPingerMonitorTest {

	@Test
	public void registerAndAllSuccessResult() throws Exception {
		final SelfPingerMonitor mrm = new SelfPingerMonitor();

		final SelfPingerMonitor.ResultStatus[] result = new SelfPingerMonitor.ResultStatus[]{null};

		final Request req = mrm.register(JID.jidInstance("sender@b.c/100"), JID.jidInstance("sender@room/nick"), "1");
		mrm.setHandler((req1, resultStatus) -> result[0] = resultStatus);

		req.registerRequest(JID.jidInstance("a@b.c/1"), "1");
		req.registerRequest(JID.jidInstance("b@b.c/1"), "2");
		req.registerRequest(JID.jidInstance("c@b.c/1"), "3");
		req.registerRequest(JID.jidInstance("d@b.c/1"), "4");

		req.registerResponse(JID.jidInstance("a@b.c/1"), "1", Request.Result.Ok);
		req.registerResponse(JID.jidInstance("b@b.c/1"), "2", Request.Result.Ok);
		req.registerResponse(JID.jidInstance("c@b.c/1"), "3", Request.Result.Ok);
		req.registerResponse(JID.jidInstance("d@b.c/1"), "4", Request.Result.Ok);

		Assert.assertEquals("In this place it should be AllSuccess", SelfPingerMonitor.ResultStatus.AllSuccess,
							result[0]);
	}

	@Test
	public void registerAndAllSuccessResultByMonitor() throws Exception {
		final SelfPingerMonitor mrm = new SelfPingerMonitor();

		final SelfPingerMonitor.ResultStatus[] result = new SelfPingerMonitor.ResultStatus[]{null};

		final Request req = mrm.register(JID.jidInstance("sender@b.c/100"), JID.jidInstance("sender@room/nick"), "1");
		mrm.setHandler((req1, resultStatus) -> result[0] = resultStatus);

		req.registerRequest(JID.jidInstance("a@b.c/1"), "1");
		req.registerRequest(JID.jidInstance("b@b.c/1"), "2");
		req.registerRequest(JID.jidInstance("c@b.c/1"), "3");
		req.registerRequest(JID.jidInstance("d@b.c/1"), "4");

		mrm.registerResponse(JID.jidInstance("a@b.c/1"), "1", Request.Result.Ok);
		mrm.registerResponse(JID.jidInstance("b@b.c/1"), "2", Request.Result.Ok);
		mrm.registerResponse(JID.jidInstance("c@b.c/1"), "3", Request.Result.Ok);
		mrm.registerResponse(JID.jidInstance("d@b.c/1"), "4", Request.Result.Ok);

		Assert.assertEquals("In this place it should be AllSuccess", SelfPingerMonitor.ResultStatus.AllSuccess,
							result[0]);
	}

	@Test
	public void registerAndErrorsResult() throws Exception {
		final SelfPingerMonitor mrm = new SelfPingerMonitor();

		final SelfPingerMonitor.ResultStatus[] result = new SelfPingerMonitor.ResultStatus[]{null};

		final Request req = mrm.register(JID.jidInstance("sender@b.c/100"), JID.jidInstance("sender@room/nick"), "1");
		mrm.setHandler((req1, resultStatus) -> result[0] = resultStatus);

		req.registerRequest(JID.jidInstance("a@b.c/1"), "1");
		req.registerRequest(JID.jidInstance("b@b.c/1"), "2");
		req.registerRequest(JID.jidInstance("c@b.c/1"), "3");
		req.registerRequest(JID.jidInstance("d@b.c/1"), "4");

		req.registerResponse(JID.jidInstance("a@b.c/1"), "1", Request.Result.Ok);
		req.registerResponse(JID.jidInstance("b@b.c/1"), "2", Request.Result.Error);
		req.registerResponse(JID.jidInstance("c@b.c/1"), "3", Request.Result.Ok);
		req.registerResponse(JID.jidInstance("d@b.c/1"), "4", Request.Result.Error);

		Assert.assertEquals("In this place it should be AllSuccess", SelfPingerMonitor.ResultStatus.Errors, result[0]);
	}

	@Test
	public void registerAndTimeoutsResult() throws Exception {
		final SelfPingerMonitor mrm = new SelfPingerMonitor();

		final SelfPingerMonitor.ResultStatus[] result = new SelfPingerMonitor.ResultStatus[]{null};

		final Request req = mrm.register(JID.jidInstance("sender@b.c/100"), JID.jidInstance("sender@room/nick"), "1");
		mrm.setHandler((req1, resultStatus) -> result[0] = resultStatus);

		req.registerRequest(JID.jidInstance("a@b.c/1"), "1");
		req.registerRequest(JID.jidInstance("b@b.c/1"), "2");
		req.registerRequest(JID.jidInstance("c@b.c/1"), "3");
		req.registerRequest(JID.jidInstance("d@b.c/1"), "4");

		req.registerResponse(JID.jidInstance("a@b.c/1"), "1", Request.Result.Ok);
		req.registerResponse(JID.jidInstance("b@b.c/1"), "2", Request.Result.Error);
		req.registerResponse(JID.jidInstance("c@b.c/1"), "3", Request.Result.Ok);
		req.registerResponse(JID.jidInstance("d@b.c/1"), "4", Request.Result.Timeout);

		Assert.assertEquals("In this place it should be AllSuccess", SelfPingerMonitor.ResultStatus.Timeouts,
							result[0]);
	}
}