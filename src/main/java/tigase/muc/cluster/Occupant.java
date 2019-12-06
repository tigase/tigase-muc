/*
 * Tigase ACS - MUC Component - Tigase Advanced Clustering Strategy - MUC Component
 * Copyright (C) 2013 Tigase, Inc. (office@tigase.com) - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package tigase.muc.cluster;

import tigase.muc.Affiliation;
import tigase.muc.Role;
import tigase.server.Packet;
import tigase.xml.Element;
import tigase.xmpp.jid.BareJID;
import tigase.xmpp.jid.JID;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author andrzej
 */
public class Occupant {

	private Affiliation affiliation;
	private String nickname;
	private BareJID occupantJID;
	private Map<JID, Presence> occupantJIDs = new ConcurrentHashMap<JID, Presence>();
	private Presence presence;
	private Role role;

	public Occupant(String nickname, JID occupantJID, Role role, Affiliation affiliation, Element presence) {
		this.nickname = nickname;
		this.affiliation = affiliation;
		this.role = role;
		this.occupantJID = occupantJID.getBareJID();
		addOccupant(occupantJID, presence);
	}

	public BareJID getOccupantJID() {
		return occupantJID;
	}

	public Collection<JID> getOccupants() {
		return occupantJIDs.keySet();
	}

	public boolean addOccupant(JID occupantJID, Element presenceEl) {
		Presence presence = new Presence(presenceEl);
		boolean added = this.occupantJIDs.put(occupantJID, presence) == null;
		updateBestPresence();
		return added;
	}

	public void removeOccupant(JID occupantJID) {
		this.occupantJIDs.remove(occupantJID);
		updateBestPresence();
	}

	public boolean isEmpty() {
		return occupantJIDs.isEmpty();
	}

	public String getNickname() {
		return nickname;
	}

	public void setNickname(String nickname) {
		this.nickname = nickname;
	}

	public Affiliation getAffiliation() {
		return affiliation;
	}

	public void setAffiliation(Affiliation affiliation) {
		this.affiliation = affiliation;
	}

	public Role getRole() {
		return role;
	}

	public void setRole(Role role) {
		this.role = role;
	}

	public Element getBestPresence() {
		return presence == null ? null : presence.element;
	}

	public Presence getBestPresenceInt() {
		return presence;
	}

	private void updateBestPresence() {
		Presence result = null;
		Iterator<Presence> it = occupantJIDs.values().iterator();
		while (it.hasNext()) {
			Presence p = it.next();
			if (result == null || p.compareTo(result) > 0) {
				result = p;
			}
		}
		presence = result;
	}

	public class Presence
			implements Comparable<Presence> {

		private final Element element;
		private final int priority;
		private final String type;

		public Presence(Element presence) {
			this.element = presence;
			type = presence.getAttributeStaticStr(Packet.TYPE_ATT);
			String p = presence.getChildCDataStaticStr(tigase.server.Presence.PRESENCE_PRIORITY_PATH);
			int x = 0;
			if (p != null) {
				try {
					x = Integer.parseInt(p);
				} catch (Exception e) {
				}
			}
			this.priority = x;
		}

		@Override
		public int compareTo(Presence o) {
			return priority - o.priority;
		}

		public int getPriority() {
			return priority;
		}

		public Element getElement() {
			return element;
		}
	}
}
