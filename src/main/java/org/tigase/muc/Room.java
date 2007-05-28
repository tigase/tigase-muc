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
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import tigase.db.UserRepository;
import tigase.util.JIDUtils;
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
        String bareJID = JIDUtils.getNodeID(realJID);
        Affiliation affiliation = this.configuration.getAffiliation(bareJID);
        Role result;
        result = configuration.isModerated() || configuration.isOccupantDefaultParticipant() ? Role.VISITOR
                : Role.PARTICIPANT;
        if (affiliation == Affiliation.ADMIN || affiliation == Affiliation.OWNER) {
            return Role.MODERATOR;
        } else if (affiliation == Affiliation.MEMBER) {
            return Role.PARTICIPANT;
        }
        return result;
    }

    /**
     * @return
     */
    private Collection<? extends String> findBareJidsWithoutAffiliations() {
        List<String> result = new ArrayList<String>();
        for (Map.Entry<String, String> entry : this.occupantsByJID.entrySet()) {
            if (this.configuration.getAffiliation(entry.getKey()) == Affiliation.NONE) {
                result.add(entry.getKey());
            }
        }
        return result;
    }

    /**
     * @param reqRole
     * @return
     */
    private Collection<String> findJidsByRole(Role reqRole) {
        List<String> result = new ArrayList<String>();
        for (Map.Entry<String, Role> entry : this.roles.entrySet()) {
            if (reqRole == entry.getValue()) {
                result.add(entry.getKey());
            }
        }
        return result;
    }

    private Collection<String> getOccupantJidsByBare(String bareJid) {
        List<String> result = new ArrayList<String>();
        String sf = JIDUtils.getNodeID(bareJid);
        for (Entry<String, String> entry : this.occupantsByJID.entrySet()) {
            if (JIDUtils.getNodeID(entry.getKey()).equals(sf)) {
                result.add(entry.getKey());
            }
        }
        return result;
    }

    private Role getRole(String jid) {
        Role result = this.roles.get(JIDUtils.getNodeID(jid));
        return result == null ? Role.NONE : result;
    }

    private Element makeErrorStanza(Element source, String code, String type, String errorName) {
        Element errorStanza = source.clone();
        errorStanza.setAttribute("to", source.getAttribute("from"));
        errorStanza.setAttribute("from", roomID);
        errorStanza.setAttribute("type", "error");

        Element error = new Element("error");
        errorStanza.addChild(error);
        error.setAttribute("code", code);
        error.setAttribute("type", type);

        Element x = new Element(errorName);
        x.setAttribute("xmlns", "urn:ietf:params:xml:ns:xmpp-stanzas");
        error.addChild(x);

        return errorStanza;
    }

    private Element preparePresenceSubItem(String occupantJid, Affiliation occupantAffiliation, Role occupantRole,
            String sendingTo) {
        Element item = new Element("item");
        String bareJID = JIDUtils.getNodeID(occupantJid);
        // item.setAttribute("affiliation", nick);
        item.setAttribute("role", occupantRole == null ? "none" : occupantRole.name().toLowerCase());

        item.setAttribute("affiliation", occupantAffiliation == null ? "none"
                : occupantAffiliation.name().toLowerCase());

        Affiliation receiverAffiliation = this.configuration.getAffiliation(JIDUtils.getNodeID(sendingTo));
        if (receiverAffiliation != null && configuration.affiliationCanViewJid(receiverAffiliation)) {
            item.setAttribute("jid", occupantJid);
        }

        String nick = this.occupantsByJID.get(occupantJid);
        if (nick != null) {
            item.setAttribute("nick", nick);
        }

        return item;
    }

    private Element preparePresenceSubItem(String jid, String sendingTo) {
        String bareJID = JIDUtils.getNodeID(jid);
        Role occupantRole = this.roles.get(bareJID);
        Affiliation occupantAffiliation = this.configuration.getAffiliation(bareJID);
        return preparePresenceSubItem(jid, occupantAffiliation, occupantRole, sendingTo);
    }

    private List<Element> processChangingNickname(String realJID, String oldNick, Element element) {
        String newNick = JIDUtils.getNodeResource(element.getAttribute("to"));
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
        this.roles.put(JIDUtils.getNodeID(realJID), occupantRole);
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
        this.roles.remove(JIDUtils.getNodeID(realJID));

        return result;
    }

    public List<Element> processIq(Element iq) {
        String nick = JIDUtils.getNodeResource(iq.getAttribute("to"));
        Element query = iq.getChild("query");
        String xmlns = query == null ? null : query.getAttribute("xmlns");

        String sender = iq.getAttribute("from");

        try {
            if ("http://jabber.org/protocol/muc#admin".equals(xmlns) && nick == null
                    && "set".equals(iq.getAttribute("type"))) {
                return processIqAdminSet(iq);
            } else if ("http://jabber.org/protocol/muc#admin".equals(xmlns) && nick == null
                    && "get".equals(iq.getAttribute("type"))) {
                return processIqAdminGet(iq);
            } else if ("http://jabber.org/protocol/muc#owner".equals(xmlns) && nick == null
                    && "get".equals(iq.getAttribute("type"))) {
                if (Affiliation.OWNER != this.configuration.getAffiliation(sender)) {
                    throw new MucInternalException(iq, "forbidden", "403", "auth");
                }
                return processIqOwnerGet(iq);
            } else if ("http://jabber.org/protocol/muc#owner".equals(xmlns) && nick == null
                    && "set".equals(iq.getAttribute("type"))) {
                if (Affiliation.OWNER != this.configuration.getAffiliation(sender)) {
                    throw new MucInternalException(iq, "forbidden", "403", "auth");
                }
                return processIqOwnerSet(iq);
            } else {
                System.err.println(" unknown <iq> stanza " + iq);
            }
        } catch (MucInternalException e) {
            Element answer = new Element("iq");
            answer.addAttribute("id", iq.getAttribute("id"));
            answer.addAttribute("type", "error");
            answer.addAttribute("to", iq.getAttribute("from"));
            answer.addAttribute("from", roomID);

            Element answerQuery = new Element("query");
            answer.addChild(answerQuery);
            answerQuery.setAttribute("query", "http://jabber.org/protocol/muc#owner");
            answerQuery.addChild(e.getItem());

            answer.addChild(e.makeErrorElement());
        }
        return null;
    }

    private List<Element> processIqAdminGet(Element iq) {
        List<Element> result = new LinkedList<Element>();
        Element query = iq.getChild("query", "http://jabber.org/protocol/muc#admin");
        List<Element> items = query.getChildren("/query");

        // Service Informs Admin or Owner of Success
        Element answer = new Element("iq");
        answer.addAttribute("id", iq.getAttribute("id"));
        answer.addAttribute("type", "result");
        answer.addAttribute("to", iq.getAttribute("from"));
        answer.addAttribute("from", roomID);

        Element answerQuery = new Element("query");
        answer.addChild(answerQuery);
        answerQuery.setAttribute("query", "http://jabber.org/protocol/muc#admin");

        Set<String> occupantsJid = new HashSet<String>();
        for (Element item : items) {

            try {
                Affiliation reqAffiliation = Affiliation.valueOf(item.getAttribute("affiliation").toUpperCase());
                occupantsJid.addAll(this.configuration.findBareJidsByAffiliations(reqAffiliation));
                if (reqAffiliation == Affiliation.NONE) {
                    occupantsJid.addAll(findBareJidsWithoutAffiliations());
                }
            } catch (Exception e) {
                ;
            }

            try {
                Role reqRole = Role.valueOf(item.getAttribute("role").toUpperCase());
                occupantsJid.addAll(findJidsByRole(reqRole));
            } catch (Exception e) {
                ;
            }

        }

        for (String jid : occupantsJid) {
            Element answerItem = preparePresenceSubItem(jid, iq.getAttribute("from"));
            answerQuery.addChild(answerItem);
        }

        result.add(answer);
        return result;
    }

    /**
     * Changing roles and affiliations
     * 
     * @param iq
     * @return
     */
    private List<Element> processIqAdminSet(Element iq) {
        List<Element> result = new LinkedList<Element>();

        Affiliation senderAffiliation = this.configuration.getAffiliation(iq.getAttribute("from"));
        Role senderRole = getRole(iq.getAttribute("from"));

        Element query = iq.getChild("query", "http://jabber.org/protocol/muc#admin");
        List<Element> items = query.getChildren("/query");

        Map<String, Affiliation> affiliationsToSet = new HashMap<String, Affiliation>();
        Map<String, Role> rolesToSet = new HashMap<String, Role>();
        Set<String> jidsToRemove = new HashSet<String>();

        try {
            for (Element item : items) {
                Set<String> occupantJids = new HashSet<String>();

                String occupantNick = item.getAttribute("nick");
                if (occupantNick != null) {
                    occupantJids.add(this.occupantsByNick.get(occupantNick));
                }
                Role newRole;
                try {
                    newRole = Role.valueOf(item.getAttribute("role").toUpperCase());
                } catch (Exception e) {
                    newRole = null;
                }

                String occupantBareJid = item.getAttribute("jid");
                if (occupantBareJid != null) {
                    occupantJids.addAll(getOccupantJidsByBare(occupantBareJid));
                } else {
                    occupantBareJid = JIDUtils.getNodeID(this.occupantsByNick.get(occupantNick));
                }
                Affiliation newAffiliation;
                try {
                    newAffiliation = Affiliation.valueOf(item.getAttribute("affiliation").toUpperCase());
                } catch (Exception e) {
                    newAffiliation = null;
                }

                Affiliation currentAffiliation = this.configuration.getAffiliation(occupantBareJid);

                if (senderAffiliation.getWeight() < currentAffiliation.getWeight()) {
                    throw new MucInternalException(item, "forbidden", "403", "auth");
                }

                // process bussines logic ;-)
                if (newAffiliation != null && occupantBareJid != null) {
                    affiliationsToSet.put(occupantBareJid, newAffiliation);
                }
                if (newRole != null && occupantNick != null) {
                    if (newRole == Role.MODERATOR
                            && (senderAffiliation != Affiliation.ADMIN && senderAffiliation != Affiliation.OWNER)) {
                        throw new MucInternalException(item, "not-allowed", "405", "cancel");
                    } else if (senderRole != Role.MODERATOR) {
                        throw new MucInternalException(item, "not-allowed", "405", "cancel");
                    }
                    rolesToSet.put(occupantNick, newRole);
                }

                List<Element> reasons = item.getChildren("/item");

                for (String occupantJid : occupantJids) {
                    occupantNick = this.occupantsByJID.get(occupantJid);
                    // Service Informs all Occupants
                    for (Entry<String, String> entry : this.occupantsByNick.entrySet()) {
                        // preparing presence stanza
                        Element presence = this.lastReceivedPresence.get(occupantJid);
                        if (presence == null) {
                            continue;
                        } else {
                            presence = presence.clone();
                        }
                        presence.setAttribute("from", roomID + "/" + occupantNick);
                        presence.setAttribute("to", entry.getValue());

                        // preparing <x> element
                        Element x = new Element("x");
                        presence.addChild(x);
                        x.setAttribute("xmlns", "http://jabber.org/protocol/muc#admin");
                        if (newRole == Role.NONE) {
                            // kick
                            x.addChild(new Element("status", new String[] { "code" }, new String[] { "307" }));
                            presence.setAttribute("type", "unavailable");
                            jidsToRemove.add(occupantJid);
                        }
                        if (newAffiliation == Affiliation.OUTCAST) {
                            // ban
                            x.addChild(new Element("status", new String[] { "code" }, new String[] { "301" }));
                            presence.setAttribute("type", "unavailable");
                            jidsToRemove.add(occupantJid);
                        }
                        if (occupantJid.equals(entry.getValue())) {
                            x.addChild(new Element("status", new String[] { "code" }, new String[] { "110" }));
                        }

                        // preparing <item> element
                        Role roleToSend = newRole != null ? newRole : getRole(occupantJid);
                        Affiliation affiliationToSend = newAffiliation != null ? newAffiliation
                                : this.configuration.getAffiliation(occupantJid);
                        Element subItem = preparePresenceSubItem(occupantJid, affiliationToSend, roleToSend,
                                entry.getValue());
                        x.addChild(subItem);
                        if (reasons != null && reasons.size() > 0) {
                            subItem.addChildren(reasons);
                        }

                        result.add(presence);
                    }
                }
            }
            for (Map.Entry<String, Affiliation> entry : affiliationsToSet.entrySet()) {
                this.configuration.setAffiliation(entry.getKey(), entry.getValue());
            }
            for (Map.Entry<String, Role> entry : rolesToSet.entrySet()) {
                setRoleByNick(entry.getKey(), entry.getValue());
            }
            for (String jid : jidsToRemove) {
                removeOccupantByJID(jid);
            }

            // Service Informs Admin or Owner of Success
            Element answer = new Element("iq");
            answer.addAttribute("id", iq.getAttribute("id"));
            answer.addAttribute("type", "result");
            answer.addAttribute("to", iq.getAttribute("from"));
            answer.addAttribute("from", roomID);
            result.add(answer);
        } catch (MucInternalException e) {
            result.clear();

            Element answer = new Element("iq");
            answer.addAttribute("id", iq.getAttribute("id"));
            answer.addAttribute("type", "error");
            answer.addAttribute("to", iq.getAttribute("from"));
            answer.addAttribute("from", roomID);

            Element answerQuery = new Element("query");
            answer.addChild(answerQuery);
            answerQuery.setAttribute("query", "http://jabber.org/protocol/muc#admin");
            answerQuery.addChild(e.getItem());

            answer.addChild(e.makeErrorElement());

            result.add(answer);
        }

        return result;
    }

    private List<Element> processIqOwnerGet(Element iq) {
        List<Element> result = new LinkedList<Element>();
        Element query = iq.getChild("query");

        if (query.getChildren() == null || query.getChildren().size() == 0) {
            Element answer = new Element("iq");
            answer.addAttribute("id", iq.getAttribute("id"));
            answer.addAttribute("type", "result");
            answer.addAttribute("to", iq.getAttribute("from"));
            answer.addAttribute("from", roomID);

            Element answerQuery = new Element("query", new String[] { "xmlns" },
                    new String[] { "http://jabber.org/protocol/muc#owner" });
            answer.addChild(answerQuery);

            answerQuery.addChild(this.configuration.getFormElement().getElement());

            result.add(answer);
        }

        return result;
    }

    private List<Element> processIqOwnerSet(Element iq) {
        return this.configuration.parseConfig(iq);
    }

    private List<Element> processMessage(Element element) {
        String senderNick = this.occupantsByJID.get(element.getAttribute("from"));
        String recipentNick = JIDUtils.getNodeResource(element.getAttribute("to"));
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
        String realJID = element.getAttribute("from");
        String nick = JIDUtils.getNodeResource(element.getAttribute("to"));

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
        String nick = JIDUtils.getNodeResource(element.getAttribute("to"));

        if ("message".equals(element.getName())) {
            return processMessage(element);
        } else if ("presence".equals(element.getName())) {
            return processPresence(element);
        } else if ("iq".equals(element.getName())) {
            return processIq(element);
        }

        return null;
    }

    private void removeOccupantByJID(String jid) {
        String nick = this.occupantsByJID.remove(jid);
        this.occupantsByNick.remove(nick);
        this.roles.remove(jid);
        this.lastReceivedPresence.remove(jid);
    }

    private void setRoleByNick(String nick, Role role) {
        String jid = this.occupantsByNick.get(nick);
        if (role == null) {
            this.roles.remove(JIDUtils.getNodeID(jid));
        } else {
            this.roles.put(JIDUtils.getNodeID(jid), role);
        }
    }

}