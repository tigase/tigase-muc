/*  tigase-muc
 *  Copyright (C) 2007 by Bartosz M. Małkowski
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software Foundation,
 *  Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 *  $Id$
 */
package org.tigase.muc;

import java.io.Serializable;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import tigase.db.TigaseDBException;
import tigase.db.UserNotFoundException;
import tigase.db.UserRepository;
import tigase.util.JID;

/**
 * 
 * <p>
 * Created: 2007-05-17 12:09:45
 * </p>
 * 
 * @author bmalkow
 * @version $Rev$
 */
public class RoomConfiguration implements Serializable {

    private UserRepository mucRepocitory;

    private static final long serialVersionUID = 1L;

    /**
     * Affiliations that May Discover Real JIDs of Occupants.
     */
    private Set<Affiliation> affiliationsViewsJID = new HashSet<Affiliation>();

    /**
     * Allow Occupants to Change Subject.
     */
    private boolean allowedOccupantChangeSubject;

    /**
     * Allow Occupants to Invite Others.
     */
    private boolean allowedOccupantsToInvite;

    /**
     * Allow Occupants to query other Occupants.
     */
    private boolean allowedOccupantsToQueryOccupants;

    /**
     * Allow Public Searching for Room.
     */
    private boolean allowedPublicSearch;

    /**
     * An Invitation is Required to Enter.
     */
    private boolean invitationRequired;

    /**
     * Lock nicknames to JID usernames.
     */
    private boolean lockNicknames;

    /**
     * Enable Logging of Room Conversations.
     */
    private boolean logging;

    /**
     * Maximum Number of Room Occupants.
     */
    private int maxOccupantNumber;

    /**
     * Make Room Moderated.
     */
    private boolean moderated = false;

    /**
     * Message for user renaming nickname in room.
     */
    private String msgUserChangeNick;

    /**
     * Message for user leaving room.
     */
    private String msgUserExit;

    /**
     * Message for user joining room.
     */
    private String msgUserJoining;

    /**
     * Make Occupants in a Moderated Room Default to Participant.
     */
    private boolean occupantDefaultParticipant;

    public Affiliation getAffiliation(String jid) {
        return this.affiliations.get(JID.getNodeID(jid));
    }

