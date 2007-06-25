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
package tigase.muc.modules;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.logging.Logger;

import tigase.criteria.Criteria;
import tigase.criteria.ElementCriteria;
import tigase.muc.Affiliation;
import tigase.muc.MUCService;
import tigase.muc.MucInternalException;
import tigase.muc.Role;
import tigase.muc.RoomContext;
import tigase.muc.RoomListener;
import tigase.muc.xmpp.JID;
import tigase.muc.xmpp.stanzas.Message;
import tigase.muc.xmpp.stanzas.MessageType;
import tigase.muc.xmpp.stanzas.Presence;
import tigase.xml.Element;

/**
 * 
 * <p>
 * Created: 2007-06-20 08:49:49
 * </p>
 * 
 * @author bmalkow
 * @version $Rev$
 */
public class PresenceModule extends AbstractModule {

    private final static Criteria CRIT = ElementCriteria.name("presence");

    public static final String XMLNS_MUC = "http://jabber.org/protocol/muc";
    public static final String XMLNS_MUC_USER = "http://jabber.org/protocol/muc#user";

    public static Presence clonePresence(Presence presence) {
        if (presence == null) {
            return null;
        }
        Element p = presence.clone();
        Element toRemove = p.getChild("x", XMLNS_MUC);
        if (toRemove != null) {
            p.removeChild(toRemove);
        }
        return new Presence(p);
    }

    public static Element preparePresenceSubItem(RoomContext roomContext, JID occupantJid, Affiliation occupantAffiliation, Role occupantRole, JID sendingTo) {
        Element item = new Element("item");
        // item.setAttribute("affiliation", nick);
        item.setAttribute("role", occupantRole == null ? "none" : occupantRole.name().toLowerCase());

        item.setAttribute("affiliation", occupantAffiliation == null ? "none" : occupantAffiliation.name().toLowerCase());

        Affiliation receiverAffiliation = roomContext.getAffiliation(sendingTo);
        if (receiverAffiliation != null && roomContext.affiliationCanViewJid(receiverAffiliation)) {
            item.setAttribute("jid", occupantJid.toString());
        }

        String nick = roomContext.getOccupantsByJID().get(occupantJid);
        if (nick != null) {
            item.setAttribute("nick", nick);
        }

        return item;
    }

    public static Element preparePresenceSubItem(RoomContext roomContext, JID jid, JID sendingTo) {
        Role occupantRole = roomContext.getRole(jid);
        Affiliation occupantAffiliation = roomContext.getAffiliation(jid);
        return preparePresenceSubItem(roomContext, jid, occupantAffiliation, occupantRole, sendingTo);
    }

    private Logger log = Logger.getLogger(this.getClass().getName());

    private RoomListener roomListener;

    public PresenceModule(RoomListener roomListener) {
        this.roomListener = roomListener;
    }

    @Override
    public Criteria getModuleCriteria() {
        return CRIT;
    }

    @Override
    protected List<Element> intProcess(RoomContext roomContext, Element e) throws MucInternalException {
        Presence element = new Presence(e);
        JID realJID = element.getFrom();
        String nick = element.getTo().getResource();

        String existNick = roomContext.getOccupantsByJID().get(realJID);

        roomContext.getLastReceivedPresence().put(realJID, element);

        if (existNick != null && !existNick.equals(nick)) {
            return processChangingNickname(roomContext, realJID, existNick, element);
        } else if ("unavailable".equals(element.getAttribute("type"))) {
            return processExitingARoom(roomContext, realJID, nick, element);
        } else if (existNick != null && existNick.equals(nick)) {
            return processNewPresenceStatus(roomContext, realJID, nick, element);
        } else {
            return processEnteringToRoom(roomContext, realJID, nick, element);
        }
    }

