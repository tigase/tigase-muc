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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;

import tigase.db.TigaseDBException;
import tigase.db.UserExistsException;
import tigase.db.UserNotFoundException;
import tigase.db.UserRepository;
import tigase.form.Field;
import tigase.form.Form;
import tigase.muc.xmpp.JID;
import tigase.util.JIDUtils;
import tigase.xml.Element;

/**
 * 
 * <p>
 * Created: 2007-05-17 12:09:45
 * </p>
 * 
 * @author bmalkow
 * @version $Rev:43 $
 */
public class RoomConfiguration implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Key: bareJID
     */
    private Map<JID, Affiliation> affiliations = new HashMap<JID, Affiliation>();

    /**
     * Room ID
     */
    private String id;

    private Logger log = Logger.getLogger(this.getClass().getName());

    private UserRepository mucRepocitory;

    /**
     * Allow Occupants to Invite Others.
     */
    private boolean roomconfigAllowInvites;

    /**
     * Allow Occupants to Change Subject.
     */
    private boolean roomconfigChangeSubject;

    /**
     * Enable Logging of Room Conversations.
     */
    private boolean roomconfigEnableLogging;

    /**
     * Maximum Number of Room Occupants.
     */
    private int roomconfigMaxUsers;

    /**
     * An Invitation is Required to Enter.
     */
    private boolean roomconfigMembersOnly;

    /**
     * Make Room Moderated.
     */
    private boolean roomconfigModeratedRoom = false;

    /**
     * A Password is required to enter.
     */
    private boolean roomconfigPasswordProtectedRoom;

    /**
     * Make Room Persistent.
     */
    private boolean roomconfigPersistentRoom;

    /**
     * Allow Public Searching for Room.
     */
    private boolean roomconfigPublicRoom;

    /**
     * private String roomName.
     */
    private String roomconfigRoomdesc;

    /**
     * Short Description of Room.
     */
    private String roomconfigRoomname;

    /**
     * The Room Password.
     */
    private String roomconfigRoomSecret;

    /**
     * Affiliations that May Discover Real JIDs of Occupants.
     */
    private String roomconfigWhois = "";

    RoomConfiguration(String namespace, String id, UserRepository mucRepocitory) {
        this.namespace = namespace;
        this.id = id;
        this.mucRepocitory = mucRepocitory;
        String roomName = JIDUtils.getNodeNick(id);

        defaultSettings(roomName);
        try {
            restoreConfiguration();
        } catch (Exception e) {
            log.info("Room [" + this.id + "] not found in database.");
            throw new RuntimeException("Room [" + this.id + "] not found in database. Config object not created.", e);
        }
    }

    private void defaultSettings(final String roomName) {
        this.roomconfigRoomdesc = roomName;
        this.roomconfigRoomname = roomName;
        this.roomconfigChangeSubject = true;
        this.roomconfigAllowInvites = true;
        this.roomconfigPublicRoom = true;
        this.roomconfigMembersOnly = false;
        this.roomconfigEnableLogging = false;
        this.roomconfigMaxUsers = 100;
        this.roomconfigModeratedRoom = true;
        this.roomconfigRoomSecret = null;
        this.roomconfigPasswordProtectedRoom = false;
        this.roomconfigPersistentRoom = false;
        this.roomconfigWhois = "admin";
    }

    RoomConfiguration(String namespace, String id, UserRepository mucRepocitory, JID constructorJid) {
        this.namespace = namespace;
        this.id = id;
        this.mucRepocitory = mucRepocitory;
        String roomName = JIDUtils.getNodeNick(id);

        defaultSettings(roomName);
        this.affiliations.put(constructorJid.getBareJID(), Affiliation.OWNER);

        try {
            restoreConfiguration();
        } catch (UserNotFoundException e) {
            log.info("Room [" + this.id + "] not found in database. Using defaults.");
        } catch (TigaseDBException e) {
            e.printStackTrace();
        }
    }

    private String namespace;

    private void restoreConfiguration() throws UserNotFoundException, TigaseDBException {
        String[] keysTable = this.mucRepocitory.getKeys(namespace, this.id);
        if (keysTable == null) {
            return;
        }
        Set<String> keys = new HashSet<String>();
        for (String key : keysTable) {
            log.finest(" Found config key: " + key);
            if (!keys.add(key)) {
                log.log(Level.SEVERE, "Duplicated config key for room " + id + ", key: " + key);
            }
        }

        String var;

        var = "roomconfigRoomname";
        if (keys.contains(var)) {
            this.roomconfigRoomname = getString(var);
        }

        var = "roomconfigChangeSubject";
        if (keys.contains(var))
            this.roomconfigChangeSubject = getBoolean(var);
        var = "roomconfigMaxUsers";
        if (keys.contains(var))
            this.roomconfigMaxUsers = getInteger(var);
        var = "roomconfigPublicRoom";
        if (keys.contains(var))
            this.roomconfigPublicRoom = getBoolean(var);
        var = "roomconfigPersistentRoom";
        if (keys.contains(var))
            this.roomconfigPersistentRoom = getBoolean(var);
        var = "roomconfigModeratedRoom";
        if (keys.contains(var))
            this.roomconfigModeratedRoom = getBoolean(var);
        var = "roomconfigMembersOnly";
        if (keys.contains(var))
            this.roomconfigMembersOnly = getBoolean(var);
        var = "roomconfigAllowInvites";
        if (keys.contains(var))
            this.roomconfigAllowInvites = getBoolean(var);
        var = "roomconfigPasswordProtectedRoom";
        if (keys.contains(var))
            this.roomconfigPasswordProtectedRoom = getBoolean(var);
        var = "roomconfigRoomSecret";
        if (keys.contains(var))
            this.roomconfigRoomSecret = getString(var);
        var = "roomconfigWhois";
        if (keys.contains(var))
            this.roomconfigWhois = getString(var);
        var = "roomconfigEnableLogging";
        if (keys.contains(var))
            this.roomconfigEnableLogging = getBoolean(var);

        Map<JID, Affiliation> tmp = new HashMap<JID, Affiliation>();
        String[] jids = this.mucRepocitory.getKeys(namespace, id + "/affiliation");
        if (jids != null) {
            String l = "";
            for (String jid : jids) {
                String affName = this.mucRepocitory.getData(namespace, id + "/affiliation", JIDUtils.getNodeID(jid));
                JID j = JID.fromString(jid);
                Affiliation a = Affiliation.valueOf(affName);
                tmp.put(j, a);
                l += j + "(" + a + "); ";
            }
            log.finest("Reading room affiliations: " + l);

            this.affiliations.clear();
            this.affiliations.putAll(tmp);
        }
    }

    /**
     * @param receiverAffiliation
     * @return
     */
    public boolean affiliationCanViewJid(Affiliation affiliation) {
        if ("owner".equals(this.roomconfigWhois)) {
            return affiliation.getWeight() >= Affiliation.OWNER.getWeight();
        } else if ("admin".equals(this.roomconfigWhois)) {
            return affiliation.getWeight() >= Affiliation.ADMIN.getWeight();
        } else if ("member".equals(this.roomconfigWhois)) {
            return affiliation.getWeight() >= Affiliation.MEMBER.getWeight();
        } else if ("anyone".equals(this.roomconfigWhois)) {
            return true;
        }
        return false;
    }

    /**
     * @param data
     * @return
     */
    public boolean checkPassword(String password) {
        return password != null && this.roomconfigRoomSecret != null && password.equals(this.roomconfigRoomSecret);
    }

    /**
     * @param reqAffiliation
     * @return
     */
    public Collection<JID> findBareJidsByAffiliations(Affiliation reqAffiliation) {
        List<JID> result = new ArrayList<JID>();
        for (Map.Entry<JID, Affiliation> entry : this.affiliations.entrySet()) {
            if (reqAffiliation == entry.getValue()) {
                result.add(entry.getKey());
            }
        }
        return result;
    }

    public void flushConfig() {
        log.info("Storing room configuration...");
        try {
            if (roomconfigRoomname != null)
                this.mucRepocitory.setData(namespace, id, "roomconfigRoomname", roomconfigRoomname);
            if (roomconfigRoomdesc != null)
                this.mucRepocitory.setData(namespace, id, "roomconfigRoomdesc", roomconfigRoomdesc);
            if (roomconfigWhois != null)
                this.mucRepocitory.setData(namespace, id, "roomconfigWhois", roomconfigWhois);
            this.mucRepocitory.setData(namespace, id, "roomconfigChangeSubject", Boolean
                    .toString(roomconfigChangeSubject));
            this.mucRepocitory.setData(namespace, id, "roomconfigAllowInvites", Boolean
                    .toString(roomconfigAllowInvites));
            this.mucRepocitory.setData(namespace, id, "roomconfigPublicRoom", Boolean.toString(roomconfigPublicRoom));
            this.mucRepocitory.setData(namespace, id, "roomconfigMembersOnly", Boolean.toString(roomconfigMembersOnly));
            this.mucRepocitory.setData(namespace, id, "roomconfigEnableLogging", Boolean
                    .toString(roomconfigEnableLogging));
            this.mucRepocitory.setData(namespace, id, "roomconfigModeratedRoom", Boolean
                    .toString(roomconfigModeratedRoom));
            this.mucRepocitory.setData(namespace, id, "roomconfigPersistentRoom", Boolean
                    .toString(roomconfigPersistentRoom));
            this.mucRepocitory.setData(namespace, id, "roomconfigPasswordProtectedRoom", Boolean
                    .toString(roomconfigPasswordProtectedRoom));
            this.mucRepocitory.setData(namespace, id, "roomconfigMaxUsers", Integer.toString(roomconfigMaxUsers));
            if (roomconfigRoomSecret != null)
                this.mucRepocitory.setData(namespace, id, "roomconfigRoomSecret", roomconfigRoomSecret);

            for (Entry<JID, Affiliation> entry : this.affiliations.entrySet()) {
                this.mucRepocitory.setData(namespace, id + "/affiliation", entry.getKey().getBareJID().toString(),
                        entry.getValue().name());
            }

        } catch (Exception e) {
            log.log(Level.SEVERE, "Error on storing room [" + this.id + "] configuration", e);
        }
    }

    public Affiliation getAffiliation(JID jid) {
        Affiliation result = this.affiliations.get(jid.getBareJID());
        return result == null ? Affiliation.NONE : result;
    }

    private Boolean getBoolean(String key) throws UserNotFoundException, TigaseDBException {
        Boolean val = Boolean.valueOf(this.mucRepocitory.getData(namespace, id, key));
        log.finest("Read from repository key " + key + " == " + val);
        return val;
    }

    /**
     * @return
     */
    public Form getFormElement() {
        Form x = new Form("form", "Room configuration", "Please configure");
        x.addField(Field.fieldHidden("FORM_TYPE", "http://jabber.org/protocol/muc#roomconfig"));
        String once = UUID.randomUUID().toString();
        x.addField(Field.fieldHidden("once", once));
        x.addField(Field.fieldTextSingle("muc#roomconfig_roomname", this.roomconfigRoomname,
                "Natural-Language Room Name"));
        x.addField(Field
                .fieldTextMulti("muc#roomconfig_roomdesc", this.roomconfigRoomdesc, "Short Description of Room"));
        x.addField(Field.fieldBoolean("muc#roomconfig_changesubject", this.roomconfigChangeSubject,
                "Allow Occupants to Change Subject"));
        x.addField(Field.fieldListSingle("muc#roomconfig_maxusers", String.valueOf(this.roomconfigMaxUsers),
                "Maximum Number of Room Occupants", new String[] { "1", "10", "20", "30", "50", "100", "150" },
                new String[] { "1", "10", "20", "30", "50", "100", "150" }));
        x.addField(Field.fieldBoolean("muc#roomconfig_publicroom", this.roomconfigPublicRoom,
                "Allow Public Searching for Room"));
        x.addField(Field.fieldBoolean("muc#roomconfig_persistentroom", this.roomconfigPersistentRoom,
                "Make Room Persistent"));
        x.addField(Field.fieldBoolean("muc#roomconfig_moderatedroom", this.roomconfigModeratedRoom,
                "Make Room Moderated"));
        x.addField(Field.fieldBoolean("muc#roomconfig_membersonly", this.roomconfigMembersOnly,
                "An Invitation is Required to Enter"));
        x.addField(Field.fieldBoolean("muc#roomconfig_allowinvites", this.roomconfigAllowInvites,
                "Allow Occupants to Invite Others"));
        x.addField(Field.fieldBoolean("muc#roomconfig_passwordprotectedroom", this.roomconfigPasswordProtectedRoom,
                "A Password is required to enter"));
        x.addField(Field.fieldTextPrivate("muc#roomconfig_roomsecret", this.roomconfigRoomSecret, "The Room Password"));
        x.addField(Field
                .fieldListSingle("muc#roomconfig_whois", this.roomconfigWhois,
                        "Affiliations that May Discover Real JIDs of Occupants", new String[] {
                                "Room Owner and Admins Only", "Room Owner, Admins and Members Only", "Anyone" },
                        new String[] { "admin", "member", "anyone" }));
        x.addField(Field.fieldBoolean("muc#roomconfig_enablelogging", this.roomconfigEnableLogging,
                "Enable Logging of Room Conversations"));

        return x;
    }

    private Integer getInteger(String key) throws NumberFormatException, UserNotFoundException, TigaseDBException {
        String v = this.mucRepocitory.getData(namespace, id, key);
        log.finest("Read from repository key " + key + " == " + v);
        Integer val = Integer.valueOf(v);
        return val;
    }

    public int getRoomconfigMaxUsers() {
        return roomconfigMaxUsers;
    }

    public String getRoomconfigRoomdesc() {
        return roomconfigRoomdesc;
    }

    public String getRoomconfigRoomname() {
        return roomconfigRoomname;
    }

    public String getRoomconfigRoomSecret() {
        return roomconfigRoomSecret;
    }

    private String getString(String key) throws UserNotFoundException, TigaseDBException {
        String val = this.mucRepocitory.getData(namespace, id, key);
        log.finest("Read from repository key " + key + " == " + val);
        return val;
    }

    public boolean isRoomconfigAllowInvites() {
        return roomconfigAllowInvites;
    }

    public boolean isRoomconfigChangeSubject() {
        return roomconfigChangeSubject;
    }

    public boolean isRoomconfigEnableLogging() {
        return roomconfigEnableLogging;
    }

    public boolean isRoomconfigMembersOnly() {
        return roomconfigMembersOnly;
    }

    public boolean isRoomconfigModeratedRoom() {
        return roomconfigModeratedRoom;
    }

    public boolean isRoomconfigPasswordProtectedRoom() {
        return roomconfigPasswordProtectedRoom;
    }

    public boolean isRoomconfigPersistentRoom() {
        return roomconfigPersistentRoom;
    }

    public boolean isRoomconfigPublicRoom() {
        return roomconfigPublicRoom;
    }

    /**
     * @param iq
     * @return
     */
    public boolean parseConfig(Element x ) {
        List<Element> result = new LinkedList<Element>();
        //Element query = iq.getChild("query");
        //Element x = query.getChild("x", "jabber:x:data");

        Form form = new Form(x);
            boolean oldPersist = this.roomconfigPersistentRoom;
            String once = form.getAsString("once");

            String var;
            var = "muc#roomconfig_whois";
            if (form.is(var)) {
                String val = form.getAsString(var);
                this.roomconfigWhois = val;
                log.finest("Set variable " + var + " to " + val);
            }
            var = "muc#roomconfig_roomname";
            if (form.is(var)) {
                this.roomconfigRoomname = form.getAsString(var);
            }
            var = "muc#roomconfig_roomdesc";
            if (form.is(var)) {
                this.roomconfigRoomdesc = form.getAsString(var);
            }
            var = "muc#roomconfig_changesubject";
            if (form.is(var)) {
                this.roomconfigChangeSubject = form.getAsBoolean(var);
            }
            var = "muc#roomconfig_maxusers";
            if (form.is(var)) {
                this.roomconfigMaxUsers = form.getAsInteger(var);
            }
            var = "muc#roomconfig_publicroom";
            if (form.is(var)) {
                this.roomconfigPublicRoom = form.getAsBoolean(var);
            }
            var = "muc#roomconfig_persistentroom";
            if (form.is(var)) {
                this.roomconfigPersistentRoom = form.getAsBoolean(var);
            }
            var = "muc#roomconfig_moderatedroom";
            if (form.is(var)) {
                this.roomconfigModeratedRoom = form.getAsBoolean(var);
            }
            var = "muc#roomconfig_membersonly";
            if (form.is(var)) {
                this.roomconfigMembersOnly = form.getAsBoolean(var);
            }
            var = "muc#roomconfig_allowinvites";
            if (form.is(var)) {
                this.roomconfigAllowInvites = form.getAsBoolean(var);
            }
            var = "muc#roomconfig_passwordprotectedroom";
            if (form.is(var)) {
                this.roomconfigPasswordProtectedRoom = form.getAsBoolean(var);
            }
            var = "muc#roomconfig_roomsecret";
            if (form.is(var)) {
                this.roomconfigRoomSecret = form.getAsString(var);
            }
            var = "muc#roomconfig_enablelogging";
            if (form.is(var)) {
                this.roomconfigEnableLogging = form.getAsBoolean(var);
            }

            try {
                if (oldPersist != this.roomconfigPersistentRoom && this.roomconfigPersistentRoom) {
                } else if (oldPersist != this.roomconfigPersistentRoom && !this.roomconfigPersistentRoom) {
                    this.mucRepocitory.removeSubnode(namespace, id);
                }
                if (this.roomconfigPersistentRoom) {
                    flushConfig();
                }
            } catch (UserExistsException e) {
                e.printStackTrace();
            } catch (TigaseDBException e) {
                e.printStackTrace();
            }

        return true;
    }

    public void setAffiliation(JID jid, Affiliation affiliation) {
        if (affiliation == null || affiliation == Affiliation.NONE) {
            this.affiliations.remove(jid.getBareJID());
        } else {
            this.affiliations.put(jid.getBareJID(), affiliation);
        }
        if (isRoomconfigPersistentRoom()) {
            try {
                if (affiliation == null || affiliation == Affiliation.NONE) {
                    this.mucRepocitory.removeData(namespace, id + "/affiliation", jid.getBareJID().toString());
                } else {
                    this.mucRepocitory.setData(namespace, id + "/affiliation", jid.getBareJID().toString(), affiliation
                            .name());
                }
            } catch (UserNotFoundException e) {
                e.printStackTrace();
            } catch (TigaseDBException e) {
                e.printStackTrace();
            }
        }
    }

    public void setRoomconfigAllowInvites(boolean allowedOccupantsToInvite) {
        this.roomconfigAllowInvites = allowedOccupantsToInvite;
    }

    public void setRoomconfigChangeSubject(boolean allowedOccupantChangeSubject) {
        this.roomconfigChangeSubject = allowedOccupantChangeSubject;
    }

    public void setRoomconfigEnableLogging(boolean logging) {
        this.roomconfigEnableLogging = logging;
    }

    public void setRoomconfigMaxUsers(int maxOccupantNumber) {
        this.roomconfigMaxUsers = maxOccupantNumber;
    }

    public void setRoomconfigMembersOnly(boolean invitationRequired) {
        this.roomconfigMembersOnly = invitationRequired;
    }

    public void setRoomconfigModeratedRoom(boolean moderated) {
        this.roomconfigModeratedRoom = moderated;
    }

    public void setRoomconfigPasswordProtectedRoom(boolean passwordRequired) {
        this.roomconfigPasswordProtectedRoom = passwordRequired;
    }

    public void setRoomconfigPersistentRoom(boolean persist) {
        this.roomconfigPersistentRoom = persist;
    }

    public void setRoomconfigPublicRoom(boolean allowedPublicSearch) {
        this.roomconfigPublicRoom = allowedPublicSearch;
    }

    public void setRoomconfigRoomdesc(String roomFullName) {
        this.roomconfigRoomdesc = roomFullName;
    }

    public void setRoomconfigRoomname(String roomShortName) {
        this.roomconfigRoomname = roomShortName;
    }

    public void setRoomconfigRoomSecret(String password) {
        this.roomconfigRoomSecret = password;
    }

    /**
     * @return Returns the id.
     */
    public String getId() {
        return id;
    }
}