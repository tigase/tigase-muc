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
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import tigase.db.UserRepository;
import tigase.util.JID;
import tigase.xml.Element;

/**
 * 
 * <p>
 * Created: 2007-05-15 13:37:55
 * </p>
 * 
 * @author bmalkow
 * @version $Rev$
 */
public class Room implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Key: bareJID
     */
    private Map<String, Affiliation> affiliations = new HashMap<String, Affiliation>();

    private Map<String, Element> lastReceivedPresence = new HashMap<String, Element>();

    private final RoomConfiguration configuration;

    public Affiliation getAffiliation(String jid) {
        return this.affiliations.get(JID.getNodeID(jid));
    }

    public void setAffiliation(String jid, Affiliation affiliation) {
        this.affiliations.put(JID.getNodeID(jid), affiliation);
    }

    /**
     * <realJID, nick>
     */
    private Map<String, String> occupantsByJID = new HashMap<String, String>();

    /**
     * <nick, realJID>
     */
    private Map<String, String> occupantsByNick = new HashMap<String, String>();

    /**
     * Key: bareJID
     */
    private Map<String, Role> roles = new HashMap<String, Role>();

    private String roomID;

    public Room(UserRepository mucRepository, String roomID, String ownerJID) {
        this.roomID = roomID;
        this.configuration = new RoomConfiguration(roomID, mucRepository);
        setAffiliation(JID.getNodeID(ownerJID), Affiliation.OWNER);
    }

    /**
     * @param realJID
     * @return
     */
    private Role calculateRole(String realJID) {
        String bareJID = JID.getNodeID(realJID);
        Affiliation affiliation = getAffiliation(bareJID);
        Role result = configuration.isModerated() ? Role.VISITOR : Role.PARTICIPANT;
        if (affiliation == Affiliation.ADMIN || affiliation == Affiliation.OWNER) {
            return Role.MODERATOR;
        }
        return null;
    }

    private Element preparePresenceSubItem(String jid, String sendingTo) {
        Element item = new Element("item");
        String bareJID = JID.getNodeID(jid);
        // item.setAttribute("affiliation", nick);
        Role occupantRole = this.roles.get(bareJID);
        if (occupantRole != null) {
            item.setAttribute("role", occupantRole.name().toLowerCase());
        }

        Affiliation occupantAffiliation = getAffiliation(bareJID);
        item.setAttribute("affiliation", occupantAffiliation == null ? "none" : occupantAffiliation.name()
                .toLowerCase());

        Affiliation receiverAffiliation = getAffiliation(JID.getNodeID(sendingTo));
        if (receiverAffiliation != null && configuration.getAffiliationsViewsJID().contains(receiverAffiliation)) {
            item.setAttribute("jid", jid);
        }

        return item;
    }

    private List<Element> processChangingNickname(String realJID, String oldNick, Element element) {
        String newNick = JID.getNodeResource(element.getAttribute("to"));
        List<Element> result = new LinkedList<Element>();

        if (this.occupantsByNick.containsKey(newNick)) {
            result.add(MUCService.errorPresence(roomID, realJID, "cancel", "409", "conflict"));
            return result;
        }

        // Service Sends New Occupant's Presence to All Occupants
        for (Entry<String, String> entry : this.occupantsByNick.entrySet()) {
            Element presence = element.clone();
            presence.setAttribute("to", entry.getValue());
            presence.setAttribute("from", roomID + "/" + oldNick);
            presence.setAttribute("type", "unavailable");

            Element x = new Element("x");
            x.setAttribute("xmlns", "http://jabber.org/protocol/muc#user");

            Element item = preparePresenceSubItem(realJID, entry.getValue());
            x.addChild(item);
            item.setAttribute("nick", newNick);

            x.addChild(new Element("status", new String[] { "code" }, new String[] { "303" }));
            if (entry.getValue().equals(realJID)) {
                x.addChild(new Element("status", new String[] { "code" }, new String[] { "110" }));
            }
            presence.addChild(x);

            result.add(presence);
        }
        this.occupantsByNick.remove(oldNick);
        this.occupantsByJID.remove(realJID);

        this.occupantsByNick.put(newNick, realJID);
        this.occupantsByJID.put(realJID, newNick);

        for (Entry<String, String> entry : this.occupantsByNick.entrySet()) {
            Element presence = element.clone();
            presence.setAttribute("to", entry.getValue());
            presence.setAttribute("from", roomID + "/" + newNick);

            Element x = new Element("x");
            Element item = preparePresenceSubItem(realJID, entry.getValue());
            x.addChild(item);

            x.setAttribute("xmlns", "http://jabber.org/protocol/muc#user");
            if (entry.getValue().equals(realJID)) {
                x.addChild(new Element("status", new String[] { "code" }, new String[] { "110" }));
            }
            presence.addChild(x);

            result.add(presence);
        }

        return result;
    }

    private List<Element> processEnteringToRoom(String realJID, String nick, Element element) {
        List<Element> result = new LinkedList<Element>();

        if (this.occupantsByNick.containsKey(nick)) {
            result.add(MUCService.errorPresence(roomID, realJID, "cancel", "409", "conflict"));
            return result;
        }
        // Service Sends Presence from Existing Occupants to New Occupant
        for (Entry<String, String> entry : this.occupantsByNick.entrySet()) {
            Element presence = this.lastReceivedPresence.get(entry.getValue()).clone();
            presence.setAttribute("to", realJID);
            presence.setAttribute("from", roomID + "/" + entry.getKey());

            Element x = new Element("x");
            x.setAttribute("xmlns", "http://jabber.org/protocol/muc#user");
            x.addChild(preparePresenceSubItem(entry.getValue(), realJID));
            if (entry.getValue().equals(realJID)) {
                x.addChild(new Element("status", new String[] { "code" }, new String[] { "110" }));
            }
            presence.addChild(x);

            result.add(presence);
        }
        this.occupantsByNick.put(nick, realJID);
        this.occupantsByJID.put(realJID, nick);
        Role occupantRole = calculateRole(realJID);
        this.roles.put(JID.getNodeID(realJID), occupantRole);
        // Service Sends Presence from Existing Occupants to New Occupant
        for (Entry<String, String> entry : this.occupantsByNick.entrySet()) {
            Element presence = element.clone();
            presence.setAttribute("to", entry.getValue());
            presence.setAttribute("from", roomID + "/" + nick);

            Element x = new Element("x");
            x.setAttribute("xmlns", "http://jabber.org/protocol/muc#user");
            x.addChild(preparePresenceSubItem(realJID, entry.getValue()));
            if (entry.getValue().equals(realJID)) {
                x.addChild(new Element("status", new String[] { "code" }, new String[] { "110" }));
            }

            presence.addChild(x);

            result.add(presence);
        }
        return result;
    }

    private List<Element> processExitingARoom(String realJID, String nick, Element element) {
        List<Element> result = new LinkedList<Element>();
        // Service Sends New Occupant's Presence to All Occupants
        for (Entry<String, String> entry : this.occupantsByNick.entrySet()) {
            Element presence = element.clone();
            presence.setAttribute("to", entry.getValue());
            presence.setAttribute("from", roomID + "/" + nick);

            Element x = new Element("x");
            x.setAttribute("xmlns", "http://jabber.org/protocol/muc#user");
            if (entry.getValue().equals(realJID)) {
                x.addChild(new Element("status", new String[] { "code" }, new String[] { "110" }));
            }
            presence.addChild(x);

            result.add(presence);
        }
        this.occupantsByNick.remove(nick);
        this.occupantsByJID.remove(realJID);
        this.roles.remove(JID.getNodeID(realJID));

        return result;
    }

    private List<Element> processMessage(Element element) {
        String senderNick = this.occupantsByJID.get(element.getAttribute("from"));
        String recipentNick = JID.getNodeResource(element.getAttribute("to"));
        System.out.println("-|" + element);
        List<Element> result = new LinkedList<Element>();
        if (recipentNick == null) {
            // broadcast message
            for (Entry<String, String> entry : this.occupantsByNick.entrySet()) {
                Element message = element.clone();
                message.setAttribute("from", this.roomID + "/" + senderNick);
                message.setAttribute("to", entry.getValue());
                result.add(message);
            }
        } else {
            // private message
            String recipentJID = this.occupantsByNick.get(recipentNick);
            Element message = element.clone();
            message.setAttribute("from", this.roomID + "/" + senderNick);
            message.setAttribute("to", recipentJID);
            result.add(message);
        }
        return result;
    }

    private List<Element> processNewPresenceStatus(String realJID, String nick, Element element) {
        List<Element> result = new LinkedList<Element>();
        for (Entry<String, String> entry : this.occupantsByNick.entrySet()) {
            Element presence = element.clone();
            presence.setAttribute("to", entry.getValue());
            presence.setAttribute("from", roomID + "/" + nick);

            Element x = new Element("x");
            x.setAttribute("xmlns", "http://jabber.org/protocol/muc#user");
            x.addChild(preparePresenceSubItem(realJID, entry.getValue()));
            if (entry.getValue().equals(realJID)) {
                x.addChild(new Element("status", new String[] { "code" }, new String[] { "110" }));
            }

            presence.addChild(x);

            result.add(presence);
        }
        return result;
    }

    private List<Element> processPresence(Element element) {
        System.out.println("P:" + element);
        String realJID = element.getAttribute("from");
        String nick = JID.getNodeResource(element.getAttribute("to"));

        String existNick = this.occupantsByJID.get(realJID);

        this.lastReceivedPresence.put(realJID, element);

        if (existNick != null && !existNick.equals(nick)) {
            return processChangingNickname(realJID, existNick, element);
        } else if ("unavailable".equals(element.getAttribute("type"))) {
            return processExitingARoom(realJID, nick, element);
        } else if (existNick != null && existNick.equals(nick)) {
            return processNewPresenceStatus(realJID, nick, element);
        } else {
            return processEnteringToRoom(realJID, nick, element);
        }
    }

    public List<Element> processStanza(Element element) {

        if ("message".equals(element.getName())) {
            return processMessage(element);
        } else if ("presence".equals(element.getName())) {
            return processPresence(element);
        }

        return null;
    }

    private void removeOccupantByJID(String jid) {
        String nick = this.occupantsByJID.remove(jid);
        this.occupantsByNick.remove(nick);
        this.roles.remove(jid);
    }

}