    private List<Element> processChangingNickname(RoomContext roomContext, JID realJID, String oldNick, Presence element) {
        String newNick = element.getTo().getResource();
        List<Element> result = new LinkedList<Element>();

        if (roomContext.getOccupantsByNick().containsKey(newNick)) {
            result.add(MUCService.errorPresence(JID.fromString(roomContext.getId()), realJID, "cancel", "409", "conflict"));
            return result;
        }

        // Service Sends New Occupant's Presence to All Occupants
        for (Entry<String, JID> entry : roomContext.getOccupantsByNick().entrySet()) {
            Presence presence = clonePresence(element);
            presence.setAttribute("to", entry.getValue().toString());
            presence.setAttribute("from", roomContext.getId() + "/" + oldNick);
            presence.setAttribute("type", "unavailable");

            Element x = new Element("x");
            x.setAttribute("xmlns", XMLNS_MUC_USER);

            Element item = preparePresenceSubItem(roomContext, realJID, entry.getValue());
            x.addChild(item);
            item.setAttribute("nick", newNick);

            x.addChild(new Element("status", new String[] { "code" }, new String[] { "303" }));
            if (entry.getValue().equals(realJID)) {
                x.addChild(new Element("status", new String[] { "code" }, new String[] { "110" }));
            }
            presence.addChild(x);

            result.add(presence);
        }
        roomContext.getOccupantsByNick().remove(oldNick);
        roomContext.getOccupantsByJID().remove(realJID);

        roomContext.getOccupantsByNick().put(newNick, realJID);
        roomContext.getOccupantsByJID().put(realJID, newNick);

        for (Entry<String, JID> entry : roomContext.getOccupantsByNick().entrySet()) {
            Presence presence = clonePresence(element);
            presence.setAttribute("to", entry.getValue().toString());
            presence.setAttribute("from", roomContext.getId() + "/" + newNick);

            Element x = new Element("x");
            Element item = preparePresenceSubItem(roomContext, realJID, entry.getValue());
            x.addChild(item);

            x.setAttribute("xmlns", XMLNS_MUC_USER);
            if (entry.getValue().equals(realJID)) {
                x.addChild(new Element("status", new String[] { "code" }, new String[] { "110" }));
            }
            presence.addChild(x);

            result.add(presence);
        }

        return result;
    }

    private List<Element> processEnteringToRoom(RoomContext roomContext, JID realJID, String nick, Presence element) {
        List<Element> result = new LinkedList<Element>();

        Element incomX = element.getChild("x", XMLNS_MUC);

        if (roomContext.getAffiliation(realJID) == Affiliation.OUTCAST) {
            result.add(MUCService.errorPresence(JID.fromString(roomContext.getId()), realJID, "auth", "403", "forbidden"));
            return result;
        }
        if (roomContext.isLockedRoom() && roomContext.getAffiliation(realJID) != Affiliation.OWNER) {
            result.add(MUCService.errorPresence(JID.fromString(roomContext.getId()), realJID, "cancel", "404", "item-not-found"));
            return result;
        }
        if (roomContext.isRoomconfigMembersOnly() && roomContext.getAffiliation(realJID).getWeight() < Affiliation.MEMBER.getWeight()) {
            result.add(MUCService.errorPresence(JID.fromString(roomContext.getId()), realJID, "auth", "407", "registration-required"));
            return result;
        }
        if (roomContext.isRoomconfigPasswordProtectedRoom()) {
            boolean auth = false;
            if (incomX != null) {
                Element pass = incomX.getChild("password");
                auth = pass != null && roomContext.checkPassword(pass.getCData());
            }
            if (!auth) {
                result.add(MUCService.errorPresence(JID.fromString(roomContext.getId()), realJID, "auth", "401", "not-authorized"));
                return result;
            }
        }
        if (roomContext.getOccupantsByNick().containsKey(nick)) {
            result.add(MUCService.errorPresence(JID.fromString(roomContext.getId()), realJID, "cancel", "409", "conflict"));
            return result;
        }
        if (roomContext.getOccupantsByJID().size() >= roomContext.getRoomconfigMaxUsers()) {
            result.add(MUCService.errorPresence(JID.fromString(roomContext.getId()), realJID, "wait", "503", "service-unavailable"));
            return result;
        }
        // Service Sends Presence from Existing Occupants to New Occupant
        for (Entry<String, JID> entry : roomContext.getOccupantsByNick().entrySet()) {
            Presence presence = clonePresence(element);
            presence.setAttribute("to", realJID.toString());
            presence.setAttribute("from", roomContext.getId() + "/" + entry.getKey());

            Element x = new Element("x");
            x.setAttribute("xmlns", XMLNS_MUC_USER);
            x.addChild(preparePresenceSubItem(roomContext, entry.getValue(), realJID));
            if (entry.getValue().equals(realJID)) {
                x.addChild(new Element("status", new String[] { "code" }, new String[] { "110" }));
            }
            presence.addChild(x);

            result.add(presence);
        }
        roomContext.getOccupantsByNick().put(nick, realJID);
        roomContext.getOccupantsByJID().put(realJID, nick);
        Role occupantRole = roomContext.calculateInitialRole(realJID);
        roomContext.setRole(realJID, occupantRole);
        // Service Sends Presence from Existing Occupants to New Occupant
        for (Entry<String, JID> entry : roomContext.getOccupantsByNick().entrySet()) {
            Presence presence = clonePresence(element);
            presence.setAttribute("to", entry.getValue().toString());
            presence.setAttribute("from", roomContext.getId() + "/" + nick);

            Element x = new Element("x");
            x.setAttribute("xmlns", XMLNS_MUC_USER);
            x.addChild(preparePresenceSubItem(roomContext, realJID, entry.getValue()));
            if (entry.getValue().equals(realJID)) {
                if (roomContext.affiliationCanViewJid(Affiliation.NONE)) {
                    x.addChild(new Element("status", new String[] { "code" }, new String[] { "100" }));
                }
                x.addChild(new Element("status", new String[] { "code" }, new String[] { "110" }));
                if (roomContext.isRoomconfigEnableLogging()) {
                    x.addChild(new Element("status", new String[] { "code" }, new String[] { "170" }));
                }
                if (roomContext.isRoomCreated()) {
                    x.addChild(new Element("status", new String[] { "code" }, new String[] { "201" }));
                }
            }
            presence.addChild(x);

            result.add(presence);
        }
        // service sends subject
        if (roomContext.getCurrentSubject() != null) {
            Element subject = roomContext.getCurrentSubject().clone();
            subject.setAttribute("to", realJID.toString());
            result.add(subject);
        }
        // service Sends new occupant conversation history
        Iterator<Element> iterator = roomContext.getConversationHistory().iterator();
        while (iterator.hasNext()) {
            Element message = iterator.next().clone();
            message.setAttribute("to", realJID.toString());
            result.add(message);
        }
        if (roomContext.isRoomCreated()) {
            roomContext.setRoomCreated(false);
            Message message = new Message(realJID, "Room is locked!");
            message.setFrom(JID.fromString(roomContext.getId()));
            message.setType(MessageType.GROUPCHAT);
            result.add(message);

        }

        log.fine("Occupant " + realJID + " (" + nick + ") entering room " + roomContext.getId() + ", with role " + occupantRole + " and affiliation "
                + roomContext.getAffiliation(realJID));
        return result;
    }

