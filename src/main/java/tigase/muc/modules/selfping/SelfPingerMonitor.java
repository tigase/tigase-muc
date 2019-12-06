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
package tigase.muc.modules.selfping;

import tigase.component.ScheduledTask;
import tigase.kernel.beans.Bean;
import tigase.kernel.beans.Inject;
import tigase.muc.Ghostbuster2;
import tigase.muc.MUCComponent;
import tigase.xmpp.jid.JID;

import java.time.Duration;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Bean(name = "self-pinger-monitor", parent = MUCComponent.class, active = true)
public class SelfPingerMonitor
		extends ScheduledTask {

	public enum ResultStatus {
		AllSuccess,
		Errors,
		Timeouts
	}

	private final Set<Request> requests = new HashSet<>();
	private final Map<String, Request> sentSubrequests = new ConcurrentHashMap<>();
	@Inject(nullAllowed = true)
	private Ghostbuster2 ghostbuster2;
	private Handler handler;

	static String key(JID jidInstance, String stanzaId) {
		return jidInstance.toString() + ":" + stanzaId;
	}

	public SelfPingerMonitor() {
		super(Duration.ofMinutes(1), Duration.ofMinutes(1));
	}

	public Request register(JID jidFrom, JID jidTo, String id) {
		Request r = new Request(this, jidFrom, jidTo, id);
		synchronized (requests) {
			requests.add(r);
		}
		return r;
	}

	public void setHandler(Handler handler) {
		this.handler = handler;
	}

	public void registerResponse(JID jid, String stanzaId, Request.Result result) {
		final String key = SelfPingerMonitor.key(jid, stanzaId);
		Request req = this.sentSubrequests.get(key);
		if (req != null) {
			req.registerResponse(jid, stanzaId, result);
		}
	}

	@Override
	public void run() {
		final HashSet<Request> toFinish = new HashSet<>();
		final long tm = System.currentTimeMillis() - 1000 * 45;
		synchronized (this.requests) {
			this.requests.forEach(req -> {
				if (req.getTimestampt() <= tm) {
					toFinish.add(req);
				}
			});
		}
		toFinish.forEach(this::finish);
	}

	void kickOut(JID jid) {
		try {
			if (ghostbuster2 != null) {
				ghostbuster2.kickJIDFromRooms(jid, null);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	void registerSubRequest(String key, Request req) {
		this.sentSubrequests.put(key, req);
	}

	void finish(final Request request) {
		synchronized (requests) {
			requests.remove(request);
		}
		final long err = request.getResultErrorCounter();
		final long oks = request.getResultOkCounter();
		final long tms = request.getNoResultCounter();

		ResultStatus st;
		if (tms > 0) {
			st = ResultStatus.Timeouts;
		} else if (err > 0) {
			st = ResultStatus.Errors;
		} else {
			st = ResultStatus.AllSuccess;
		}
		handler.finished(request, st);
		request.getTimeoutedJIDs().forEach(this::kickOut);
	}

	public interface Handler {

		void finished(Request req, ResultStatus resultStatus);

	}

}
