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
package org.tigase.muc.room;

import java.util.HashMap;
import java.util.Map;

import org.tigase.jaxmpp.xmpp.core.JID;
import org.tigase.jaxmpp.xmpp.core.stanzas.Message;
import org.tigase.jaxmpp.xmpp.core.stanzas.MessageType;
import org.tigase.jaxmpp.xmpp.core.stanzas.PresenceType;
import org.tigase.jaxmpp.xmpp.im.presence.Presence;
import org.tigase.muc.ReceptionPlugin;

import tigase.xml.Element;

/**
 * Implements MUC Room.
 * <p>
 * Created: 2007-01-25 13:45:19
 * </p>
 * 
 * @author bmalkow
 * @version $Rev$
 */
public class Room {
    /**
     * Rooms name.
     */
    private String roomName;

    private ReceptionPlugin reception;

    private Map<JID, Occupant> occupants = new HashMap<JID, Occupant>();

    private void putOccupant(Occupant occupant) {
        this.occupants.put(occupant.getJid(), occupant);
    }

    private Occupant getOccupantByNickname(String nickname) {
        for (Map.Entry<JID, Occupant> entry : this.occupants.entrySet()) {
            if (entry.getValue().getNickname().equals(nickname)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private Occupant getOccupantByJID(JID jid) {
        return this.occupants.get(jid);
    }

    private void changeNickname(Occupant occupant, Presence newPresence) {

        for (Map.Entry<JID, Occupant> entry : this.occupants.entrySet()) {
            Presence presence = preparePresence(entry.getValue(), occupant, newPresence.getTo().getResource(), 303);
            presence.setType(PresenceType.UNAVAILABLE);
            this.reception.send(presence);
        }
        occupant.setCurrentPresence(newPresence);
        for (Map.Entry<JID, Occupant> entry : this.occupants.entrySet()) {
            Presence presence = preparePresence(entry.getValue(), occupant);
            this.reception.send(presence);
        }

    }

    /**
     * Construct room.
     * 
     * @param name
     *            room name
     */
    public Room(final Presence initialPresence, final ReceptionPlugin reception) {
        this.roomName = initialPresence.getTo().getUsername();
        this.reception = reception;
        Occupant occupant = new Occupant(initialPresence);
        putOccupant(occupant);
        Element presence = preparePresence(occupant, occupant, 201);

        this.reception.send(presence);
    }

    private Presence preparePresence(Occupant recipient, Occupant occupant, int... statuses) {
        return preparePresence(recipient, occupant, null, statuses);
    }

    private Presence preparePresence(Occupant recipient, Occupant occupant, String nick, int... statuses) {
        JID from = new JID(roomName, reception.getHostName(), occupant.getNickname());

        Presence result = new Presence();
        result.setTo(recipient.getJid());
        result.setFrom(from);

        result.setShowType(occupant.getCurrentPresence().getShowType());
        result.setStatus(occupant.getCurrentPresence().getStatus());

        Element x = new Element("x");
        x.setXMLNS("http://jabber.org/protocol/muc#user");
        result.addChild(x);

        Element item = new Element("item");
        if (occupant.getRole() != null) {
            item.setAttribute("role", occupant.getRole().name().toLowerCase());
        }
        if (occupant.getAffiliation() != null) {
            item.setAttribute("affiliation", occupant.getAffiliation().name().toLowerCase());
        }

        if (recipient.getRole() == Roles.MODERATOR) {
            item.setAttribute("jid", occupant.getJid().toString());
        }

        if (nick != null) {
            item.setAttribute("nick", nick);
        }

        x.addChild(item);

        if (statuses != null) {
            for (int statusCode : statuses) {
                Element status = new Element("status");
                status.addAttribute("code", String.valueOf(statusCode));
                x.addChild(status);
            }
        }

        if (occupant == recipient) {
            Element status = new Element("status");
            /* inform that this is your own presence */
            status.addAttribute("code", "110");
            x.addChild(status);
        }

        return result;
    }

    private void processPresence(Presence presence) {
        Occupant occupant = getOccupantByJID(presence.getFrom());
        if (occupant == null) {
            occupant = new Occupant(presence);
            putOccupant(occupant);
            // send all occupants to new occupant
            for (Occupant occFrom : this.occupants.values()) {
                if (occFrom == occupant) {
                    continue;
                }
                Element toSend = preparePresence(occupant, occFrom);
                this.reception.send(toSend);
                for (Occupant occTo : this.occupants.values()) {
                    Element toSen = preparePresence(occTo, occupant);
                    this.reception.send(toSen);
                }
            }
        } else {
            if (presence.getType() == PresenceType.UNAVAILABLE) {
                removeOccupantFromRoom(occupant);
            } else if (!occupant.getNickname().equals(presence.getTo().getResource())) {
                // user change name
                changeNickname(occupant, presence);
            } else {
                occupant.setCurrentPresence(presence);
                for (Occupant occTo : this.occupants.values()) {
                    Element toSend = preparePresence(occTo, occupant);
                    this.reception.send(toSend);
                }
            }
        }

    }

    /**
     * @param occupant
     */
    private void removeOccupantFromRoom(Occupant occupant) {
        for (Map.Entry<JID, Occupant> entry : this.occupants.entrySet()) {
            Presence presence = preparePresence(entry.getValue(), occupant);
            presence.setType(PresenceType.UNAVAILABLE);
            this.reception.send(presence);
        }
        this.occupants.remove(occupant.getJid());
    }

    public void process(Element element) {
        if ("message".equals(element.getName())) {
            processMessage(new Message(element));
        } else if ("presence".equals(element.getName())) {
            processPresence(new Presence(element));
        }

    }

    private void processMessage(Message message) {
        Occupant sender = getOccupantByJID(message.getFrom());
        if (occupants != null) {
            JID from = new JID(roomName, reception.getHostName(), sender.getNickname());
            Message msg = (Message) message.clone();
            msg.setFrom(from);

            if (message.getTo().getResource() == null) {
                msg.setType(MessageType.GROUPCHAT);
                for (Occupant recipent : this.occupants.values()) {
                    Message toSend = new Message(msg);
                    toSend.setTo(recipent.getJid());
                    this.reception.send(toSend);
                }
            } else {
                msg.setType(MessageType.CHAT);
                Occupant recipent = getOccupantByNickname(message.getTo().getResource());
                msg.setTo(recipent.getJid());
                this.reception.send(msg);
            }
        }
    }
}
