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
 *  $Id:Room.java 43 2007-05-31 07:35:05Z bmalkow $
 */
package tigase.muc;

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
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;

import tigase.db.UserRepository;
import tigase.muc.xmpp.JID;
import tigase.muc.xmpp.stanzas.IQ;
import tigase.muc.xmpp.stanzas.IQType;
import tigase.muc.xmpp.stanzas.Message;
import tigase.muc.xmpp.stanzas.Presence;
import tigase.xml.Element;

/**
 * 
 * <p>
 * Created: 2007-05-15 13:37:55
 * </p>
 * 
 * @author bmalkow
 * @version $Rev:43 $
 */
public class Room implements Serializable {

    private static final long serialVersionUID = 1L;

    private final RoomConfiguration configuration;

    private History conversationHistory;

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

    private String roomID;

    public Room(UserRepository mucRepository, String roomID, JID ownerJID, boolean roomCreated) {
        this.conversationHistory = new History(20);
        this.roomID = roomID;
        this.configuration = new RoomConfiguration(roomID, mucRepository, ownerJID);
        this.lockedRoom = roomCreated;
        log.info((roomCreated ? "Creating " : "Retriving ") + " room " + roomID);
        if (roomCreated) {
            this.configuration.setAffiliation(ownerJID, Affiliation.OWNER);
        }
    }