    public void setAffiliation(String jid, Affiliation affiliation) {
        this.affiliations.put(JID.getNodeID(jid), affiliation);
        if (isPersist()) {
            try {
                if (affiliation != null) {
                    this.mucRepocitory.setData(id, "affiliation", JID.getNodeID(jid), affiliation.name());
                } else {
                    this.mucRepocitory.removeData(id, "affiliation", JID.getNodeID(jid));
                }
            } catch (UserNotFoundException e) {
                e.printStackTrace();
            } catch (TigaseDBException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Key: bareJID
     */
    private Map<String, Affiliation> affiliations = new HashMap<String, Affiliation>();

    /**
     * The Room Password.
     */
    private String password;

    /**
     * A Password is required to enter.
     */
    private boolean passwordRequired;

    /**
     * Make Room Persistent.
     */
    private boolean persist;

    /**
     * Ban Private Messages between Occupants.
     */
    private boolean privateMessageBanned;

    /**
     * private String roomName.
     */
    private String roomFullName;

    /**
     * Short Description of Room.
     */
    private String roomShortName;

    /**
     * Room ID
     */
    private String id;

    private Boolean getBoolean(String key, Boolean defaultValue) {
        try {
            return Boolean.valueOf(this.mucRepocitory.getData(id, key));
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private Integer getInteger(String key, Integer defaultValue) {
        try {
            return Integer.valueOf(this.mucRepocitory.getData(id, key));
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private String getString(String key, String defaultValue) {
        try {
            return this.mucRepocitory.getData(id, key);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    RoomConfiguration(String id, UserRepository mucRepocitory, String constructorJid) {
        this.id = id;
        this.mucRepocitory = mucRepocitory;
        String roomName = JID.getNodeNick(id);
        this.roomFullName = getString("roomFullName", roomName);
        this.roomShortName = getString("roomShortName", roomName);
        this.allowedOccupantChangeSubject = getBoolean("allowedOccupantChangeSubject", true);
        this.allowedOccupantsToInvite = getBoolean("allowedOccupantsToInvite", true);
        this.allowedOccupantsToQueryOccupants = false;
        this.allowedPublicSearch = getBoolean("allowedPublicSearch", true);
        this.invitationRequired = getBoolean("invitationRequired", false);
        this.lockNicknames = getBoolean("lockNicknames", false);
        this.logging = getBoolean("logging", false);
        this.maxOccupantNumber = getInteger("maxOccupantNumber", 100);
        this.moderated = getBoolean("moderated", true);
        this.msgUserChangeNick = getString("msgUserChangeNick", "now known as");
        this.msgUserExit = getString("msgUserExit", "leave room");
        this.msgUserJoining = getString("msgUserJoining", "join to room");
        this.occupantDefaultParticipant = getBoolean("occupantDefaultParticipant", true);
        this.password = getString("password", null);
        this.passwordRequired = getBoolean("passwordRequired", false);
        this.persist = getBoolean("persist", false);
        this.privateMessageBanned = getBoolean("privateMessageBanned", false);

        this.affiliationsViewsJID.clear();
        try {
            Collection<Affiliation> x = new HashSet<Affiliation>();
            String[] affs = this.mucRepocitory.getDataList(id, "", "affiliationsViewsJID");
            for (String string : affs) {
                x.add(Affiliation.valueOf(string));
            }
            this.affiliationsViewsJID.addAll(x);
        } catch (Exception e) {
            this.affiliationsViewsJID.add(Affiliation.ADMIN);
            this.affiliationsViewsJID.add(Affiliation.OWNER);
        }

        try {
            Map<String, Affiliation> tmp = new HashMap<String, Affiliation>();
            // this.mucRepocitory.setData(id, "affiliation", JID.getNodeID(jid)
            String[] jids = this.mucRepocitory.getKeys(id, "affiliation");
            for (String jid : jids) {
                String affName = this.mucRepocitory.getData(id, "affiliation", JID.getNodeID(jid));
                tmp.put(jid, Affiliation.valueOf(affName));
            }
            this.affiliations.clear();
            this.affiliations.putAll(tmp);
        } catch (Exception e) {
            this.affiliations.put(JID.getNodeID(constructorJid), Affiliation.OWNER);
        }

    }

    public void flushConfig() {
        try {
            this.mucRepocitory.setData(id, "roomShortName", roomShortName);
            this.mucRepocitory.setData(id, "roomFullName", roomFullName);
            String[] tmp = new String[this.affiliationsViewsJID.size()];
            Iterator<Affiliation> iterator = this.affiliationsViewsJID.iterator();
            int c = 0;
            while (iterator.hasNext()) {
                Affiliation a = iterator.next();
                tmp[c++] = a.name();
            }
            this.mucRepocitory.setDataList(id, "", "affiliationsViewsJID", tmp);
            this.mucRepocitory.setData(id, "allowedOccupantChangeSubject", Boolean
                    .toString(allowedOccupantChangeSubject));
            this.mucRepocitory.setData(id, "allowedOccupantsToInvite", Boolean.toString(allowedOccupantsToInvite));
            this.mucRepocitory.setData(id, "allowedOccupantsToQueryOccupants", Boolean
                    .toString(allowedOccupantsToQueryOccupants));
            this.mucRepocitory.setData(id, "allowedPublicSearch", Boolean.toString(allowedPublicSearch));
            this.mucRepocitory.setData(id, "invitationRequired", Boolean.toString(invitationRequired));
            this.mucRepocitory.setData(id, "lockNicknames", Boolean.toString(lockNicknames));
            this.mucRepocitory.setData(id, "logging", Boolean.toString(logging));
            this.mucRepocitory.setData(id, "moderated", Boolean.toString(moderated));
            this.mucRepocitory.setData(id, "persist", Boolean.toString(persist));
            this.mucRepocitory.setData(id, "passwordRequired", Boolean.toString(passwordRequired));
            this.mucRepocitory.setData(id, "privateMessageBanned", Boolean.toString(privateMessageBanned));
            this.mucRepocitory.setData(id, "occupantDefaultParticipant", Boolean.toString(occupantDefaultParticipant));
            this.mucRepocitory.setData(id, "maxOccupantNumber", Integer.toString(maxOccupantNumber));
            if (msgUserChangeNick != null)
                this.mucRepocitory.setData(id, "msgUserChangeNick", msgUserChangeNick);
            if (msgUserExit != null)
                this.mucRepocitory.setData(id, "msgUserExit", msgUserExit);
            if (msgUserJoining != null)
                this.mucRepocitory.setData(id, "msgUserJoining", msgUserJoining);
            if (password != null)
                this.mucRepocitory.setData(id, "password", password);

        } catch (UserNotFoundException e) {
            e.printStackTrace();
        } catch (TigaseDBException e) {
            e.printStackTrace();
        }
    }

    public Set<Affiliation> getAffiliationsViewsJID() {
        return affiliationsViewsJID;
    }

    public int getMaxOccupantNumber() {
        return maxOccupantNumber;
    }

    public String getMsgUserChangeNick() {
        return msgUserChangeNick;
    }

    public String getMsgUserExit() {
        return msgUserExit;
    }

    public String getMsgUserJoining() {
        return msgUserJoining;
    }

    public String getPassword() {
        return password;
    }

    public String getRoomFullName() {
        return roomFullName;
    }

    public String getRoomShortName() {
        return roomShortName;
    }

    public boolean isAllowedOccupantChangeSubject() {
        return allowedOccupantChangeSubject;
    }

    public boolean isAllowedOccupantsToInvite() {
        return allowedOccupantsToInvite;
    }

    public boolean isAllowedOccupantsToQueryOccupants() {
        return allowedOccupantsToQueryOccupants;
    }

    public boolean isAllowedPublicSearch() {
        return allowedPublicSearch;
    }

    public boolean isInvitationRequired() {
        return invitationRequired;
    }

    public boolean isLockNicknames() {
        return lockNicknames;
    }

    public boolean isLogging() {
        return logging;
    }

    public boolean isModerated() {
        return moderated;
    }

    public boolean isOccupantDefaultParticipant() {
        return occupantDefaultParticipant;
    }

    public boolean isPasswordRequired() {
        return passwordRequired;
    }

    public boolean isPersist() {
        return persist;
    }

    public boolean isPrivateMessageBanned() {
        return privateMessageBanned;
    }

    public void setAffiliationsViewsJID(Set<Affiliation> affiliationsViewsJID) {
        this.affiliationsViewsJID = affiliationsViewsJID;
    }

    public void setAllowedOccupantChangeSubject(boolean allowedOccupantChangeSubject) {
        this.allowedOccupantChangeSubject = allowedOccupantChangeSubject;
    }

    public void setAllowedOccupantsToInvite(boolean allowedOccupantsToInvite) {
        this.allowedOccupantsToInvite = allowedOccupantsToInvite;
    }

    public void setAllowedOccupantsToQueryOccupants(boolean allowedOccupantsToQueryOccupants) {
        this.allowedOccupantsToQueryOccupants = allowedOccupantsToQueryOccupants;
    }

    public void setAllowedPublicSearch(boolean allowedPublicSearch) {
        this.allowedPublicSearch = allowedPublicSearch;
    }

    public void setInvitationRequired(boolean invitationRequired) {
        this.invitationRequired = invitationRequired;
    }

    public void setLockNicknames(boolean lockNicknames) {
        this.lockNicknames = lockNicknames;
    }

    public void setLogging(boolean logging) {
        this.logging = logging;
    }

    public void setMaxOccupantNumber(int maxOccupantNumber) {
        this.maxOccupantNumber = maxOccupantNumber;
    }

    public void setModerated(boolean moderated) {
        this.moderated = moderated;
    }

    public void setMsgUserChangeNick(String msgUserChangeNick) {
        this.msgUserChangeNick = msgUserChangeNick;
    }

    public void setMsgUserExit(String msgUserExit) {
        this.msgUserExit = msgUserExit;
    }

    public void setMsgUserJoining(String msgUserJoining) {
        this.msgUserJoining = msgUserJoining;
    }

    public void setOccupantDefaultParticipant(boolean occupantDefaultParticipant) {
        this.occupantDefaultParticipant = occupantDefaultParticipant;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public void setPasswordRequired(boolean passwordRequired) {
        this.passwordRequired = passwordRequired;
    }

    public void setPersist(boolean persist) {
        this.persist = persist;
    }

    public void setPrivateMessageBanned(boolean privateMessageBanned) {
        this.privateMessageBanned = privateMessageBanned;
    }

    public void setRoomFullName(String roomFullName) {
        this.roomFullName = roomFullName;
    }

    public void setRoomShortName(String roomShortName) {
        this.roomShortName = roomShortName;
    }

}
