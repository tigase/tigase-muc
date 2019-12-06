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

import tigase.xmpp.jid.JID;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Request {

	public enum Result {
		Ok,
		Error,
		Timeout
	}

	private final String id;
	private final JID jid;
	private final JID jidTo;
	private final SelfPingerMonitor monitor;
	private final Map<String, JID> subrequests = new ConcurrentHashMap<>();
	private final long timestampt = System.currentTimeMillis();
	private long requestsCounter = 0;
	private long resultErrorCounter = 0;
	private long resultOkCounter = 0;
	private long resultTimeoutCounter = 0;

	public Request(SelfPingerMonitor monitor, JID jid, JID to, String id) {
		this.monitor = monitor;
		this.jid = jid;
		this.jidTo = to;
		this.id = id;
	}

	public JID getJidTo() {
		return jidTo;
	}

	public long getResultErrorCounter() {
		return resultErrorCounter;
	}

	public long getResultOkCounter() {
		return resultOkCounter;
	}

	public long getNoResultCounter() {
		return this.subrequests.size();
	}

	public String getId() {
		return id;
	}

	public JID getJid() {
		return jid;
	}

	public long getTimestampt() {
		return timestampt;
	}

	public void registerRequest(JID jid, String stanzaId) {
		final String key = SelfPingerMonitor.key(jid, stanzaId);
		this.subrequests.put(key, jid);
		monitor.registerSubRequest(key, this);
		++requestsCounter;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof Request)) {
			return false;
		}

		Request request = (Request) o;

		if (!id.equals(request.id)) {
			return false;
		}
		return jid.equals(request.jid);
	}

	@Override
	public int hashCode() {
		int result = id.hashCode();
		result = 31 * result + jid.hashCode();
		return result;
	}

	public Collection<JID> getTimeoutedJIDs() {
		return Collections.unmodifiableCollection(this.subrequests.values());
	}

	public void registerResponse(JID jid, String stanzaId, Result result) {
		final String key = SelfPingerMonitor.key(jid, stanzaId);
		if (this.subrequests.containsKey(key)) {
			switch (result) {
				case Ok:
					++resultOkCounter;
					this.subrequests.remove(key);
					break;
				case Error:
					++resultErrorCounter;
					this.subrequests.remove(key);
					monitor.kickOut(jid);
					break;
				case Timeout:
					++resultTimeoutCounter;
					monitor.kickOut(jid);
					break;
			}
		}
		if (this.subrequests.isEmpty() ||
				(resultOkCounter + resultErrorCounter + resultTimeoutCounter == requestsCounter)) {
			monitor.finish(this);
		}
	}

}
