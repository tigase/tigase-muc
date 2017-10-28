/*
 * RoomClustered.java
 *
 * Tigase Jabber/XMPP Server
 * Copyright (C) 2004-2017 "Tigase, Inc." <office@tigase.com>
 */

package tigase.muc.cluster;

import tigase.kernel.beans.Bean;
import tigase.muc.*;
import tigase.xml.Element;
import tigase.xmpp.jid.BareJID;
import tigase.xmpp.jid.JID;

import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 *
 * @author andrzej
 */
public class RoomClustered<ID> extends RoomWithId<ID> {

	@Bean(name = "roomFactory", parent = MUCComponentClustered.class, active = true, exportable = true)
	public static class RoomFactoryImpl implements RoomFactory {

		@Override
		public <T> RoomWithId<T> newInstance(T id, RoomConfig rc, Date creationDate, BareJID creatorJid) {
			return new RoomClustered(id, rc, creationDate, creatorJid);
		}

	};

	private final ConcurrentMap<JID, String> remoteNicknames = new ConcurrentHashMap<JID, String>();
	private final ConcurrentMap<String, Occupant> remoteOccupants = new ConcurrentHashMap<String, Occupant>();

	protected RoomClustered(ID id, RoomConfig rc, Date creationDate, BareJID creatorJid) {
		super(id, rc, creationDate, creatorJid);
	}

	public Collection<Occupant> getRemoteOccupants() {
		return remoteOccupants.values();
	}
	
	public void addRemoteOccupant(String nickname, JID occupantJID, Role role, Affiliation affiliation, Element presence) {			
		if (remoteNicknames.containsKey(occupantJID) && !nickname.equals(remoteNicknames.get(occupantJID))) {
			removeRemoteOccupant(occupantJID);
		}
		Occupant occupant = remoteOccupants.get(nickname);
		if (occupant == null) {
			occupant = new Occupant(nickname, occupantJID, role, affiliation, presence);
			Occupant tmp = remoteOccupants.putIfAbsent(nickname, occupant);
			if (tmp != null) {
				occupant = tmp;
			} else {
				remoteNicknames.put(occupantJID, nickname);
				return;
			}
		}
		occupant.addOccupant(occupantJID, presence);
		synchronized (occupant) {
			if (!remoteOccupants.containsKey(nickname)) {
				remoteOccupants.put(nickname, occupant);
			}
		}
		occupant.setRole(role);
		occupant.setAffiliation(affiliation);
		remoteNicknames.put(occupantJID, nickname);
	}

	public void removeRemoteOccupant(JID occupantJID) {
		String nickname = remoteNicknames.remove(occupantJID);
		if (nickname == null) 
			return;
		Occupant occupant = remoteOccupants.get(nickname);
		if (occupant == null)
			return;
		occupant.removeOccupant(occupantJID);
		if (occupant.isEmpty()) {
			synchronized (occupant) {
				if (occupant.isEmpty()) {
					remoteOccupants.remove(nickname, occupant);
				}
			}
		}
	}
	
	@Override
	public int getOccupantsCount() {
		return super.getOccupantsCount() + remoteOccupants.size();
	}
	
	@Override
	public Collection<JID> getOccupantsJidsByNickname(String nickname) {
		Collection<JID> jids = super.getOccupantsJidsByNickname(nickname);
		if (jids.isEmpty()) {
			Occupant occupant = remoteOccupants.get(nickname);
			if (occupant != null) {
				return Collections.unmodifiableCollection(occupant.getOccupants());
			}
		}
		return jids;
	}
	
	@Override
	public boolean removeOccupant(JID jid) {
		String nickname = getOccupantsNickname(jid);
		Occupant occupant = nickname == null ? null : remoteOccupants.get(nickname);
		return super.removeOccupant(jid) && (occupant == null || occupant.isEmpty());
	}

	@Override
	public Affiliation getAffiliation(String nickname) {
		Affiliation affil = super.getAffiliation(nickname);
		if (affil == Affiliation.none) {
			Occupant occupant = remoteOccupants.get(nickname);
			if (occupant != null) {
				affil = occupant.getAffiliation();
			}
		}
		return affil;
	}
	
	@Override
	public Role getRole(String nickname) {
		Role role = super.getRole(nickname);
		if (role == Role.none && nickname != null) {
			Occupant occupant = remoteOccupants.get(nickname);
			if (occupant != null) {
				role = occupant.getRole();
			}
		}
		return role;
	}	
	
	@Override
	public Element getLastPresenceCopy(BareJID occupantJid, String nickname) {
		PresenceStore.Presence p1 = presences.getBestPresenceInt(occupantJid);
		Occupant occupant = remoteOccupants.get(nickname);
		Occupant.Presence p2 = occupant != null ? occupant.getBestPresenceInt() : null;
		int p1p = p1 == null ? -1 : p1.getPriority();
		int p2p = p2 == null ? -1 : p2.getPriority();
		Element presence = (p1 == null && p2 == null) ? null : (p1p < p2p ? p2.getElement() : p1.getElement());
		return presence == null ? null : presence.clone();
	}

}
