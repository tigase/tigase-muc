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

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

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
public class Room {

    /**
     * <realJID, nick>
     */
    private Map<String, String> occupantsByJID = new HashMap<String, String>();

    /**
     * <nick, realJID>
     */
    private Map<String, String> occupantsByNick = new HashMap<String, String>();

    private String roomID;

    public Room(String roomID) {
        this.roomID = roomID;
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
            Element presence = new Element("presence");
            presence.setAttribute("to", entry.getValue());
            presence.setAttribute("from", roomID + "/" + oldNick);
            presence.setAttribute("type", "unavailable");

            Element x = new Element("x");
            x.setAttribute("xmlns", "http://jabber.org/protocol/muc#user");

            Element item = new Element("item");
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
        for (Entry<String, String> entry : this.occupantsByNick.entrySet()) {
            Element presence = element.clone();
            presence.setAttribute("to", realJID);
            presence.setAttribute("from", roomID + "/" + entry.getKey());

            Element x = new Element("x");
            x.setAttribute("xmlns", "http://jabber.org/protocol/muc#user");
            if (entry.getValue().equals(realJID)) {
                x.addChild(new Element("status", new String[] { "code" }, new String[] { "110" }));
            }
            presence.addChild(x);

            result.add(presence);
        }
        this.occupantsByNick.put(nick, realJID);
        this.occupantsByJID.put(realJID, nick);

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

    private List<Element> processPresence(Element element) {
        String realJID = element.getAttribute("from");
        String nick = JID.getNodeResource(element.getAttribute("to"));

        String existNick = this.occupantsByJID.get(realJID);

        if (existNick != null && !existNick.equals(nick)) {
            return processChangingNickname(realJID, existNick, element);
        } else if ("unavailable".equals(element.getAttribute("type"))) {
            return processExitingARoom(realJID, nick, element);
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

}
