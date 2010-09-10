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
import tigase.xml.Element;
import tigase.xmpp.BareJID;
import tigase.xmpp.JID;

/**
 * @author bmalkow
 * 
 */
public class Room {

	public static interface RoomListener {
		void onChangeSubject(Room room, String nick, String newSubject, Date changeDate);

		void onSetAffiliation(Room room, BareJID jid, Affiliation newAffiliation);
	}

	/**
	 * <bareJID, Affiliation>
	 */
	private final HashMap<BareJID, Affiliation> affiliations = new HashMap<BareJID, Affiliation>();

	private final RoomConfig config;

	private final Date creationDate;

	private final BareJID creatorJid;

	private History history = new History();

	/**
	 * <real JID, Presence>
	 */
	private final HashMap<JID, Element> lastPresences = new HashMap<JID, Element>();

	private final ArrayList<RoomListener> listeners = new ArrayList<RoomListener>();

	/**
	 * < real JID,nickname>
	 */
	private final HashMap<JID, String> occupantsJidNickname = new HashMap<JID, String>();

	/**
	 * <nickname,real JID>
	 */
	private final HashMap<String, ArrayList<JID>> occupantsNicknameJid = new HashMap<String, ArrayList<JID>>();

	/**
	 * <real JID, Role>
	 */
	private final HashMap<BareJID, Role> roles = new HashMap<BareJID, Role>();

	private boolean roomLocked;

	private String subject;

	private Date subjectChangeDate;

	private String subjectChangerNick;

	/**
	 * @param rc
	 * @param creationDate
	 * @param creatorJid2
	 */
	public Room(RoomConfig rc, Date creationDate, BareJID creatorJid) {
		this.config = rc;
		this.creationDate = creationDate;
		this.creatorJid = creatorJid;
	}

	/**
	 * @param jid
	 * @param owner
	 * @throws RepositoryException
	 */
	public void addAffiliationByJid(BareJID jid, Affiliation affiliation) throws RepositoryException {
		if (affiliation == Affiliation.none) {
			this.affiliations.remove(jid);
		} else {
			this.affiliations.put(jid, affiliation);
		}
		fireOnSetAffiliation(jid, affiliation);
	}

	public void addListener(RoomListener listener) {
		this.listeners.add(listener);
	}

	/**
	 * @param senderJid
	 * @param nickName
	 */
	public void addOccupantByJid(JID senderJid, String nickName, Role role) {
		this.occupantsJidNickname.put(senderJid, nickName);
		setJidNickname(nickName, senderJid);
		this.roles.put(senderJid.getBareJID(), role);
	}

	public void addToHistory(final String message, JID senderJid, String senderNickname, Date time) {
		history.add(message, senderJid, senderNickname, time);
	}

	/**
	 * @param senderJid
	 * @param nickName
	 */
	public void changeNickName(JID senderJid, String nickName) {
		String oldNickname = this.occupantsJidNickname.put(senderJid, nickName);
		if (oldNickname != null) {
			this.occupantsNicknameJid.remove(oldNickname);
		}
		if (this.occupantsNicknameJid.containsKey(nickName))
			this.occupantsNicknameJid.get(nickName).add(senderJid);
	}

