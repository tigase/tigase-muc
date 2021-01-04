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

import tigase.server.Packet;
import tigase.util.stringprep.TigaseStringprepException;
import tigase.xml.Element;
import tigase.xmpp.jid.BareJID;
import tigase.xmpp.jid.JID;

import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

//~--- non-JDK imports --------------------------------------------------------

/**
 * Class description
 *
 * @author Enter your name here...
 * @version Enter version here..., 13/02/16
 */
public class PresenceStore {

	protected static final Logger log = Logger.getLogger(PresenceStore.class.getName());

	/**
	 * Possible presence delivery strategies - either prefering last send presence or the presence with the highest
	 * priority
	 */
	public enum PresenceDeliveryLogic {

		PREFERE_LAST,
		PREFERE_PRIORITY;
	}

	private final Map<BareJID, Presence> bestPresence = new ConcurrentHashMap<>();
	private final Map<JID, Presence> presenceByJid = new ConcurrentHashMap<>();
	private PresenceDeliveryLogic presenceOrdering;
	private Map<BareJID, Map<String, Presence>> presencesMapByBareJid = new ConcurrentHashMap<>();

	public PresenceStore() {
		presenceOrdering = PresenceDeliveryLogic.PREFERE_PRIORITY;
	}

	public PresenceStore(PresenceDeliveryLogic pdl) {
		presenceOrdering = pdl;
	}

	public void clear() {
		presenceByJid.clear();
		bestPresence.clear();
		presencesMapByBareJid.clear();
	}

	// ~--- methods
	// --------------------------------------------------------------

	private Presence findHighPriorityPresence(final BareJID jid) {
		return findPresence(jid, (result, x) -> {
			if ((result == null) || ((x.type == null) && (x.priority > result.priority ||
					(x.priority == result.priority && x.lastUpdated.after(result.lastUpdated))))) {
				return 1;
			} else {
				return -1;
			}

		});
	}

	private Presence findLastPresence(final BareJID jid) {
		return findPresence(jid, (result, x) -> {
			Date l = x.lastUpdated;
			if ((result == null) || ((l.after(result.lastUpdated)) && (x.type == null))) {
				return 1;
			} else {
				return -1;
			}
		});
	}

	private Presence findPresence(final BareJID jid, final Comparator<Presence> c) {
		Map<String, Presence> resourcesPresence = this.presencesMapByBareJid.get(jid);
		Presence result = null;

		if (log.isLoggable(Level.FINEST)) {
			log.log(Level.FINEST, "User resources presences: " + resourcesPresence);
		}

		if (resourcesPresence != null) {
			Iterator<Presence> it = resourcesPresence.values().iterator();

			while (it.hasNext()) {
				Presence x = it.next();
				int r = c.compare(result, x);

				if (r > 0) {
					result = x;
				}

			}
		}
		return result;
	}

	public Collection<JID> getAllKnownJIDs() {
		ArrayList<JID> result = new ArrayList<>();

		for (Entry<JID, Presence> entry : this.presenceByJid.entrySet()) {
			if (entry.getValue().type == null) {
				result.add(entry.getKey());
			}
		}

		return result;
	}

	public Element getBestPresence(final BareJID jid) {
		Presence p = getBestPresenceInt(jid);
		if (p == null) {
			Map<String, Presence> set = presencesMapByBareJid.get(jid);
			if (set != null && !set.isEmpty()) {
				return set.values().iterator().next().element;
			}
		}
		return (p == null) ? null : p.element;
	}

	public Presence getBestPresenceInt(final BareJID jid) {
		return this.bestPresence.get(jid);
	}

	public Element getPresence(final JID jid) {
		Presence p = this.presenceByJid.get(jid);

		return (p == null) ? null : p.element;
	}

	public boolean isAvailable(BareJID jid) {
		Map<String, Presence> resourcesPresence = this.presencesMapByBareJid.get(jid);
		if (log.isLoggable(Level.FINEST)) {
			log.log(Level.FINEST, "resourcesPresence: " + resourcesPresence);
		}

		boolean result = false;

		if (resourcesPresence != null) {
			Iterator<Presence> it = resourcesPresence.values().iterator();

			while (it.hasNext() && !result) {
				Presence x = it.next();

				result = result | x.type == null;
			}
		}

		return result;
	}

