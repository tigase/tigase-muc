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
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.tigase.form.Field;
import org.tigase.form.Form;

import tigase.db.TigaseDBException;
import tigase.db.UserExistsException;
import tigase.db.UserNotFoundException;
import tigase.db.UserRepository;
import tigase.util.JIDUtils;
import tigase.xml.Element;

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

    private static final long serialVersionUID = 1L;

    /**
     * Key: bareJID
     */
    private Map<String, Affiliation> affiliations = new HashMap<String, Affiliation>();

    /**
     * Affiliations that May Discover Real JIDs of Occupants.
     */
    private String affiliationsViewsJID = "";

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
     * Room ID
     */
    private String id;

    /**
     * An Invitation is Required to Enter.
     */
    private boolean invitationRequired;

    /**
     * Lock nicknames to JIDUtils usernames.
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

    private UserRepository mucRepocitory;

    /**
     * Make Occupants in a Moderated Room Default to Participant.
     */
    private boolean occupantDefaultParticipant;

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

    RoomConfiguration(String id, UserRepository mucRepocitory, String constructorJid) {
        this.id = id;
        this.mucRepocitory = mucRepocitory;
        String roomName = JIDUtils.getNodeNick(id);
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
        this.affiliationsViewsJID = getString("affiliationsViewsJID", "admin");

        try {
            Map<String, Affiliation> tmp = new HashMap<String, Affiliation>();
            // this.mucRepocitory.setData(id, "affiliation",
            // JIDUtils.getNodeID(jid)
            String[] jids = this.mucRepocitory.getKeys(id, "affiliation");
            for (String jid : jids) {
                String affName = this.mucRepocitory.getData(id, "affiliation", JIDUtils.getNodeID(jid));
                tmp.put(jid, Affiliation.valueOf(affName));
            }
            this.affiliations.clear();
            this.affiliations.putAll(tmp);
        } catch (Exception e) {
            this.affiliations.put(JIDUtils.getNodeID(constructorJid), Affiliation.OWNER);
        }
    }

    /**
     * @param receiverAffiliation
     * @return
     */
    public boolean affiliationCanViewJid(Affiliation affiliation) {
        if ("owner".equals(this.affiliationsViewsJID)) {
            return affiliation.getWeight() >= Affiliation.OWNER.getWeight();
        } else if ("admin".equals(this.affiliationsViewsJID)) {
            return affiliation.getWeight() >= Affiliation.ADMIN.getWeight();
        } else if ("member".equals(this.affiliationsViewsJID)) {
            return affiliation.getWeight() >= Affiliation.MEMBER.getWeight();
        } else if ("anyone".equals(this.affiliationsViewsJID)) {
            return true;
        }
        return false;
    }

    /**
     * @param data
     * @return
     */
    public boolean checkPassword(String password) {
        return password != null && this.password != null && password.equals(this.password);
    }

    private Element field(String type, String label, String var, Boolean value) {
        String v = null;
        if (value != null && value) {
            v = "1";
        } else if (value != null && !value) {
            v = "0";
        }
        return field(type, label, var, v, null, null);
    }

    private Element field(String type, String label, String var, String value) {
        return field(type, label, var, value, null, null);
    }

    private Element field(String type, String label, String var, String value, String[] labels, String[] values) {
        Element field = new Element("field");
        if (var != null)
            field.setAttribute("var", var);
        if (label != null)
            field.setAttribute("label", label);
        field.setAttribute("type", type);

        Element v = new Element("value");
        if (value != null) {
            v.setCData(value);
        }
        field.addChild(v);

        if (labels != null) {
            for (int i = 0; i < labels.length; i++) {
                Element option = new Element("option");
                option.setAttribute("label", labels[i]);
                Element vo = new Element("value", values[i]);
                option.addChild(vo);
                field.addChild(option);
            }
        }

        return field;
    }

    /**
     * @param reqAffiliation
     * @return
     */
    public Collection<String> findBareJidsByAffiliations(Affiliation reqAffiliation) {
        List<String> result = new ArrayList<String>();
        for (Map.Entry<String, Affiliation> entry : this.affiliations.entrySet()) {
            if (reqAffiliation == entry.getValue()) {
                result.add(entry.getKey());
            }
        }
        return result;
    }

    public void flushConfig() {
        try {
            this.mucRepocitory.setData(id, "roomShortName", roomShortName);
            this.mucRepocitory.setData(id, "roomFullName", roomFullName);
            this.mucRepocitory.setData(id, "affiliationsViewsJID", affiliationsViewsJID);
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

    public Affiliation getAffiliation(String jid) {
        Affiliation result = this.affiliations.get(JIDUtils.getNodeID(jid));
        return result == null ? Affiliation.NONE : result;
    }

    private Boolean getBoolean(String key, Boolean defaultValue) {
        try {
            return Boolean.valueOf(this.mucRepocitory.getData(id, key));
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * @return
     */
    public Form getFormElement() {
        Form x = new Form("form", "Room configuration", "Please configure");
        x.addField(Field.fieldHidden("FORM_TYPE", "http://jabber.org/protocol/muc#roomconfig"));
        String once = UUID.randomUUID().toString();
        x.addField(Field.fieldHidden("once", once));
        x.addField(Field.fieldTextSingle("muc#roomconfig_roomname", "Natural-Language Room Name", this.roomShortName));
        x.addField(Field.fieldTextMulti("muc#roomconfig_roomdesc", "Short Description of Room", this.roomFullName));
        x.addField(Field.fieldBoolean("muc#roomconfig_changesubject", "Allow Occupants to Change Subject",
                this.allowedOccupantChangeSubject));
        x.addField(Field.fieldListSingle("muc#roomconfig_maxusers", "Maximum Number of Room Occupants", String
                .valueOf(this.maxOccupantNumber), new String[] { "1", "10", "20", "30", "50", "100", "150" },
                new String[] { "1", "10", "20", "30", "50", "100", "150" }));
        x.addField(Field.fieldBoolean("muc#roomconfig_publicroom", "Allow Public Searching for Room",
                this.allowedPublicSearch));
        x.addField(Field.fieldBoolean("muc#roomconfig_persistentroom", "Make Room Persistent", this.persist));
        x.addField(Field.fieldBoolean("muc#roomconfig_moderatedroom", "Make Room Moderated", this.moderated));
        x.addField(Field.fieldBoolean("muc#roomconfig_membersonly", "An Invitation is Required to Enter",
                this.invitationRequired));
        x.addField(Field.fieldBoolean("muc#roomconfig_allowinvites", "Allow Occupants to Invite Others",
                this.allowedOccupantsToInvite));
        x.addField(Field.fieldBoolean("muc#roomconfig_passwordprotectedroom", "A Password is required to enter",
                this.passwordRequired));
        x.addField(Field.fieldTextPrivate("muc#roomconfig_roomsecret", "The Room Password", this.password));
        x.addField(Field
                .fieldListSingle("Affiliations that May Discover Real JIDs of Occupants", "muc#roomconfig_whois",
                        this.affiliationsViewsJID, new String[] { "Room Owner and Admins Only",
                                "Room Owner, Admins and Members Only", "Anyone" }, new String[] { "admin", "member",
                                "anyone" }));
        x.addField(Field.fieldBoolean("", "", false));
        x.addField(Field.fieldBoolean("", "", false));
        x.addField(Field.fieldBoolean("muc#roomconfig_enablelogging", "Enable Logging of Room Conversations",
                this.logging));

        return x;
    }

    private Integer getInteger(String key, Integer defaultValue) {
        try {
            return Integer.valueOf(this.mucRepocitory.getData(id, key));
        } catch (Exception e) {
            return defaultValue;
        }
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

    private String getString(String key, String defaultValue) {
        try {
            return this.mucRepocitory.getData(id, key);
        } catch (Exception e) {
            return defaultValue;
        }
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

    /**
     * @param iq
     * @return
     */
    public List<Element> parseConfig(Element iq) {
        List<Element> result = new LinkedList<Element>();
        Element query = iq.getChild("query");
        Element x = query.getChild("x", "jabber:x:data");

        Form form = new Form(x);
        if ("set".equals(iq.getAttribute("type"))) {
            boolean oldPersist = this.persist;
            String once = form.getAsString("once");

            String var;
            var = "muc#roomconfig_whois";
            if (form.is(var)) {
                this.affiliationsViewsJID = form.getAsString(var);
            }
            var = "muc#roomconfig_roomname";
            if (form.is(var)) {
                this.roomShortName = form.getAsString(var);
            }
            var = "muc#roomconfig_roomdesc";
            if (form.is(var)) {
                this.roomFullName = form.getAsString(var);
            }
            var = "muc#roomconfig_changesubject";
            if (form.is(var)) {
                this.allowedOccupantChangeSubject = form.getAsBoolean(var);
            }
            var = "muc#roomconfig_maxusers";
            if (form.is(var)) {
                this.maxOccupantNumber = form.getAsInteger(var);
            }
            var = "muc#roomconfig_publicroom";
            if (form.is(var)) {
                this.allowedPublicSearch = form.getAsBoolean(var);
            }
            var = "muc#roomconfig_persistentroom";
            if (form.is(var)) {
                this.persist = form.getAsBoolean(var);
            }
            var = "muc#roomconfig_moderatedroom";
            if (form.is(var)) {
                this.moderated = form.getAsBoolean(var);
            }
            var = "muc#roomconfig_membersonly";
            if (form.is(var)) {
                this.invitationRequired = form.getAsBoolean(var);
            }
            var = "muc#roomconfig_allowinvites";
            if (form.is(var)) {
                this.allowedOccupantsToInvite = form.getAsBoolean(var);
            }
            var = "muc#roomconfig_passwordprotectedroom";
            if (form.is(var)) {
                this.passwordRequired = form.getAsBoolean(var);
            }
            var = "muc#roomconfig_roomsecret";
            if (form.is(var)) {
                this.password = form.getAsString(var);
            }
            var = "muc#roomconfig_enablelogging";
            if (form.is(var)) {
                this.logging = form.getAsBoolean(var);
            }

            try {
                if (oldPersist != this.persist && this.persist) {
                    this.mucRepocitory.addUser(id);
                } else if (oldPersist != this.persist && !this.persist) {
                    this.mucRepocitory.removeUser(id);
                }
                if (this.persist) {
                    flushConfig();
                }
            } catch (UserExistsException e) {
                e.printStackTrace();
            } catch (TigaseDBException e) {
                e.printStackTrace();
            }

        }
        Element answer = new Element("iq");
        answer.addAttribute("id", iq.getAttribute("id"));
        answer.addAttribute("type", "result");
        answer.addAttribute("to", iq.getAttribute("from"));
        answer.addAttribute("from", this.id);
        result.add(answer);
        return result;
    }

    public void setAffiliation(String jid, Affiliation affiliation) {
        if (affiliation == null || affiliation == Affiliation.NONE) {
            this.affiliations.remove(JIDUtils.getNodeID(jid));
        } else {
            this.affiliations.put(JIDUtils.getNodeID(jid), affiliation);
        }
        if (isPersist()) {
            try {
                if (affiliation == null || affiliation == Affiliation.NONE) {
                    this.mucRepocitory.removeData(id, "affiliation", JIDUtils.getNodeID(jid));
                } else {
                    this.mucRepocitory.setData(id, "affiliation", JIDUtils.getNodeID(jid), affiliation.name());
                }
            } catch (UserNotFoundException e) {
                e.printStackTrace();
            } catch (TigaseDBException e) {
                e.printStackTrace();
            }
        }
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