    /**
     * @param realJID
     * @return
     */
    private Role calculateInitialRole(JID realJID) {
        Affiliation affiliation = this.configuration.getAffiliation(realJID);
        Role result;
        result = configuration.isModerated() || configuration.isOccupantDefaultParticipant() ? Role.VISITOR : Role.PARTICIPANT;
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
    private Collection<? extends JID> findBareJidsWithoutAffiliations() {
        List<JID> result = new ArrayList<JID>();
        for (Map.Entry<JID, String> entry : this.occupantsByJID.entrySet()) {
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
    private Collection<JID> findJidsByRole(Role reqRole) {
        List<JID> result = new ArrayList<JID>();
        for (Map.Entry<JID, Role> entry : this.occupantsRole.entrySet()) {
            if (reqRole == entry.getValue()) {
                result.add(entry.getKey());
            }
        }
        return result;
    }

    private Collection<JID> getOccupantJidsByBare(JID jid) {
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

    private Role getRole(JID jid) {
        Role result = this.occupantsRole.get(jid.toString());
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

    private Element preparePresenceSubItem(JID occupantJid, Affiliation occupantAffiliation, Role occupantRole,
            JID sendingTo) {
        Element item = new Element("item");
        // item.setAttribute("affiliation", nick);
        item.setAttribute("role", occupantRole == null ? "none" : occupantRole.name().toLowerCase());

        item.setAttribute("affiliation", occupantAffiliation == null ? "none" : occupantAffiliation.name()
                .toLowerCase());

        Affiliation receiverAffiliation = this.configuration.getAffiliation(sendingTo);
        if (receiverAffiliation != null && configuration.affiliationCanViewJid(receiverAffiliation)) {
            item.setAttribute("jid", occupantJid.toString());
        }

        String nick = this.occupantsByJID.get(occupantJid);
        if (nick != null) {
            item.setAttribute("nick", nick);
        }

        return item;
    }

    private Element preparePresenceSubItem(JID jid, JID sendingTo) {
        Role occupantRole = getRole(jid);
        Affiliation occupantAffiliation = this.configuration.getAffiliation(jid);
        return preparePresenceSubItem(jid, occupantAffiliation, occupantRole, sendingTo);
    }

    private List<Element> processChangingNickname(JID realJID, String oldNick, Presence element) {
        String newNick = element.getTo().getResource();
        List<Element> result = new LinkedList<Element>();

        if (this.occupantsByNick.containsKey(newNick)) {
            result.add(MUCService.errorPresence(JID.fromString(roomID), realJID, "cancel", "409", "conflict"));
            return result;
        }

        // Service Sends New Occupant's Presence to All Occupants
        for (Entry<String, JID> entry : this.occupantsByNick.entrySet()) {
            Presence presence = clonePresence(element);
            presence.setAttribute("to", entry.getValue().toString());
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

        for (Entry<String, JID> entry : this.occupantsByNick.entrySet()) {
            Presence presence = clonePresence(element);
            presence.setAttribute("to", entry.getValue().toString());
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

    private List<Element> processEnteringToRoom(JID realJID, String nick, Presence element, boolean roomCreated) {
        List<Element> result = new LinkedList<Element>();

        Element incomX = element.getChild("x", "http://jabber.org/protocol/muc");

        if (this.configuration.getAffiliation(realJID) == Affiliation.OUTCAST) {
            result.add(MUCService.errorPresence(JID.fromString(roomID), realJID, "auth", "403", "forbidden"));
            return result;
        }
        if (lockedRoom && this.configuration.getAffiliation(realJID) != Affiliation.OWNER) {
            result.add(MUCService.errorPresence(JID.fromString(roomID), realJID, "cancel", "404", "item-not-found"));
            return result;
        }
        if (this.configuration.isInvitationRequired()
                && this.configuration.getAffiliation(realJID).getWeight() < Affiliation.MEMBER.getWeight()) {
            result.add(MUCService
                    .errorPresence(JID.fromString(roomID), realJID, "auth", "407", "registration-required"));
            return result;
        }
        if (this.configuration.isPasswordRequired()) {
            boolean auth = false;
            if (incomX != null) {
                Element pass = incomX.getChild("password");
                auth = pass != null && this.configuration.checkPassword(pass.getCData());
            }
            if (!auth) {
                result.add(MUCService.errorPresence(JID.fromString(roomID), realJID, "auth", "401", "not-authorized"));
                return result;
            }
        }
        if (this.occupantsByNick.containsKey(nick)) {
            result.add(MUCService.errorPresence(JID.fromString(roomID), realJID, "cancel", "409", "conflict"));
            return result;
        }
        // Service Sends Presence from Existing Occupants to New Occupant
        for (Entry<String, JID> entry : this.occupantsByNick.entrySet()) {
            Presence presence = clonePresence(this.lastReceivedPresence.get(entry.getValue()));
            presence.setAttribute("to", realJID.toString());
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
        setRole(realJID, occupantRole);
        // Service Sends Presence from Existing Occupants to New Occupant
        for (Entry<String, JID> entry : this.occupantsByNick.entrySet()) {
            Presence presence = clonePresence(element);
            presence.setAttribute("to", entry.getValue().toString());
            presence.setAttribute("from", roomID + "/" + nick);

            Element x = new Element("x");
            x.setAttribute("xmlns", "http://jabber.org/protocol/muc#user");
            x.addChild(preparePresenceSubItem(realJID, entry.getValue()));
            if (roomCreated) {
                x.addChild(new Element("status", new String[] { "code" }, new String[] { "201" }));
            }
            if (entry.getValue().equals(realJID)) {
                if (this.configuration.isLogging()) {
                    x.addChild(new Element("status", new String[] { "code" }, new String[] { "170" }));
                }
                if (this.configuration.affiliationCanViewJid(Affiliation.NONE)) {
                    x.addChild(new Element("status", new String[] { "code" }, new String[] { "100" }));
                }
                x.addChild(new Element("status", new String[] { "code" }, new String[] { "110" }));
            }
            presence.addChild(x);

            result.add(presence);
        }
        // service Sends new occupant conversation history
        Iterator<Element> iterator = this.conversationHistory.iterator();
        while (iterator.hasNext()) {
            Element message = iterator.next().clone();
            message.setAttribute("to", realJID.toString());
            result.add(message);
        }
        return result;
    }

    private List<Element> processExitingARoom(JID realJID, String nick, Presence element) {
        List<Element> result = new LinkedList<Element>();
        // Service Sends New Occupant's Presence to All Occupants
        for (Entry<String, JID> entry : this.occupantsByNick.entrySet()) {
            Presence presence = clonePresence(element);
            presence.setAttribute("to", entry.getValue().toString());
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
        setRole(realJID, null);

        return result;
    }

    /**
     * @param element
     * @return
     */
    public List<Element> processInitialStanza(Presence element) {
        /*
         * <presence from='darkcave@macbeth.shakespeare.lit/firstwitch'
         * to='crone1@shakespeare.lit/desktop'> <x
         * xmlns='http://jabber.org/protocol/muc#user'> <item
         * affiliation='owner' role='moderator'/> <status code='201'/> </x>
         * </presence>
         */
        JID realJID = JID.fromString(element.getAttribute("from"));
        String nick = element.getTo().getResource();
        return processEnteringToRoom(realJID, nick, element, true);

    }

    private Presence clonePresence(Presence presence) {
        if (presence == null) {
            return null;
        }
        Element p = presence.clone();
        Element toRemove = p.getChild("x", "http://jabber.org/protocol/muc");
        if (toRemove != null) {
            p.removeChild(toRemove);
        }
        return new Presence(p);
    }

    private List<Element> processIqAdminGet(IQ iq) {
        List<Element> result = new LinkedList<Element>();
        Element query = iq.getChild("query", "http://jabber.org/protocol/muc#admin");
        List<Element> items = query.getChildren("/query");

        // Service Informs Admin or Owner of Success
        IQ answer = new IQ(IQType.RESULT);
        answer.setId(iq.getId());
        answer.setTo(iq.getFrom());
        answer.setFrom(JID.fromString(roomID));

        Element answerQuery = new Element("query");
        answer.addChild(answerQuery);
        answerQuery.setAttribute("query", "http://jabber.org/protocol/muc#admin");

        Set<JID> occupantsJid = new HashSet<JID>();
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

        for (JID jid : occupantsJid) {
            Element answerItem = preparePresenceSubItem(jid, iq.getFrom());
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
    private List<Element> processIqAdminSet(IQ iq) {
        List<Element> result = new LinkedList<Element>();

        Affiliation senderAffiliation = this.configuration.getAffiliation(iq.getFrom());
        Role senderRole = getRole(iq.getFrom());

        Element query = iq.getChild("query", "http://jabber.org/protocol/muc#admin");
        List<Element> items = query.getChildren("/query");

        Map<JID, Affiliation> affiliationsToSet = new HashMap<JID, Affiliation>();
        Map<String, Role> rolesToSet = new HashMap<String, Role>();
        Set<JID> jidsToRemove = new HashSet<JID>();

        try {
            for (Element item : items) {
                Set<JID> occupantJids = new HashSet<JID>();

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

                JID occupantBareJid = JID.fromString(item.getAttribute("jid"));
                if (occupantBareJid != null) {
                    occupantJids.addAll(getOccupantJidsByBare(occupantBareJid));
                } else {
                    occupantBareJid = this.occupantsByNick.get(occupantNick).getBareJID();
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

                for (JID occupantJid : occupantJids) {
                    occupantNick = this.occupantsByJID.get(occupantJid);
                    // Service Informs all Occupants
                    for (Entry<String, JID> entry : this.occupantsByNick.entrySet()) {
                        // preparing presence stanza
                        Presence presence = this.lastReceivedPresence.get(occupantJid);
                        if (presence == null) {
                            continue;
                        } else {
                            presence = clonePresence(presence);
                        }
                        presence.setAttribute("from", roomID + "/" + occupantNick);
                        presence.setAttribute("to", entry.getValue().toString());

                        // preparing <x> element
                        Element x = new Element("x");
                        presence.addChild(x);
                        x.setAttribute("xmlns", "http://jabber.org/protocol/muc#user");
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
                        Affiliation affiliationToSend = newAffiliation != null ? newAffiliation : this.configuration
                                .getAffiliation(occupantJid);
                        Element subItem = preparePresenceSubItem(occupantJid, affiliationToSend, roleToSend, entry
                                .getValue());
                        x.addChild(subItem);
                        if (reasons != null && reasons.size() > 0) {
                            subItem.addChildren(reasons);
                        }

                        result.add(presence);
                    }
                }
            }
            for (Map.Entry<JID, Affiliation> entry : affiliationsToSet.entrySet()) {
                this.configuration.setAffiliation(entry.getKey(), entry.getValue());
            }
            for (Map.Entry<String, Role> entry : rolesToSet.entrySet()) {
                setRole(entry.getKey(), entry.getValue());
            }
            for (JID jid : jidsToRemove) {
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

    private List<Element> processIqOwnerGet(IQ iq) {
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
        List<Element> result = this.configuration.parseConfig(iq);
        this.lockedRoom = false;
        return result;
    }

    private List<Element> processNewPresenceStatus(JID realJID, String nick, Presence element) {
        List<Element> result = new LinkedList<Element>();
        for (Entry<String, JID> entry : this.occupantsByNick.entrySet()) {
            Presence presence = clonePresence(element);
            presence.setAttribute("to", entry.getValue().toString());
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

    public List<Element> processStanza(IQ iq) {
        List<Element> result = new LinkedList<Element>();
        String nick = iq.getTo().getResource();
        Element query = iq.getChild("query");
        String xmlns = query == null ? null : query.getXMLNS();

        JID sender = iq.getFrom();

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
                log.log(Level.SEVERE, " unknown <iq> stanza " + iq);
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
        return result;
    }

    public List<Element> processStanza(Message element) {
        List<Element> result = new LinkedList<Element>();
        String senderNick = this.occupantsByJID.get(element.getFrom());
        String recipentNick = element.getTo().getResource();

        if (senderNick == null) {
            Element errMsg = element.clone();
            errMsg.setAttribute("from", roomID);
            errMsg.setAttribute("to", element.getAttribute("from"));
            errMsg.setAttribute("type", "error");

            Element error = new Element("error", new String[] { "code", "type" }, new String[] { "406", "modify" });
            errMsg.addChild(error);
            error.addChild(new Element("not-acceptable", new String[] { "xmlns" },
                    new String[] { "urn:ietf:params:xml:ns:xmpp-stanzas" }));
            error.addChild(new Element("text", "Only occupants are allowed to send messages to the conference",
                    new String[] { "xmlns" }, new String[] { "urn:ietf:params:xml:ns:xmpp-stanzas" }));

            result.add(errMsg);
            return result;
        }

        if (getRole(JID.fromString(element.getAttribute("from"))) == Role.VISITOR) {
            return result;
        }

        if (recipentNick == null) {
            // broadcast message
            this.conversationHistory.add(element, this.roomID + "/" + senderNick, roomID);
            for (Entry<String, JID> entry : this.occupantsByNick.entrySet()) {
                Element message = element.clone();
                message.setAttribute("from", this.roomID + "/" + senderNick);
                message.setAttribute("to", entry.getValue().toString());
                result.add(message);
            }
        } else {
            // private message
            JID recipentJID = this.occupantsByNick.get(recipentNick);
            Element message = element.clone();
            message.setAttribute("from", this.roomID + "/" + senderNick);
            message.setAttribute("to", recipentJID.toString());
            result.add(message);
        }
        return result;
    }

    public List<Element> processStanza(Presence element) {
        JID realJID = element.getFrom();
        String nick = element.getTo().getResource();

        String existNick = this.occupantsByJID.get(realJID);

        this.lastReceivedPresence.put(realJID, element);

        if (existNick != null && !existNick.equals(nick)) {
            return processChangingNickname(realJID, existNick, element);
        } else if ("unavailable".equals(element.getAttribute("type"))) {
            return processExitingARoom(realJID, nick, element);
        } else if (existNick != null && existNick.equals(nick)) {
            return processNewPresenceStatus(realJID, nick, element);
        } else {
            return processEnteringToRoom(realJID, nick, element, false);
        }
    }

    private void removeOccupantByJID(JID jid) {
        String nick = this.occupantsByJID.remove(jid);
        this.occupantsByNick.remove(nick);
        this.lastReceivedPresence.remove(jid);
        setRole(jid, null);
    }

    private void setRole(JID jid, Role role) {
        if (role == null) {
            this.occupantsRole.remove(jid.getBareJID());
        } else {
            this.occupantsRole.put(jid.getBareJID(), role);
        }
    }

    private void setRole(String nick, Role role) {
        JID jid = this.occupantsByNick.get(nick);
        if (role == null) {
            this.occupantsRole.remove(jid.getBareJID());
        } else {
            this.occupantsRole.put(jid.getBareJID(), role);
        }
    }

}