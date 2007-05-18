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

    private final RoomConfiguration configuration;

    private Map<String, Element> lastReceivedPresence = new HashMap<String, Element>();

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
        this.configuration = new RoomConfiguration(roomID, mucRepository, ownerJID);
    }

    /**
     * @param realJID
     * @return
     */
    private Role calculateInitialRole(String realJID) {
        String bareJID = JID.getNodeID(realJID);
        Affiliation affiliation = this.configuration.getAffiliation(bareJID);
        Role result;
        result = configuration.isModerated() || configuration.isOccupantDefaultParticipant() ? Role.VISITOR : Role.PARTICIPANT;
        if (affiliation == Affiliation.ADMIN || affiliation == Affiliation.OWNER) {
            return Role.MODERATOR;
        } else if (affiliation == Affiliation.MEMBER) {
            return Role.PARTICIPANT;
        }
        return result;
    }

    private Element preparePresenceSubItem(String jid, String sendingTo) {
        Element item = new Element("item");
        String bareJID = JID.getNodeID(jid);
        // item.setAttribute("affiliation", nick);
        Role occupantRole = this.roles.get(bareJID);
        item.setAttribute("role", occupantRole == null ? "none" : occupantRole.name().toLowerCase());

        Affiliation occupantAffiliation = this.configuration.getAffiliation(bareJID);
        item.setAttribute("affiliation", occupantAffiliation == null ? "none" : occupantAffiliation.name()
                .toLowerCase());

        Affiliation receiverAffiliation = this.configuration.getAffiliation(JID.getNodeID(sendingTo));
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
        Role occupantRole = calculateInitialRole(realJID);
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

    private List<Element> processIq(Element element) {
        List<Element> result = new LinkedList<Element>();
        System.out.println("I:" + element);
        Element query = element.getChild("query", "http://jabber.org/protocol/muc#admin");
        Element item = query.getChild("item");

        String nick = item.getAttribute("nick");
        String role = item.getAttribute("role");

        String jid = item.getAttribute("jid");
        String affiliation = item.getAttribute("affiliation");

        if (nick != null && role != null && role.equals("none")) {
            jid = this.occupantsByNick.get(nick);
            return processIqKick(jid, element);
        } else if (nick != null && role != null) {
            // changing role
            jid = this.occupantsByNick.get(nick);
            Role r = Role.valueOf(role.toUpperCase());
            this.roles.put(JID.getNodeID(jid), r);
            publishNewPresence(result, jid);
            Element answer = new Element("iq");
            answer.addAttribute("id", element.getAttribute("id"));
            answer.addAttribute("type", "result");
            answer.addAttribute("to", element.getAttribute("from"));
            answer.addAttribute("from", roomID);

            result.add(answer);
        } else if (jid != null && affiliation != null) {
            // changing affiliation
            return processIqChangeAffiliation(element);
        }

        return result;
    }

    private List<Element> processIqChangeAffiliation(Element element) {
        Element queryR = element.getChild("query", "http://jabber.org/protocol/muc#admin");
        Element itemR = queryR.getChild("item");
        List<Element> result = new LinkedList<Element>();
        Element reason = itemR.getChild("reason");

        String affiliation = itemR.getAttribute("affiliation");

        Affiliation aff = "none".equals(affiliation) ? null : Affiliation.valueOf(affiliation.toUpperCase());

        String whoSendCommand = element.getAttribute("from");

        String modifiedBareJID = JID.getNodeID(itemR.getAttribute("jid"));

        List<String> modifiedsJIDs = new LinkedList<String>();
        for (String jid : this.occupantsByJID.keySet()) {
            if (modifiedBareJID.equals(JID.getNodeID(jid))) {
                modifiedsJIDs.add(jid);
            }
        }

        this.configuration.setAffiliation(modifiedBareJID, aff);

        for (String jid : modifiedsJIDs) {
            String nick = this.occupantsByJID.get(jid);
            {
                Element kick = this.lastReceivedPresence.get(jid);
                kick = kick == null ? new Element("presence") : kick.clone();
                kick.setAttribute("from", roomID + "/" + nick);
                kick.setAttribute("to", jid);
                if ("outcast".equals(affiliation)) {
                    kick.setAttribute("type", "unavailable");
                }
                Element x = new Element("x");
                kick.addChild(x);
                x.setAttribute("xmlns", "http://jabber.org/protocol/muc#user");
                Element item = preparePresenceSubItem(jid, jid);
                item.addChild(new Element("actor", new String[] { "jid" },
                        new String[] { JID.getNodeID(whoSendCommand) }));
                if (reason != null) {
                    item.addChild(reason);
                }
                x.addChild(item);
                if ("outcast".equals(affiliation)) {
                    x.addChild(new Element("status", new String[] { "code" }, new String[] { "301" }));
                }
                x.addChild(new Element("status", new String[] { "code" }, new String[] { "110" }));
                result.add(kick);
            }

            if ("outcast".equals(affiliation)) {
                removeOccupantByJID(jid);
            }

            // Service Informs Remaining Occupants
            for (Entry<String, String> entry : this.occupantsByNick.entrySet()) {
                if (jid.equals(entry.getValue()))
                    continue;
                Element kick = this.lastReceivedPresence.get(jid);
                kick = kick == null ? new Element("presence") : kick.clone();
                kick.setAttribute("from", roomID + "/" + nick);
                kick.setAttribute("to", entry.getValue());
                if ("outcast".equals(affiliation)) {
                    kick.setAttribute("type", "unavailable");
                }
                Element x = new Element("x");
                kick.addChild(x);
                x.setAttribute("xmlns", "http://jabber.org/protocol/muc#user");
                Element item = preparePresenceSubItem(jid, entry.getValue());
                if (reason != null) {
                    item.addChild(reason);
                }

                x.addChild(item);
                if ("outcast".equals(affiliation)) {
                    x.addChild(new Element("status", new String[] { "code" }, new String[] { "301" }));
                }
                result.add(kick);
            }

        }

        // Service Informs Admin or Owner of Success
        Element answer = new Element("iq");
        answer.addAttribute("id", element.getAttribute("id"));
        answer.addAttribute("type", "result");
        answer.addAttribute("to", element.getAttribute("from"));
        answer.addAttribute("from", roomID);
        result.add(answer);

        return result;
    }

    private List<Element> processIqKick(String kickedJID, Element element) {
        List<Element> result = new LinkedList<Element>();

        Element query = element.getChild("query", "http://jabber.org/protocol/muc#admin");
        Element itemReceived = query.getChild("item");
        Element reason = itemReceived.getChild("reason");

        String nick = itemReceived.getAttribute("nick");
        String whoKickJid = element.getAttribute("from");
        String whoKickNick = this.occupantsByJID.get(kickedJID);

        // Service Removes Kicked Occupant
        {
            Element kick = new Element("presence");
            kick.setAttribute("from", roomID + "/" + nick);
            kick.setAttribute("to", kickedJID);
            kick.setAttribute("type", "unavailable");
            Element x = new Element("x");
            kick.addChild(x);
            x.setAttribute("xmlns", "http://jabber.org/protocol/muc#user");
            Element item = preparePresenceSubItem(kickedJID, kickedJID);
            item.addChild(new Element("actor", new String[] { "jid" }, new String[] { JID.getNodeID(whoKickJid) }));
            if (reason != null) {
                item.addChild(reason);
            }
            x.addChild(item);
            x.addChild(new Element("status", new String[] { "code" }, new String[] { "307" }));
            x.addChild(new Element("status", new String[] { "code" }, new String[] { "110" }));
            result.add(kick);
        }

        removeOccupantByJID(kickedJID);

        // Service Informs Moderator of Success
        Element answer = new Element("iq");
        answer.addAttribute("id", element.getAttribute("id"));
        answer.addAttribute("type", "result");
        answer.addAttribute("to", element.getAttribute("from"));
        answer.addAttribute("from", roomID);
        result.add(answer);

        // Service Informs Remaining Occupants
        for (Entry<String, String> entry : this.occupantsByNick.entrySet()) {
            Element kick = new Element("presence");
            kick.setAttribute("from", roomID + "/" + nick);
            kick.setAttribute("to", entry.getValue());
            kick.setAttribute("type", "unavailable");
            Element x = new Element("x");
            kick.addChild(x);
            x.setAttribute("xmlns", "http://jabber.org/protocol/muc#user");
            Element item = preparePresenceSubItem(kickedJID, entry.getValue());
            if (reason != null) {
                item.addChild(reason);
            }
            x.addChild(item);
            x.addChild(new Element("status", new String[] { "code" }, new String[] { "307" }));
            result.add(kick);
        }

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
        } else if ("iq".equals(element.getName())) {
            return processIq(element);
        }

        return null;
    }

    private void publishNewPresence(List<Element> out, String jid, String... additionalStatuses) {
        String nick = this.occupantsByJID.get(jid);
        for (Entry<String, String> entry : this.occupantsByNick.entrySet()) {
            Element presence = this.lastReceivedPresence.get(jid).clone();
            presence.setAttribute("to", entry.getValue());
            presence.setAttribute("from", roomID + "/" + nick);

            Element x = new Element("x");
            x.setAttribute("xmlns", "http://jabber.org/protocol/muc#user");
            Element item = preparePresenceSubItem(jid, entry.getValue());
            item.addChild(new Element("reason", "bo kurwa takk cgcuiał"));
            x.addChild(item);
            if (additionalStatuses != null) {
                for (String code : additionalStatuses) {
                    x.addChild(new Element("status", new String[] { "code" }, new String[] { code }));
                }
            }
            if (entry.getValue().equals(jid)) {
                x.addChild(new Element("status", new String[] { "code" }, new String[] { "110" }));
            }

            presence.addChild(x);

            out.add(presence);
        }
    }

    private void removeOccupantByJID(String jid) {
        String nick = this.occupantsByJID.remove(jid);
        this.occupantsByNick.remove(nick);
        this.roles.remove(jid);
    }

}