	public void remove(final JID from) throws TigaseStringprepException {
		final String resource = (from.getResource() == null) ? "" : from.getResource();

		this.presenceByJid.remove(from);

		Map<String, Presence> m = this.presencesMapByBareJid.get(from.getBareJID());

		if (m != null) {
			m.remove(resource);
			if (m.isEmpty()) {
				this.presencesMapByBareJid.remove(from.getBareJID());
			}
		}
		updateBestPresence(from.getBareJID(), null);
	}

	public void setOrdening(PresenceDeliveryLogic pdl) {
		if (log.isLoggable(Level.FINEST)) {
			log.log(Level.FINEST, "Setting presence delivery logic to: " + pdl);
		}
		presenceOrdering = pdl;
	}

	public void update(final Element presence) throws TigaseStringprepException {
		String f = presence.getAttributeStaticStr(Packet.FROM_ATT);

		if (f == null) {
			return;
		}

		final JID from = JID.jidInstance(f);
		final BareJID bareFrom = from.getBareJID();
		final String resource = (from.getResource() == null) ? "" : from.getResource();
		final Presence p = new Presence(presence);

		if ((p.type != null) && p.type.equals("unavailable")) {
			this.presenceByJid.remove(from);

			Map<String, Presence> m = this.presencesMapByBareJid.get(bareFrom);

			if (m != null) {
				m.remove(resource);
				if (m.isEmpty()) {
					this.presencesMapByBareJid.remove(bareFrom);
				}
			}
		} else {
			this.presenceByJid.put(from, p);

			Map<String, Presence> m = this.presencesMapByBareJid.get(bareFrom);

			if (m == null) {
				m = new ConcurrentHashMap<String, Presence>();
				this.presencesMapByBareJid.put(bareFrom, m);
			}
			m.put(resource, p);

		}
		updateBestPresence(bareFrom, p);
	}

	private void updateBestPresence(final BareJID bareFrom, final Presence currentPresence)
			throws TigaseStringprepException {
		Presence x;

		switch (presenceOrdering) {
			case PREFERE_PRIORITY:
				x = findHighPriorityPresence(bareFrom);
				break;
			case PREFERE_LAST:
				if (currentPresence == null) {
					x = findLastPresence(bareFrom);
				} else {
					x = currentPresence;
				}
				break;
			default:
				throw new RuntimeException("Unknown presenceOrdering");
		}

		if (log.isLoggable(Level.FINEST)) {
			log.finest("Selected BestPresence: " + (x != null ? x.element.toString() : "n/a"));
		}

		if (x == null) {
			this.bestPresence.remove(bareFrom);
		} else {
			this.bestPresence.put(bareFrom, x);
		}
	}

	// ~--- inner classes
	// --------------------------------------------------------

	public class Presence {

		final Element element;
		final JID from;
		final Date lastUpdated;
		final int priority;
		final String show;
		final String type;

		public Presence(Element presence) {
			this.lastUpdated = new Date();
			this.element = presence;
			this.type = presence.getAttributeStaticStr(Packet.TYPE_ATT);
			this.from = JID.jidInstanceNS(presence.getAttributeStaticStr(Packet.FROM_ATT));

			this.show = presence.getChildCDataStaticStr(tigase.server.Presence.PRESENCE_SHOW_PATH);

			String p = presence.getChildCDataStaticStr(tigase.server.Presence.PRESENCE_PRIORITY_PATH);
			int x = 0;

			try {
				x = Integer.parseInt(p);
			} catch (Exception e) {
			}
			this.priority = x;
		}

		public JID getFrom() {
			return from;
		}

		public Element getElement() {
			return element;
		}

		public Date getLastUpdated() {
			return lastUpdated;
		}

		public int getPriority() {
			return priority;
		}

		public String getShow() {
			return show;
		}

		@Override
		public String toString() {
			return "Presence[" + "priority=" + priority + ", type=" + type + ", show=" + show + ", from=" + from +
					", lastUpdated=" + lastUpdated + "]";
		}
	}
}
