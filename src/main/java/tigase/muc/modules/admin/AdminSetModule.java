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
package tigase.muc.modules.admin;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import tigase.criteria.Criteria;
import tigase.criteria.ElementCriteria;
import tigase.muc.Affiliation;
import tigase.muc.MucInternalException;
import tigase.muc.Role;
import tigase.muc.RoomContext;
import tigase.muc.modules.AbstractModule;
import tigase.muc.modules.RoomModule;
import tigase.muc.modules.PresenceModule;
import tigase.muc.xmpp.JID;
import tigase.muc.xmpp.stanzas.IQ;
import tigase.muc.xmpp.stanzas.Presence;
import tigase.xml.Element;

public class AdminSetModule extends AbstractModule {

    private static final Criteria CRIT = new ElementCriteria("iq", new String[] { "type" }, new String[] { "set" }).add(ElementCriteria.name("query",
            "http://jabber.org/protocol/muc#admin"));

    @Override
    public Criteria getModuleCriteria() {
        return CRIT;
    }

    @Override
    protected List<Element> intProcess(RoomContext roomContext, Element element) throws MucInternalException {
        IQ iq = new IQ(element);


        List<Element> result = new LinkedList<Element>();

        Affiliation senderAffiliation = roomContext.getAffiliation(iq.getFrom());
        Role senderRole = roomContext.getRole(iq.getFrom());

        Element query = iq.getChild("query", "http://jabber.org/protocol/muc#admin");
        List<Element> items = query.getChildren("/query");

        Map<JID, Affiliation> affiliationsToSet = new HashMap<JID, Affiliation>();
        Map<String, Role> rolesToSet = new HashMap<String, Role>();
        Set<JID> jidsToRemove = new HashSet<JID>();

        for (Element item : items) {
            Set<JID> occupantJids = new HashSet<JID>();

            String occupantNick = item.getAttribute("nick");
            if (occupantNick != null) {
                occupantJids.add(roomContext.getOccupantsByNick().get(occupantNick));
            }
            Role newRole;
            try {
                newRole = Role.valueOf(item.getAttribute("role").toUpperCase());
            } catch (Exception e) {
                newRole = null;
            }

            JID occupantBareJid = JID.fromString(item.getAttribute("jid"));
            if (occupantBareJid != null) {
                occupantJids.addAll(roomContext.getOccupantJidsByBare(occupantBareJid));
            } else {
                occupantBareJid = roomContext.getOccupantsByNick().get(occupantNick).getBareJID();
            }
            Affiliation newAffiliation;
            try {
                newAffiliation = Affiliation.valueOf(item.getAttribute("affiliation").toUpperCase());
            } catch (Exception e) {
                newAffiliation = null;
            }

            Affiliation currentAffiliation = roomContext.getAffiliation(occupantBareJid);

            if (senderAffiliation.getWeight() < currentAffiliation.getWeight()) {
                throw new MucInternalException(item, "forbidden", "403", "auth");
            }

            // process bussines logic ;-)
            if (newAffiliation != null && occupantBareJid != null) {
                affiliationsToSet.put(occupantBareJid, newAffiliation);
            }
            if (newRole != null && occupantNick != null) {
                if (newRole == Role.MODERATOR && (senderAffiliation != Affiliation.ADMIN && senderAffiliation != Affiliation.OWNER)) {
                    throw new MucInternalException(item, "not-allowed", "405", "cancel");
                } else if (senderRole != Role.MODERATOR) {
                    throw new MucInternalException(item, "not-allowed", "405", "cancel");
                }
                rolesToSet.put(occupantNick, newRole);
            }

            List<Element> reasons = item.getChildren("/item");

            for (JID occupantJid : occupantJids) {
                occupantNick = roomContext.getOccupantsByJID().get(occupantJid);
                // Service Informs all Occupants
                for (Entry<String, JID> entry : roomContext.getOccupantsByNick().entrySet()) {
                    // preparing presence stanza
                    Presence presence = roomContext.getLastReceivedPresence().get(occupantJid);
                    if (presence == null) {
                        continue;
                    } else {
                        presence = PresenceModule.clonePresence(presence);
                    }
                    presence.setAttribute("from", roomContext.getId() + "/" + occupantNick);
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
                    Role roleToSend = newRole != null ? newRole : roomContext.getRole(occupantJid);
                    Affiliation affiliationToSend = newAffiliation != null ? newAffiliation : roomContext.getAffiliation(occupantJid);
                    Element subItem = PresenceModule.preparePresenceSubItem(roomContext, occupantJid, affiliationToSend, roleToSend, entry.getValue());
                    x.addChild(subItem);
                    if (reasons != null && reasons.size() > 0) {
                        subItem.addChildren(reasons);
                    }

                    result.add(presence);
                }
            }
        }
        for (Map.Entry<JID, Affiliation> entry : affiliationsToSet.entrySet()) {
            roomContext.setAffiliation(entry.getKey(), entry.getValue());
        }
        for (Map.Entry<String, Role> entry : rolesToSet.entrySet()) {
            roomContext.setRole(entry.getKey(), entry.getValue());
        }
        for (JID jid : jidsToRemove) {
            roomContext.removeOccupantByJID(jid);
        }

        // Service Informs Admin or Owner of Success
        Element answer = new Element("iq");
        answer.addAttribute("id", iq.getAttribute("id"));
        answer.addAttribute("type", "result");
        answer.addAttribute("to", iq.getAttribute("from"));
        answer.addAttribute("from", roomContext.getId());
        result.add(answer);

        return result;
    }

}