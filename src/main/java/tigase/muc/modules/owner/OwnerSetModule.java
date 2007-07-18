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
 * Last modified by $Author: $
 * $Date: $
 */
package tigase.muc.modules.owner;

import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;

import tigase.criteria.Criteria;
import tigase.criteria.ElementCriteria;
import tigase.muc.Affiliation;
import tigase.muc.MucInternalException;
import tigase.muc.RoomContext;
import tigase.muc.RoomListener;
import tigase.muc.modules.AbstractModule;
import tigase.muc.xmpp.JID;
import tigase.muc.xmpp.stanzas.IQ;
import tigase.muc.xmpp.stanzas.Message;
import tigase.muc.xmpp.stanzas.MessageType;
import tigase.muc.xmpp.stanzas.Presence;
import tigase.muc.xmpp.stanzas.PresenceType;
import tigase.xml.Element;

/**
 * 
 * <p>
 * Created: 2007-06-20 19:10:33
 * </p>
 * 
 * @author bmalkow
 * @version $Rev$
 */
public class OwnerSetModule extends AbstractModule {

    private static final Criteria CRIT = new ElementCriteria("iq", new String[] { "type" }, new String[] { "set" }).add(ElementCriteria.name("query",
            "http://jabber.org/protocol/muc#owner"));

    private static final String XMLNS_MUC_OWNER = "http://jabber.org/protocol/muc#owner";

    private RoomListener roomListener;

    public OwnerSetModule(RoomListener roomListener) {
        this.roomListener = roomListener;
    }

    @Override
    public Criteria getModuleCriteria() {
        return CRIT;
    }

    /*
     * (non-Javadoc)
     * 
     * @see tigase.muc.modules.AbstractModule#intProcess(tigase.muc.RoomContext,
     *      tigase.xml.Element)
     */
    @Override
    protected List<Element> intProcess(RoomContext roomContext, Element element) throws MucInternalException {
        IQ iq = new IQ(element);

        if (Affiliation.OWNER != roomContext.getAffiliation(iq.getFrom())) {
            throw new MucInternalException(iq, "forbidden", "403", "auth");
        }

        List<Element> result = new ArrayList<Element>();

        Element query = iq.getChild("query");
        Element destroy = query.getChild("destroy");
        Element x = query.getChild("x", "jabber:x:data");

        boolean ok = false;
        if (x != null) {
            ok = roomContext.parseConfig(x);
            if (roomContext.isLockedRoom()) {
                Message message = new Message(iq.getFrom(), "Room is unlocked!");
                message.setFrom(JID.fromString(roomContext.getId()));
                message.setType(MessageType.GROUPCHAT);
                result.add(message);
            }
            roomContext.setLockedRoom(false);

            if (this.roomListener != null) {
                this.roomListener.onConfigurationChange(roomContext);
            }

        } else if (destroy != null) {
            log.info("Destroying room " + roomContext.getId());
            // Service Removes Each Occupant
            for (Entry<JID, String> entry : roomContext.getOccupantsByJID().entrySet()) {
                Presence presence = new Presence(PresenceType.UNAVAILABLE);
                presence.setTo(entry.getKey());
                presence.setAttribute("from", roomContext.getId() + "/" + entry.getValue());
                Element destroyX = new Element("x", new String[] { "xmlns" }, new String[] { "http://jabber.org/protocol/muc#user" });
                presence.addChild(destroyX);
                destroyX.addChild(new Element("item", new String[] { "affiliation", "role" }, new String[] { "none", "none" }));
                destroyX.addChild(destroy);
                result.add(presence);
            }

            if (this.roomListener != null) {
                this.roomListener.onDestroy(roomContext);
            }

        }

        // answer OK
        if (ok) {
            Element answer = new Element("iq");
            answer.addAttribute("id", iq.getAttribute("id"));
            answer.addAttribute("type", "result");
            answer.addAttribute("to", iq.getAttribute("from"));
            answer.addAttribute("from", roomContext.getId());
            result.add(answer);
        }
        return result;
    }

}