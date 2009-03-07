/*
 * Tigase Jabber/XMPP Multi-User Chat Component
 * Copyright (C) 2008 "Bartosz M. Małkowski" <bartosz.malkowski@tigase.org>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. Look for COPYING file in the top folder.
 * If not, see http://www.gnu.org/licenses/.
 *
 * $Rev$
 * Last modified by $Author$
 * $Date$
 */
package tigase.muc;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import tigase.muc.RoomConfig.Anonymity;
import tigase.muc.repository.RepositoryException;
import tigase.util.JIDUtils;
import tigase.xml.Element;

/**
 * @author bmalkow
 * 
 */
public class Room {

	public static interface RoomListener {

		void onChangeSubject(Room room, String nick, String newSubject, Date changeDate);

		void onSetAffiliation(Room room, String jid, Affiliation newAffiliation);
	}

	/**
	 * <bareJID, Affiliation>
	 */
	private final HashMap<String, Affiliation> affiliations = new HashMap<String, Affiliation>();

	private final RoomConfig config;

	private final Date creationDate;

	private final String creatorJid;

	private History history = new History();

	/**
	 * <real JID, Presence>
	 */
	private final HashMap<String, Element> lastPresences = new HashMap<String, Element>();

	private final ArrayList<RoomListener> listeners = new ArrayList<RoomListener>();

	/**
	 * < real JID,nickname>
	 */
	private final HashMap<String, String> occupantsJidNickname = new HashMap<String, String>();

	/**
	 * <nickname,real JID>
	 */
	private final HashMap<String, String> occupantsNicknameJid = new HashMap<String, String>();

	/**
	 * <real JID, Role>
	 */
	private final HashMap<String, Role> roles = new HashMap<String, Role>();

	private boolean roomLocked;

	private String subject;

	private Date subjectChangeDate;

	private String subjectChangerNick;

	/**
	 * @param rc
	 * @param creationDate
	 * @param creatorJid2
	 */
	public Room(RoomConfig rc, Date creationDate, String creatorJid) {
		this.config = rc;
		this.creationDate = creationDate;
		this.creatorJid = creatorJid;
	}

	/**
	 * @param jid
	 * @param owner
	 * @throws RepositoryException
	 */
	public void addAffiliationByJid(String jid, Affiliation affiliation) throws RepositoryException {
		String j = JIDUtils.getNodeID(jid);
		if (affiliation == Affiliation.none) {
			this.affiliations.remove(j);
		} else {
			this.affiliations.put(j, affiliation);
		}
		fireOnSetAffiliation(j, affiliation);
	}

	public void addListener(RoomListener listener) {
		this.listeners.add(listener);
	}

	/**
	 * @param senderJid
	 * @param nickName
	 */
	public void addOccupantByJid(String senderJid, String nickName, Role role) {
		this.occupantsJidNickname.put(senderJid, nickName);
		this.occupantsNicknameJid.put(nickName, senderJid);
		this.roles.put(senderJid, role);
	}

	public void addToHistory(final String message, String senderJid, String senderNickname, Date time) {
		history.add(message, senderJid, senderNickname, time);
	}

	/**
	 * @param senderJid
	 * @param nickName
	 */
	public void changeNickName(String senderJid, String nickName) {
		String oldNickname = this.occupantsJidNickname.put(senderJid, nickName);
		if (oldNickname != null) {
			this.occupantsNicknameJid.remove(oldNickname);
		}
		this.occupantsNicknameJid.put(nickName, senderJid);
	}

	private void fireOnSetAffiliation(String jid, Affiliation affiliation) {
		for (RoomListener listener : this.listeners) {
			listener.onSetAffiliation(this, jid, affiliation);
		}
	}

	private void fireOnSetSubject(String nick, String subject, Date changeDate) {
		for (RoomListener listener : this.listeners) {
			listener.onChangeSubject(this, nick, subject, changeDate);
		}
	}

	/**
	 * @param value
	 *            user JID
	 * @return
	 */
	public Affiliation getAffiliation(String jid) {
		Affiliation result = null;
		if (jid != null) {
			result = this.affiliations.get(JIDUtils.getNodeID(jid));
		}
		return result == null ? Affiliation.none : result;
	}

	/**
	 * @return
	 */
	public Collection<String> getAffiliations() {
		return this.affiliations.keySet();
	}

	public RoomConfig getConfig() {
		return config;
	}

	/**
	 * @return
	 */
	public Date getCreationDate() {
		return this.creationDate;
	}

	public String getCreatorJid() {
		return creatorJid;
	}

