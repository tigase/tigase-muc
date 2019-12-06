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

import tigase.component.exceptions.RepositoryException;
import tigase.kernel.beans.Bean;
import tigase.server.Packet;
import tigase.util.stringprep.TigaseStringprepException;
import tigase.xml.Element;
import tigase.xmpp.jid.BareJID;
import tigase.xmpp.jid.JID;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Room
		implements RoomConfig.RoomConfigListener {

	public static final String FILTERED_OCCUPANTS_COLLECTION = "filtered_occupants_collection";
	protected static final Logger log = Logger.getLogger(Room.class.getName());
	protected final PresenceFiltered presenceFiltered;
	protected final PresenceStore presences = new PresenceStore();

	private final Map<BareJID, RoomAffiliation> affiliations = new ConcurrentHashMap<BareJID, RoomAffiliation>();
	private final RoomConfig config;
	private final Date creationDate;
	private final BareJID creatorJid;
	private final List<RoomListener> listeners = new CopyOnWriteArrayList<RoomListener>();
	private final List<Room.RoomOccupantListener> occupantListeners = new CopyOnWriteArrayList<Room.RoomOccupantListener>();
	private final Map<String, OccupantEntry> occupants = new ConcurrentHashMap<String, OccupantEntry>();
	private final Map<String, Object> roomCustomData = new ConcurrentHashMap<String, Object>();
	private String avatarHash;
	private boolean roomLocked;
	private String subject;
	private Date subjectChangeDate;
	private String subjectChangerNick;

	public static Role getDefaultRole(final RoomConfig config, final Affiliation affiliation) {
		if (config.isRoomModerated() && (affiliation == Affiliation.none)) {
			return Role.visitor;
		} else {
			switch (affiliation) {
				case admin:
					return Role.moderator;
				case member:
					return Role.participant;
				case none:
					return Role.participant;
				case outcast:
					return Role.none;
				case owner:
					return Role.moderator;
				default:
					return Role.none;
			}
		}
	}

	private static BareJID nicknameToJid(String nickname) {
		if (nickname == null || !nickname.contains("@")) {
			return null;
		}
		return BareJID.bareJIDInstanceNS(nickname);
	}

	protected Room(RoomConfig rc, Date creationDate, BareJID creatorJid) {
		this.config = rc;
		this.creationDate = creationDate;
		this.creatorJid = creatorJid;
		this.presenceFiltered = new PresenceFiltered(this);
		addOccupantListener(presenceFiltered);
		addListener(presenceFiltered);
		rc.addListener(this);
		presences.setOrdening(rc.getPresenceDeliveryLogic());
	}

	public String getAvatarHash() {
		return avatarHash;
	}

	public void setAvatarHash(String avatarHash) {
		this.avatarHash = avatarHash;
	}

	public void addAffiliationByJid(BareJID jid, RoomAffiliation affiliation) throws RepositoryException {
		final RoomAffiliation oldAffiliation = getAffiliation(jid);
		if (affiliation.getAffiliation() == Affiliation.none) {
			this.affiliations.remove(jid);
		} else {
			this.affiliations.put(jid, affiliation);
		}
		fireOnSetAffiliation(jid, oldAffiliation, affiliation);
	}

	public void addListener(Room.RoomListener listener) {
		this.listeners.add(listener);
	}

	public void addOccupantByJid(JID senderJid, String nickName, Role role, Element pe)
			throws TigaseStringprepException {
		OccupantEntry entry = this.occupants.get(nickName);
		this.presences.update(pe);
		if (entry == null) {
			entry = new OccupantEntry(nickName, senderJid.getBareJID());
			this.occupants.put(nickName, entry);

			if (log.isLoggable(Level.FINEST)) {
				log.log(Level.FINEST, "Room {0}. Created OccupantEntry for {1}, nickname={2}",
						new Object[]{config.getRoomJID(), senderJid, nickName});
			}
		}

		entry.role = role;
		boolean added = false;
		synchronized (entry.jids) {
			added = entry.jids.add(senderJid);
		}

		if (log.isLoggable(Level.FINEST)) {
			log.log(Level.FINEST, "Room {0}. {1} occupant {2} ({3}) to room with role={4}; filtering enabled: {5}",
					new Object[]{config.getRoomJID(), (added ? "Added" : "Updated"), senderJid, nickName, role,
								 config.isPresenceFilterEnabled()});
		}

		if (added) {
			if (!config.isPresenceFilterEnabled() || (config.isPresenceFilterEnabled() &&
					(!config.getPresenceFilteredAffiliations().isEmpty() && config.getPresenceFilteredAffiliations()
							.contains(getAffiliation(senderJid.getBareJID()).getAffiliation())))) {
				fireOnOccupantAdded(senderJid);
				fireOnOccupantChangedPresence(senderJid, nickName, pe, true);
			}
		}
	}

	public void addOccupantListener(Room.RoomOccupantListener listener) {
		this.occupantListeners.add(listener);
	}

	public void changeNickName(JID senderJid, String nickName) {
		OccupantEntry occ = getBySenderJid(senderJid);
		String oldNickname = occ.nickname;

		this.occupants.remove(oldNickname);
		occ.nickname = nickName;
		this.occupants.put(nickName, occ);

		if (log.isLoggable(Level.FINEST)) {
			log.finest("Room " + config.getRoomJID() + ". Occupant " + senderJid + " changed nickname from " +
							   oldNickname + " to " + nickName);
		}
	}

	public void fireOnMessageToOccupants(JID fromJID, Packet msg) {
		for (Room.RoomListener listener : this.listeners) {
			listener.onMessageToOccupants(this, fromJID, msg);
		}
	}

	public RoomAffiliation getAffiliation(BareJID jid) {
		if (log.isLoggable(Level.FINEST)) {
			log.log(Level.FINEST, "Getting affiliations for: " + jid + " from set: " + affiliations.toString());
		}
		RoomAffiliation result = null;
		if (jid != null) {
			result = this.affiliations.get(jid);
		}
		return result == null ? RoomAffiliation.none : result;
	}

	public RoomAffiliation getAffiliation(String nickname) {
		BareJID jid = getOccupantsJidByNickname(nickname);
		if (jid != null) {
			return getAffiliation(jid);
		}
		return RoomAffiliation.none;
	}

	public Collection<BareJID> getAffiliations() {
		if (log.isLoggable(Level.FINEST)) {
			log.log(Level.FINEST, "Getting affiliations: " + affiliations.toString());
		}
		return this.affiliations.keySet();
	}

	public void setAffiliations(Map<BareJID, RoomAffiliation> affiliations) {
		this.affiliations.clear();
		this.affiliations.putAll(affiliations);
	}

	public Stream<BareJID> getAffiliationsMatching(Predicate<RoomAffiliation> predicate) {
		return this.affiliations.entrySet().stream().filter(e -> predicate.test(e.getValue())).map(Map.Entry::getKey);
	}

	public Collection<JID> getAllOccupantsJID() {
		if (config.isPresenceFilterEnabled()) {
			return presenceFiltered.getOccupantsPresenceFilteredJIDs();
		} else {
			return presences.getAllKnownJIDs();
		}
	}

	public Stream<JID> getAllOccupantsJidsForMessageDelivery() {
		return this.occupants.entrySet()
				.stream()
				.filter(entry -> entry.getValue().role.isReceiveMessages())
				.flatMap(entry -> {
					synchronized (entry.getValue().jids) {
						return new HashSet(entry.getValue().jids).stream();
					}
				});
	}

	public Stream<JID> getAllJidsForMessageDelivery() {
		return Stream.concat(getAllOccupantsJidsForMessageDelivery(),
							 getAffiliationsMatching(RoomAffiliation::isPersistentOccupant).filter(
									 createAvailableFilter()).map(JID::jidInstanceNS));
	}

	public RoomConfig getConfig() {
		return config;
	}

	public Date getCreationDate() {
		return this.creationDate;
	}

	public BareJID getCreatorJid() {
		return creatorJid;
	}

	public String getDebugInfoOccupants() {
		StringBuilder sb = new StringBuilder();
		sb.append("Occupants in room " + config.getRoomJID() + "[" + occupants.entrySet().size() + "]: ");
		for (Map.Entry<String, OccupantEntry> o : occupants.entrySet()) {
			sb.append(o.getKey()).append('=').append(o.getValue().toString()).append(" ");
		}
		return sb.toString();
	}

	public Element getLastPresenceCopy(BareJID occupantJid, String nickname) {
		return getLastPresenceCopyByJid(occupantJid);
	}

	public Element getLastPresenceCopyByJid(BareJID occupantJid) {
		Element e = this.presences.getBestPresence(occupantJid);
		if (e != null) {
			return e.clone();
		}

		RoomAffiliation aff = getAffiliation(occupantJid);
		if (!aff.isPersistentOccupant()) {
			return null;
		}

		Element result = new Element("presence");
		result.setAttribute("from", occupantJid + "/" +
				Optional.ofNullable(aff.getRegisteredNickname()).orElse(occupantJid.toString()));
		result.setAttribute("to", getRoomJID().toString());

		result.addChild(new Element("show", "xa"));

		return result;
	}

	public int getOccupantsCount() {
		final HashSet<BareJID> tmp = new HashSet<>();

		this.occupants.values().forEach(occupantEntry -> tmp.add(occupantEntry.jid));
		this.affiliations.forEach((key, value) -> {
			if (value.isPersistentOccupant()) {
				tmp.add(key);
			}
		});

		return tmp.size();
	}

	public Stream<BareJID> getOccupantsBareJids() {
		return this.occupants.values().stream().map(OccupantEntry::getBareJID);
	}

	public BareJID getOccupantsJidByNickname(String nickname) {
		if (nickname == null) {
			return null;
		}
		OccupantEntry entry = this.occupants.get(nickname);
		if (entry != null) {
			return entry.jid;
		}

		return getPersistentOccupantJidByNickname(nickname);
	}

	public Collection<JID> getOccupantsJidsByNickname(final String nickname) {
		if (nickname == null) {
			return Collections.emptyList();
		}
		Collection<JID> result = getLocalOccupantsJidsByNickname(nickname);
		if (!result.isEmpty()) {
			return result;
		}
		return getPersistentOccupantsJidsByNickname(nickname);
	}

	public String getOccupantsNickname(JID jid) {
		OccupantEntry e = getBySenderJid(jid);
		if (e != null) {
			return e.nickname;
		}

		RoomAffiliation aff = getAffiliation(jid.getBareJID());
		if (aff.isPersistentOccupant()) {
			return Optional.ofNullable(aff.getRegisteredNickname()).orElse(jid.getBareJID().toString());
		}

		return null;
	}

	public Collection<String> getOccupantsNicknames(boolean includePersistent) {
		if (includePersistent) {
			final HashSet<String> result = new HashSet<>();

			result.addAll(this.occupants.keySet());

			affiliations.forEach((jid, affiliation) -> {
				if (affiliation.isPersistentOccupant()) {
					result.add(Optional.ofNullable(affiliation.getRegisteredNickname()).orElse(jid.toString()));
				}
			});

			return result;
		} else {
			return this.occupants.keySet();
		}
	}

	public boolean isOccupantOnline(final BareJID jid) {
		for (Map.Entry<String, OccupantEntry> e : this.occupants.entrySet()) {
			if (e.getValue().jid.equals(jid)) {
				return true;
			}
		}
		return false;
	}

	public Collection<String> getOccupantsNicknames(BareJID bareJid) {
		Set<String> result = new HashSet<>();

		for (Map.Entry<String, OccupantEntry> e : this.occupants.entrySet()) {
			if (e.getValue().jid.equals(bareJid)) {
				result.add(e.getKey());
			}
		}

		return Collections.unmodifiableCollection(result);
	}

	public PresenceFiltered getPresenceFiltered() {
		return presenceFiltered;
	}

	public Role getRole(String nickname) {
		if (nickname == null) {
			return Role.none;
		}
		OccupantEntry entry = this.occupants.get(nickname);
		if (entry != null) {
			return entry.role == null ? Role.none : entry.role;
		}

		BareJID candidate = getPersistentOccupantJidByNickname(nickname);
		if (candidate == null) {
			return Role.none;
		}

		RoomAffiliation aff = getAffiliation(candidate);
		if (!aff.isPersistentOccupant()) {
			return Role.none;
		}

		return getDefaultRole(this.config, aff.getAffiliation());
	}

	public Object getRoomCustomData(String key) {
		return roomCustomData.get(key);
	}

	public BareJID getRoomJID() {
		return this.config.getRoomJID();
	}

	public String getSubject() {
		return subject;
	}

	public Date getSubjectChangeDate() {
		return subjectChangeDate;
	}

	public void setSubjectChangeDate(Date subjectChangeDate) {
		this.subjectChangeDate = subjectChangeDate;
	}

	public String getSubjectChangerNick() {
		return subjectChangerNick;
	}

	public boolean isOccupantInRoom(final JID jid) {
		OccupantEntry oe = getBySenderJid(jid);
		if (oe != null) {
			return true;
		}

		RoomAffiliation aff = getAffiliation(jid.getBareJID());
		return aff.isPersistentOccupant();
	}

	public boolean isRoomLocked() {
		return roomLocked;
	}

	public void setRoomLocked(boolean roomLocked) {
		this.roomLocked = roomLocked;
	}

	@Override
	public void onConfigChanged(RoomConfig roomConfig, Set<String> modifiedVars) {
		presences.setOrdening(roomConfig.getPresenceDeliveryLogic());
	}

	@Override
	public void onInitialRoomConfig(RoomConfig roomConfig) {
	}

	public void removeListener(Room.RoomListener listener) {
		this.listeners.remove(listener);
	}

	/**
	 * @return <code>true</code> if no more JIDs assigned to nickname. In other words: nickname is removed
	 */
	public boolean removeOccupant(JID jid) {
		OccupantEntry e = getBySenderJid(jid);
		if (e != null) {
			try {
				synchronized (e.jids) {
					e.jids.remove(jid);
					if (log.isLoggable(Level.FINEST)) {
						log.finest("Room " + config.getRoomJID() + ". Removed JID " + jid + " of occupant");
					}
					if (e.jids.isEmpty()) {
						this.occupants.remove(e.nickname);
						if (log.isLoggable(Level.FINEST)) {
							log.finest("Room " + config.getRoomJID() + ". Removed occupant " + jid);
						}
						return true;
					}
				}
			} finally {
				fireOnOccupantRemoved(jid);
			}
		}
		return false;
	}

	public void removeOccupant(String occupantNick) {
		OccupantEntry e = this.occupants.remove(occupantNick);
		if (e != null) {
			if (log.isLoggable(Level.FINEST)) {
				log.finest("Room " + config.getRoomJID() + ". Removed occupant " + occupantNick);
			}

			for (JID jid : e.jids) {
				fireOnOccupantRemoved(jid);
			}
		}
	}

	public void setNewAffiliation(BareJID user, RoomAffiliation affiliation) {
		this.affiliations.put(user, affiliation);
	}

	public void setNewRole(String nickname, Role newRole) {
		OccupantEntry entry = this.occupants.get(nickname);
		if (entry != null) {
			entry.role = newRole;
			if (log.isLoggable(Level.FINEST)) {
				log.finest("Room " + config.getRoomJID() + ". Changed role of occupant " + nickname + " to " + newRole);
			}
		}
	}

	public void setNewSubject(String msg, String senderNickname) throws RepositoryException {
		this.subjectChangerNick = senderNickname;
		this.subject = msg;
		this.subjectChangeDate = new Date();
		fireOnSetSubject(senderNickname, msg, this.subjectChangeDate);
	}

	public void setRoomCustomData(String key, Object data) {
		synchronized (this.roomCustomData) {
			this.roomCustomData.put(key, data);
		}
	}

	public void updatePresenceByJid(JID jid, String nickname, Element cp) throws TigaseStringprepException {
		if (cp == null) {
			this.presences.remove(jid);
			if (log.isLoggable(Level.FINEST)) {
				log.finest("Room " + config.getRoomJID() + ". Removed presence from " + jid + " (" + nickname + ")");
			}
		} else {
			if (log.isLoggable(Level.FINEST)) {
				log.finest("Room " + config.getRoomJID() + ". Updated presence from " + jid + " (" + nickname + ")");
			}
			this.presences.update(cp);
		}

		fireOnOccupantChangedPresence(jid, nickname, cp, false);
	}

	protected Collection<JID> getLocalOccupantsJidsByNickname(final String nickname) {
		OccupantEntry entry = this.occupants.get(nickname);
		if (entry != null) {
			return Collections.unmodifiableCollection(new ConcurrentSkipListSet(entry.jids));
		}
		return Collections.emptyList();
	}

	protected Collection<JID> getPersistentOccupantsJidsByNickname(final String nickname) {
		BareJID candidate = getPersistentOccupantJidByNickname(nickname);

		if (candidate != null) {
			return Collections.singleton(JID.jidInstanceNS(candidate, nickname));
		} else {
			return Collections.emptyList();
		}
	}

	protected BareJID getPersistentOccupantJidByNickname(final String nickname) {
		Optional<BareJID> optionalJid = this.affiliations.entrySet()
				.stream()
				.filter(e -> e.getValue().isPersistentOccupant())
				.filter(e -> nickname.equals(e.getValue().getRegisteredNickname()))
				.map(Map.Entry::getKey)
				.findAny();
		if (optionalJid.isPresent()) {
			return optionalJid.get();
		}

		BareJID candidate = nicknameToJid(nickname);
		if (candidate == null) {
			return null;
		}

		RoomAffiliation aff = getAffiliation(candidate);
		if (aff == null || !aff.isPersistentOccupant()) {
			return null;
		}

		return candidate;
	}

	protected Predicate<BareJID> createAvailableFilter() {
		Set<BareJID> occupants = getOccupantsBareJids().collect(Collectors.toSet());
		return (jid) -> !occupants.contains(jid);
	}

	private void fireOnOccupantAdded(JID occupantJid) {
		for (Room.RoomOccupantListener listener : this.occupantListeners) {
			listener.onOccupantAdded(this, occupantJid);
		}
	}

	private void fireOnOccupantChangedPresence(JID occupantJid, String nickname, Element cp, boolean newOccupant) {
		for (Room.RoomOccupantListener listener : this.occupantListeners) {
			listener.onOccupantChangedPresence(this, occupantJid, nickname, cp, newOccupant);
		}
	}

	private void fireOnOccupantRemoved(JID occupantJid) {
		for (Room.RoomOccupantListener listener : this.occupantListeners) {
			listener.onOccupantRemoved(this, occupantJid);
		}
	}

	private void fireOnSetAffiliation(BareJID jid, RoomAffiliation oldAffiliation, RoomAffiliation newAffiliation) {
		for (Room.RoomListener listener : this.listeners) {
			listener.onSetAffiliation(this, jid, oldAffiliation, newAffiliation);
		}
	}

	private void fireOnSetSubject(String nick, String subject, Date changeDate) {
		for (Room.RoomListener listener : this.listeners) {
			listener.onChangeSubject(this, nick, subject, changeDate);
		}
	}

	private OccupantEntry getBySenderJid(JID sender) {
		for (Map.Entry<String, OccupantEntry> e : occupants.entrySet()) {
			synchronized (e.getValue().jids) {
				if (e.getValue().jids.contains(sender)) {
					return e.getValue();
				}
			}
		}
		return null;
	}

	public interface RoomFactory {

		<T> RoomWithId<T> newInstance(T id, RoomConfig rc, Date creationDate, BareJID creatorJid);

	}

	public interface RoomListener {

		void onChangeSubject(Room room, String nick, String newSubject, Date changeDate);

		void onMessageToOccupants(Room room, JID from, Packet msg);

		void onSetAffiliation(Room room, BareJID jid, RoomAffiliation oldAffiliation, RoomAffiliation newAffiliation);
	}

	public interface RoomOccupantListener {

		void onOccupantAdded(Room room, JID occupantJid);

		void onOccupantChangedPresence(Room room, JID occupantJid, String nickname, Element presence,
									   boolean newOccupant);

		void onOccupantRemoved(Room room, JID occupantJid);
	}

	private static class OccupantEntry {

		private final BareJID jid;
		private final Set<JID> jids = new HashSet<JID>();
		private String nickname;

		private Role role = Role.none;

		private OccupantEntry(String nickname, BareJID jid) {
			this.nickname = nickname;
			this.jid = jid;
		}

		@Override
		public String toString() {
			return "[" + nickname + "; " + role + "; " + jid + "; " + jids.toString() + "]";
		}

		private BareJID getBareJID() {
			return jid;
		}
	}

	@Bean(name = "roomFactory", parent = MUCComponent.class, active = true, exportable = true)
	public static class RoomFactoryImpl
			implements RoomFactory {

		@Override
		public <T> RoomWithId<T> newInstance(T id, RoomConfig rc, Date creationDate, BareJID creatorJid) {
			return new RoomWithId(id, rc, creationDate, creatorJid);
		}

	}

}
