/*
 * PresenceStore.java
 *
 * Tigase Jabber/XMPP Server
 * Copyright (C) 2004-2012 "Artur Hefczyc" <artur.hefczyc@tigase.org>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. Look for COPYING file in the top folder.
 * If not, see http://www.gnu.org/licenses/.
 *
 */



package tigase.muc;

//~--- non-JDK imports --------------------------------------------------------

import tigase.server.Packet;

import tigase.util.TigaseStringprepException;

import tigase.xml.Element;

import tigase.xmpp.BareJID;
import tigase.xmpp.JID;

//~--- JDK imports ------------------------------------------------------------

import java.util.concurrent.ConcurrentHashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Class description
 *
 *
 * @version        Enter version here..., 13/02/16
 * @author         Enter your name here...
 */
public class PresenceStore {
	private Map<BareJID, Presence> bestPresence = new ConcurrentHashMap<BareJID,
																									Presence>();
	private Map<JID, Presence> presenceByJid                          =
		new ConcurrentHashMap<JID, Presence>();
	private Map<BareJID, Map<String, Presence>> presencesMapByBareJid =
		new ConcurrentHashMap<BareJID, Map<String, Presence>>();

	//~--- methods --------------------------------------------------------------

	/**
	 * Method description
	 *
	 */
	public void clear() {
		presenceByJid.clear();
		bestPresence.clear();
		presencesMapByBareJid.clear();
	}

	//~--- get methods ----------------------------------------------------------

	/**
	 * Method description
	 *
	 *
	 * @param jid
	 *
	 * @return
	 */
	public Element getBestPresence(final BareJID jid) {
		Presence p = this.bestPresence.get(jid);

		return (p == null)
					 ? null
					 : p.element;
	}

	/**
	 * Method description
	 *
	 *
	 * @param jid
	 *
	 * @return
	 */
	public Element getPresence(final JID jid) {
		Presence p = this.presenceByJid.get(jid);

		return (p == null)
					 ? null
					 : p.element;
	}

	//~--- methods --------------------------------------------------------------

	private Presence intGetBestPresence(final BareJID jid) {
		Map<String, Presence> resourcesPresence = this.presencesMapByBareJid.get(jid);
		Presence result                         = null;

		if (resourcesPresence != null) {
			Iterator<Presence> it = resourcesPresence.values().iterator();

			while (it.hasNext()) {
				Presence x = it.next();
				Integer p  = x.priority;

				if ((result == null) || ((p >= result.priority) && (x.type == null))) {
					result = x;
				}
			}
		}

		return result;
	}

	//~--- get methods ----------------------------------------------------------

	/**
	 * Method description
	 *
	 *
	 * @param jid
	 *
	 * @return
	 */
	public boolean isAvailable(BareJID jid) {
		Map<String, Presence> resourcesPresence = this.presencesMapByBareJid.get(jid);
		boolean result                          = false;

		if (resourcesPresence != null) {
			Iterator<Presence> it = resourcesPresence.values().iterator();

			while (it.hasNext() &&!result) {
				Presence x = it.next();

				result = result | x.type == null;
			}
		}

		return result;
	}

	//~--- methods --------------------------------------------------------------

	/**
	 *
	 * @param from
	 * @throws TigaseStringprepException
	 */
	public void remove(final JID from) throws TigaseStringprepException {
		final String resource = (from.getResource() == null)
														? ""
														: from.getResource();

		this.presenceByJid.remove(from);

		Map<String, Presence> m = this.presencesMapByBareJid.get(from.getBareJID());

		if (m != null) {
			m.remove(resource);
			if (m.isEmpty()) {
				this.presencesMapByBareJid.remove(from.getBareJID());
			}
		}
		updateBestPresence(from.getBareJID());
	}

	/**
	 * Method description
	 *
	 *
	 * @param presence
	 *
	 * @throws TigaseStringprepException
	 */
	public void update(final Element presence) throws TigaseStringprepException {
		String f = presence.getAttributeStaticStr(Packet.FROM_ATT);

		if (f == null) {
			return;
		}

		final JID from         = JID.jidInstance(f);
		final BareJID bareFrom = from.getBareJID();
		final String resource  = (from.getResource() == null)
														 ? ""
														 : from.getResource();
		final Presence p       = new Presence(presence);

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
		updateBestPresence(bareFrom);
	}

	private void updateBestPresence(final BareJID bareFrom)
					throws TigaseStringprepException {
		Presence x = intGetBestPresence(bareFrom);

		if (x == null) {
			this.bestPresence.remove(bareFrom);
		} else {
			this.bestPresence.put(bareFrom, x);
		}
	}

	//~--- inner classes --------------------------------------------------------

	private class Presence {
		final Element element;
		final int priority;
		final String type;

		//~--- constructors -------------------------------------------------------

		/**
		 * @param presence
		 */
		public Presence(Element presence) {
			this.element = presence;
			this.type    = presence.getAttributeStaticStr(Packet.TYPE_ATT);

			String p =
				presence.getChildCDataStaticStr(tigase.server.Presence.PRESENCE_PRIORITY_PATH);
			int x = 0;

			try {
				x = Integer.parseInt(p);
			} catch (Exception e) {}
			this.priority = x;
		}
	}
}


//~ Formatted in Tigase Code Convention on 13/02/20