	public List<Element> getHistoryMessages(String recipientJid) {
		Affiliation recipientAffiliation = getAffiliation(recipientJid);
		boolean showJids = config.getRoomAnonymity() == Anonymity.nonanonymous
				|| config.getRoomAnonymity() == Anonymity.semianonymous
				&& (recipientAffiliation == Affiliation.owner || recipientAffiliation == Affiliation.admin);

		return history.getMessages(recipientJid, config.getRoomId(), showJids);
	}

	public Element getLastPresenceCopyByJid(String jid) {
		Element x = this.lastPresences.get(jid);
		return x == null ? null : x.clone();
	}

	/**
	 * @return
	 */
	public int getOccupantsCount() {
		return this.occupantsJidNickname.size();
	}

	/**
	 * @param itemNick
	 */
	public String getOccupantsJidByNickname(String nickname) {
		return this.occupantsNicknameJid.get(nickname);
	}

	public Collection<String> getOccupantsJids() {
		return Collections.unmodifiableSet(this.occupantsJidNickname.keySet());
	}

	/**
	 * @param nickName
	 * @return
	 */
	public String getOccupantsNickname(String jid) {
		return this.occupantsJidNickname.get(jid);
	}

	public String getOccupantsNicknameByBareJid(String jid) {
		String j = JIDUtils.getNodeID(jid);
		for (Entry<String, String> entry : this.occupantsJidNickname.entrySet()) {
			if (j.equals(entry.getKey())) {
				return entry.getValue();
			}
		}
		return null;
	}

	/**
	 * @param occupantBareJid
	 * @return
	 */
	public String[] getRealJidsByBareJid(final String occupantBareJid) {
		ArrayList<String> result = new ArrayList<String>();

		for (Entry<String, String> entry : this.occupantsJidNickname.entrySet()) {
			if (occupantBareJid.equals(JIDUtils.getNodeID(entry.getKey()))) {
				result.add(entry.getKey());
			}
		}

		return result.toArray(new String[] {});
	}

	/**
	 * @param occupantNickname
	 * @return
	 */
	public Role getRoleByJid(String jid) {
		Role result = null;
		if (jid != null) {
			result = this.roles.get(jid);
		}
		return result == null ? Role.none : result;
	}

	public String getRoomId() {
		return this.config.getRoomId();
	}

	/**
	 * @return
	 */
	public String getSubject() {
		return subject;
	}

	public Date getSubjectChangeDate() {
		return subjectChangeDate;
	}

	/**
	 * @return
	 */
	public String getSubjectChangerNick() {
		return subjectChangerNick;
	}

	public boolean isNickNameExists(String nickname) {
		return this.occupantsJidNickname.values().contains(nickname);
	}

	/**
	 * @param senderJid
	 * @return
	 */
	public boolean isOccupantExistsByJid(String senderJid) {
		return this.occupantsJidNickname.containsKey(senderJid);
	}

	public boolean isRoomLocked() {
		return roomLocked;
	}

	public void removeListener(RoomListener listener) {
		this.listeners.remove(listener);
	}

	public void removeOccupantByJid(String jid) {
		String nickName = this.occupantsJidNickname.remove(jid);
		if (nickName != null)
			this.occupantsNicknameJid.remove(nickName);
		this.lastPresences.remove(jid);
		this.roles.remove(jid);
	}

	/**
	 * @param affiliations2
	 */
	public void setAffiliations(Map<String, Affiliation> affiliations) {
		this.affiliations.clear();
		this.affiliations.putAll(affiliations);
	}

	public void setNewRole(String occupantJid, Role occupantNewRole) {
		this.roles.put(occupantJid, occupantNewRole);
	}

	/**
	 * @param msg
	 * @param senderRoomJid
	 * @throws RepositoryException
	 */
	public void setNewSubject(String msg, String senderNickname) throws RepositoryException {
		this.subjectChangerNick = senderNickname;
		this.subject = msg;
		this.subjectChangeDate = new Date();
		fireOnSetSubject(senderNickname, msg, this.subjectChangeDate);
	}

	public void setRoomLocked(boolean roomLocked) {
		this.roomLocked = roomLocked;
	}

	public void setSubjectChangeDate(Date subjectChangeDate) {
		this.subjectChangeDate = subjectChangeDate;
	}

	/**
	 * @param nickName
	 * @param element
	 */
	public void updatePresenceByJid(String jid, Element element) {
		Element cp = element.clone();
		Element toRemove = cp.getChild("x", "http://jabber.org/protocol/muc");
		if (toRemove != null)
			cp.removeChild(toRemove);

		this.lastPresences.put(jid, cp);
	}
}
