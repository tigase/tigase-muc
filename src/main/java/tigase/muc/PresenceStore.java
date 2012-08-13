package tigase.muc;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import tigase.util.TigaseStringprepException;
import tigase.xml.Element;
import tigase.xmpp.BareJID;
import tigase.xmpp.JID;

public class PresenceStore {

	private class Presence {

		final Element element;

		final int priority;

		final String type;

		/**
		 * @param presence
		 */
		public Presence(Element presence) {
			this.element = presence;
			this.type = presence.getAttribute("type");
			String p = presence.getChildCData("/presence/priority");
			int x = 0;
			try {
				x = Integer.parseInt(p);
			} catch (Exception e) {
			}
			this.priority = x;
		}

	}

	private Map<BareJID, Presence> bestPresence = new ConcurrentHashMap<BareJID, Presence>();

	private Map<JID, Presence> presenceByJid = new ConcurrentHashMap<JID, Presence>();

	private Map<BareJID, Map<String, Presence>> presencesMapByBareJid = new ConcurrentHashMap<BareJID, Map<String, Presence>>();

	public void clear() {
		presenceByJid.clear();
		bestPresence.clear();
		presencesMapByBareJid.clear();
	}

	public Element getBestPresence(final BareJID jid) {
		Presence p = this.bestPresence.get(jid);
		return p == null ? null : p.element;
	}

	public Element getPresence(final JID jid) {
		Presence p = this.presenceByJid.get(jid);
		return p == null ? null : p.element;
	}

	private Presence intGetBestPresence(final BareJID jid) {
		Map<String, Presence> resourcesPresence = this.presencesMapByBareJid.get(jid);
		Presence result = null;
		if (resourcesPresence != null) {
			Iterator<Presence> it = resourcesPresence.values().iterator();
			while (it.hasNext()) {
				Presence x = it.next();
				Integer p = x.priority;
				if (result == null || p >= result.priority && x.type == null) {
					result = x;
				}
			}
		}
		return result;
	}

	public boolean isAvailable(BareJID jid) {
		Map<String, Presence> resourcesPresence = this.presencesMapByBareJid.get(jid);
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

	/**
	 * @param jid
	 * @throws TigaseStringprepException
	 */
	public void remove(final JID from) throws TigaseStringprepException {
		final String resource = from.getResource() == null ? "" : from.getResource();

		this.presenceByJid.remove(from);
		Map<String, Presence> m = this.presencesMapByBareJid.get(from.getBareJID());
		if (m != null) {
			m.remove(resource);
			if (m.isEmpty())
				this.presencesMapByBareJid.remove(from.getBareJID());
		}

		updateBestPresence(from.getBareJID());
	}

	public void update(final Element presence) throws TigaseStringprepException {
		String f = presence.getAttribute("from");
		if (f == null)
			return;
		final JID from = JID.jidInstance(f);
		final BareJID bareFrom = from.getBareJID();
		final String resource = from.getResource() == null ? "" : from.getResource();

		final Presence p = new Presence(presence);

		if (p.type != null && p.type.equals("unavailable")) {
			this.presenceByJid.remove(from);
			Map<String, Presence> m = this.presencesMapByBareJid.get(bareFrom);
			if (m != null) {
				m.remove(resource);
				if (m.isEmpty())
					this.presencesMapByBareJid.remove(bareFrom);
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
		updateBestPresence(bareFrom);
	}

	private void updateBestPresence(final BareJID bareFrom) throws TigaseStringprepException {
		Presence x = intGetBestPresence(bareFrom);
		if (x == null) {
			this.bestPresence.remove(bareFrom);
		} else {
			this.bestPresence.put(bareFrom, x);
		}
	}
}