    private List<Element> processExitingARoom(RoomContext roomContext, JID realJID, String nick, Presence element) {
        List<Element> result = new LinkedList<Element>();
        // Service Sends New Occupant's Presence to All Occupants
        for (Entry<String, JID> entry : roomContext.getOccupantsByNick().entrySet()) {
            Presence presence = clonePresence(element);
            presence.setAttribute("to", entry.getValue().toString());
            presence.setAttribute("from", roomContext.getId() + "/" + nick);

            Element x = new Element("x");
            x.setAttribute("xmlns", XMLNS_MUC_USER);
            if (entry.getValue().equals(realJID)) {
                x.addChild(new Element("status", new String[] { "code" }, new String[] { "110" }));
            }
            presence.addChild(x);

            result.add(presence);
        }
        roomContext.getOccupantsByNick().remove(nick);
        roomContext.getOccupantsByJID().remove(realJID);
        roomContext.setRole(realJID, null);
        roomContext.getLastReceivedPresence().remove(realJID);

        if (this.roomListener != null) {
            this.roomListener.onOccupantLeave(roomContext);
        }

        log.fine("Occupant " + realJID + " (" + nick + ") leave room " + roomContext.getId());
        return result;
    }

    private List<Element> processNewPresenceStatus(RoomContext roomContext, JID realJID, String nick, Presence element) {
        List<Element> result = new LinkedList<Element>();
        for (Entry<String, JID> entry : roomContext.getOccupantsByNick().entrySet()) {
            Presence presence = clonePresence(element);
            presence.setAttribute("to", entry.getValue().toString());
            presence.setAttribute("from", roomContext.getId() + "/" + nick);

            Element x = new Element("x");
            x.setAttribute("xmlns", XMLNS_MUC_USER);
            x.addChild(preparePresenceSubItem(roomContext, realJID, entry.getValue()));
            if (entry.getValue().equals(realJID)) {
                x.addChild(new Element("status", new String[] { "code" }, new String[] { "110" }));
            }

            presence.addChild(x);

            result.add(presence);
        }
        return result;
    }

}
