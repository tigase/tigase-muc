/*
 * Tigase Jabber/XMPP Multi User Chatroom Component
 * Copyright (C) 2007 "Bartosz M. Małkowski" <bartosz.malkowski@tigase.org>
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Logger;

import tigase.db.UserRepository;
import tigase.muc.xmpp.JID;
import tigase.muc.xmpp.stanzas.Message;
import tigase.muc.xmpp.stanzas.Presence;

/**
 * 
 * <p>
 * Created: 2007-06-20 08:46:24
 * </p>
 * 
 * @author bmalkow
 * @version $Rev$
 */
public class RoomContext extends RoomConfiguration {
	private static final long serialVersionUID = 1L;

	private History conversationHistory = new History(10);

	private Message currentSubject;

	private Map<JID, Presence> lastReceivedPresence = new HashMap<JID, Presence>();

	private boolean lockedRoom;

	private Logger log = Logger.getLogger(this.getClass().getName());

	/**
	 * <realJID, nick>
	 */
	private Map<JID, String> occupantsByJID = new HashMap<JID, String>();

	/**
	 * <nick, realJID>
	 */
	private Map<String, JID> occupantsByNick = new HashMap<String, JID>();

	/**
	 * Key: bareJID
	 */
	private Map<JID, Role> occupantsRole = new HashMap<JID, Role>();

	private boolean roomCreated = false;

	/**
	 * @param namespace
	 * @param id
	 * @param mucRepocitory
	 * @param constructorJid
	 */
	public RoomContext(String namespace, String id, UserRepository mucRepocitory, JID constructorJid, boolean roomCreated) {
		super(namespace, id, mucRepocitory, constructorJid);
		this.roomCreated = roomCreated;
		this.lockedRoom = roomCreated;
	}

	public Role calculateInitialRole(JID realJID) {
		Affiliation affiliation = getAffiliation(realJID);
		Role result;
		result = isRoomconfigModeratedRoom()
		// || configuration.isOccupantDefaultParticipant()
		? Role.VISITOR
				: Role.PARTICIPANT;
		if (affiliation == Affiliation.ADMIN || affiliation == Affiliation.OWNER) {
			return Role.MODERATOR;
		} else if (affiliation == Affiliation.MEMBER) {
			return Role.PARTICIPANT;
		}
		return result;
	}

	public List<? extends JID> findBareJidsWithoutAffiliations() {
		List<JID> result = new ArrayList<JID>();
		for (Map.Entry<JID, String> entry : this.occupantsByJID.entrySet()) {
			if (getAffiliation(entry.getKey()) == Affiliation.NONE) {
				result.add(entry.getKey());
			}
		}
		return result;
	}

	public List<JID> findJidsByRole(Role reqRole) {
		List<JID> result = new ArrayList<JID>();
		for (Map.Entry<JID, Role> entry : this.occupantsRole.entrySet()) {
			if (reqRole == entry.getValue()) {
				result.add(entry.getKey());
			}
		}
		return result;
	}

	public History getConversationHistory() {
		return conversationHistory;
	}

	public Message getCurrentSubject() {
		return currentSubject;
	}

	public Map<JID, Presence> getLastReceivedPresence() {
		return lastReceivedPresence;
	}

	public List<JID> getOccupantJidsByBare(JID jid) {
		List<JID> result = new ArrayList<JID>();
		JID sf = jid.getBareJID();
		for (Entry<JID, String> entry : this.occupantsByJID.entrySet()) {
			JID k = entry.getKey().getBareJID();
			if (k.equals(sf)) {
				result.add(entry.getKey());
			}
		}
		return result;
	}

	public Map<JID, String> getOccupantsByJID() {
		return occupantsByJID;
	}

	public Map<String, JID> getOccupantsByNick() {
		return occupantsByNick;
	}

	public Map<JID, Role> getOccupantsRole() {
		return occupantsRole;
	}

	public Role getRole(JID jid) {
		Role result = this.occupantsRole.get(jid.getBareJID());
		return result == null ? Role.NONE : result;
	}

	public boolean isLockedRoom() {
		return lockedRoom;
	}

	public boolean isRoomCreated() {
		return roomCreated;
	}

	public void removeOccupantByJID(JID jid) {
		String nick = this.occupantsByJID.remove(jid);
		this.occupantsByNick.remove(nick);
		this.lastReceivedPresence.remove(jid);
		setRole(jid, null);
	}

	public void setConversationHistory(History conversationHistory) {
		this.conversationHistory = conversationHistory;
	}

	public void setCurrentSubject(Message currentSubject) {
		this.currentSubject = currentSubject;
	}

	public void setLastReceivedPresence(Map<JID, Presence> lastReceivedPresence) {
		this.lastReceivedPresence = lastReceivedPresence;
	}

	public void setLockedRoom(boolean lockedRoom) {
		this.lockedRoom = lockedRoom;
	}

	public void setOccupantsByJID(Map<JID, String> occupantsByJID) {
		this.occupantsByJID = occupantsByJID;
	}

	public void setOccupantsByNick(Map<String, JID> occupantsByNick) {
		this.occupantsByNick = occupantsByNick;
	}

	public void setOccupantsRole(Map<JID, Role> occupantsRole) {
		this.occupantsRole = occupantsRole;
	}

	public void setRole(JID jid, Role role) {
		if (role == null) {
			this.occupantsRole.remove(jid.getBareJID());
		} else {
			this.occupantsRole.put(jid.getBareJID(), role);
		}
	}

	public void setRole(String nick, Role role) {
		JID jid = this.occupantsByNick.get(nick);
		if (role == null) {
			this.occupantsRole.remove(jid.getBareJID());
		} else {
			this.occupantsRole.put(jid.getBareJID(), role);
		}
	}

	public void setRoomCreated(boolean roomCreated) {
		this.roomCreated = roomCreated;
	}

}