	private void fireOnSetAffiliation(BareJID jid, Affiliation affiliation) {
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
	public Affiliation getAffiliation(BareJID jid) {
		Affiliation result = null;
		if (jid != null) {
			result = this.affiliations.get(jid);
		}
		return result == null ? Affiliation.none : result;
	}

	/**
	 * @return
	 */
	public Collection<BareJID> getAffiliations() {
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

	public BareJID getCreatorJid() {
		return creatorJid;
	}

	public List<Element> getHistoryMessages(JID recipientJid) {
		Affiliation recipientAffiliation = getAffiliation(recipientJid.getBareJID());
		boolean showJids = config.getRoomAnonymity() == Anonymity.nonanonymous
				|| config.getRoomAnonymity() == Anonymity.semianonymous
				&& (recipientAffiliation == Affiliation.owner || recipientAffiliation == Affiliation.admin);

		return history.getMessages(recipientJid, config.getRoomJID(), showJids);
	}

	public Element getLastPresenceCopyByJid(JID jid) {
		Element x = this.lastPresences.get(jid);
		return x == null ? null : x.clone();
	}

	/**
	 * @return
	 */
	public int getOccupantsCount() {
		return this.occupantsJidNickname.size();
	}

	public Collection<JID> getOccupantsJids() {
		return Collections.unmodifiableSet(this.occupantsJidNickname.keySet());
	}

	/**
	 * @param itemNick
	 */
	public Collection<JID> getOccupantsJidsByNickname(String nickname) {
		return this.occupantsNicknameJid.get(nickname);
	}

	/**
	 * @param nickName
	 * @return
	 */
	public String getOccupantsNickname(JID jid) {
		return this.occupantsJidNickname.get(jid);
	}

	public String getOccupantsNicknameByBareJid(BareJID jid) {
		for (Entry<JID, String> entry : this.occupantsJidNickname.entrySet()) {
			if (jid.equals(entry.getKey().getBareJID())) {
				return entry.getValue();
			}
		}
		return null;
	}

	/**
	 * @param occupantBareJid
	 * @return
	 */
	public JID[] getRealJidsByBareJid(final BareJID occupantBareJid) {
		ArrayList<JID> result = new ArrayList<JID>();

		for (Entry<JID, String> entry : this.occupantsJidNickname.entrySet()) {
			if (occupantBareJid.equals(entry.getKey())) {
				result.add(entry.getKey());
			}
		}

		return result.toArray(new JID[] {});
	}

	/**
	 * @param occupantNickname
	 * @return
	 */
	public Role getRoleByJid(JID jid) {
		Role result = null;
		if (jid != null) {
			result = this.roles.get(jid.getBareJID());
		}
		return result == null ? Role.none : result;
	}

	public BareJID getRoomJID() {
		return this.config.getRoomJID();
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

	public boolean isNickNameExistsForDifferentJid(String nickname, BareJID jid) {
		if (this.occupantsJidNickname.values().contains(nickname)) {
			JID first_jid = this.occupantsNicknameJid.get(nickname).get(0);
			return !first_jid.getBareJID().equals(jid);
		} else {
			return false;
		}
	}

	/**
	 * @param senderJid
	 * @return
	 */
	public boolean isOccupantExistsByJid(JID senderJid) {
		return this.occupantsJidNickname.containsKey(senderJid);
	}

	public boolean isRoomLocked() {
		return roomLocked;
	}

	public void removeAllOccupantsByBareJid(BareJID jid) {
		String nickName = null;
		for (JID full_jid : this.occupantsJidNickname.keySet()) {
			BareJID bare_jid = full_jid.getBareJID();
			if (bare_jid.equals(jid)) {
				nickName = this.occupantsJidNickname.remove(jid);
			}
		}
		if (nickName != null)
			this.occupantsNicknameJid.remove(nickName);
		this.lastPresences.remove(jid);
		this.roles.remove(jid);
	}

	public void removeListener(RoomListener listener) {
		this.listeners.remove(listener);
	}

	public void removeOccupantByJid(JID jid) {
		String nickName = this.occupantsJidNickname.remove(jid);
		if (nickName != null)
			this.occupantsNicknameJid.get(nickName).remove(jid);

		if (this.occupantsNicknameJid.get(nickName).isEmpty()) {
			this.occupantsNicknameJid.remove(nickName);
			this.lastPresences.remove(jid);
			this.roles.remove(jid);
		}
	}

	/**
	 * @param affiliations2
	 */
	public void setAffiliations(Map<BareJID, Affiliation> affiliations) {
		this.affiliations.clear();
		this.affiliations.putAll(affiliations);
	}

	public void setJidNickname(String nickname, JID jid) {
		HashMap<String, ArrayList<JID>> map = this.occupantsNicknameJid;
		if (map.get(nickname) == null) {
			ArrayList<JID> new_list = new ArrayList<JID>();
			map.put(nickname, new_list);
		}
		map.get(nickname).add(jid);
	}

	public void setNewRole(BareJID occupantJid, Role occupantNewRole) {
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
	public void updatePresenceByJid(JID jid, Element element) {
		Element cp = element.clone();
		Element toRemove = cp.getChild("x", "http://jabber.org/protocol/muc");
		if (toRemove != null)
			cp.removeChild(toRemove);

		this.lastPresences.put(jid, cp);
	}
}
