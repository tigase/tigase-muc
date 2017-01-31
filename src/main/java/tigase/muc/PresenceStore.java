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

import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Class description
 * 
 * 
 * @version Enter version here..., 13/02/16
 * @author Enter your name here...
 */
public class PresenceStore {

	public PresenceStore() {
		presenceOrdening = PresenceDeliveryLogic.PREFERE_PRIORITY;
	}

	public PresenceStore( PresenceDeliveryLogic pdl ) {
		presenceOrdening = pdl;
	}

	public class Presence {

		final Element element;
		final int priority;
		final String type;
		final String show;
		final JID from;
		final Date lastUpdated;

		/**
		 * @param presence
		 */
		public Presence(Element presence) {
			this.lastUpdated = new Date();
			this.element = presence;
			this.type = presence.getAttributeStaticStr(Packet.TYPE_ATT);
			this.from = JID.jidInstanceNS( presence.getAttributeStaticStr(Packet.FROM_ATT) );

			this.show = presence.getChildCDataStaticStr( tigase.server.Presence.PRESENCE_SHOW_PATH );

			String p = presence.getChildCDataStaticStr(tigase.server.Presence.PRESENCE_PRIORITY_PATH);
			int x = 0;

			try {
				x = Integer.parseInt(p);
			} catch (Exception e) {
			}
			this.priority = x;
		}

		public Element getElement() {
			return element;
		}

		public Date getLastUpdated() {
			return lastUpdated;
		}

		public String getShow() {
			return show;
		}

		public int getPriority() {
			return priority;
		}

		@Override
		public String toString() {
			return "Presence[" + "priority=" + priority + ", type=" + type + ", show=" + show
						 + ", from=" + from + ", lastUpdated=" + lastUpdated + "]";
		}
	}

	/**
	 * Possible presence delivery strategies - either prefering last send presence
	 * or the presence with the highest priority
	 */
	public enum PresenceDeliveryLogic {

		PREFERE_LAST,
		PREFERE_PRIORITY;
	}

	private final Map<BareJID, Presence> bestPresence = new ConcurrentHashMap<>();
	private final Map<JID, Presence> presenceByJid = new ConcurrentHashMap<>();
	protected static final Logger log = Logger.getLogger(PresenceStore.class.getName());
	private Map<BareJID, Map<String, Presence>> presencesMapByBareJid = new ConcurrentHashMap<BareJID, Map<String, Presence>>();
	private PresenceDeliveryLogic presenceOrdening;

	// ~--- methods
	// --------------------------------------------------------------


	/**
	 * Method description
	 * 
	 */
	public void clear() {
		presenceByJid.clear();
		bestPresence.clear();
		presencesMapByBareJid.clear();
		
	}

	public Collection<JID> getAllKnownJIDs() {
		ArrayList<JID> result = new ArrayList<JID>();

		for (Entry<JID, Presence> entry : this.presenceByJid.entrySet()) {
			if (entry.getValue().type == null)
				result.add(entry.getKey());
		}

		return result;
	}

	/**
	 * Method description
	 * 
	 * 
	 * @param jid
	 * 
	 * @return
	 */
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

		return (p == null) ? null : p.element;
	}

	private Presence intGetBestPresence( final BareJID jid ) {
		Map<String, Presence> resourcesPresence = this.presencesMapByBareJid.get( jid );
		Presence result = null;

		if ( log.isLoggable( Level.FINEST ) ){
			log.log(Level.FINEST, "User resources presences: " + resourcesPresence );
		}

		if ( resourcesPresence != null ){
			Iterator<Presence> it = resourcesPresence.values().iterator();

			while ( it.hasNext() ) {
				Presence x = it.next();

				switch ( presenceOrdening ) {
					case PREFERE_PRIORITY:
						if ( ( result == null )
								 || ( ( x.type == null )
											&& ( x.priority > result.priority
													 || ( x.priority == result.priority && x.lastUpdated.after( result.lastUpdated ) ) ) ) ){
							result = x;
						}

						break;
					case PREFERE_LAST:
						Date l = x.lastUpdated;
						if ( ( result == null )
								 || ( ( l.after( result.lastUpdated ) ) && ( x.type == null ) ) ){
							result = x;
						}
						break;
				}
			}
		}
		return result;
	}

	/**
	 * Method description
	 * 
	 * 
	 * @param jid
	 * 
	 * @return
	 */
	public boolean isAvailable(BareJID jid) {
		Map<String, Presence> resourcesPresence = this.presencesMapByBareJid.get( jid );
		if ( log.isLoggable( Level.FINEST ) ){
			log.log( Level.FINEST, "resourcesPresence: " + resourcesPresence );
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

	public void setOrdening( PresenceDeliveryLogic pdl ) {
		if ( log.isLoggable( Level.FINEST ) ){
			log.log( Level.FINEST, "Setting presence delivery logic to: " + pdl);
		}
		presenceOrdening = pdl;
	}

	/**
	 * 
	 * @param from
	 * @throws TigaseStringprepException
	 */
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
		updateBestPresence(bareFrom);
	}

	// ~--- inner classes
	// --------------------------------------------------------

	private void updateBestPresence(final BareJID bareFrom) throws TigaseStringprepException {
		Presence x = intGetBestPresence(bareFrom);

		if ( log.isLoggable( Level.FINEST ) ){
			log.finest( "Selected BestPresence: " + (x !=null ? x.element.toString() : "n/a" ) );
		}

		if (x == null) {
			this.bestPresence.remove(bareFrom);
		} else {
			this.bestPresence.put(bareFrom, x);
		}
	}
}
