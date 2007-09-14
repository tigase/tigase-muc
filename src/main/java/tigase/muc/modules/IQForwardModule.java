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

import java.util.LinkedList;
import java.util.List;

import tigase.criteria.Criteria;
import tigase.criteria.ElementCriteria;
import tigase.muc.MucInternalException;
import tigase.muc.Role;
import tigase.muc.RoomContext;
import tigase.muc.xmpp.JID;
import tigase.muc.xmpp.stanzas.IQ;
import tigase.muc.xmpp.stanzas.IQType;
import tigase.xml.Element;

/**
 * 
 * <p>
 * Created: 2007-06-23 09:02:28
 * </p>
 * 
 * @author bmalkow
 * @version $Rev$
 */
public class IQForwardModule extends AbstractModule {

    @Override
    protected List<Element> intProcess(RoomContext roomContext, Element el) throws MucInternalException {
        IQ iq = new IQ(el);

        List<Element> result = new LinkedList<Element>();
        String senderNick = roomContext.getOccupantsByJID().get(iq.getFrom());
        String recipentNick = iq.getTo().getResource();

        if (recipentNick == null) {
            return null;
        }

        JID recipentJID = roomContext.getOccupantsByNick().get(recipentNick);

        if (recipentJID == null) {
            throw new MucInternalException(iq, "item-not-found", "404", "cancel");
        }

        JID senderJID = JID.fromString(iq.getAttribute("from"));

        // broadcast message
        if (roomContext.getOccupantsByJID().get(senderJID) == null || roomContext.getRole(senderJID) == Role.VISITOR) {
            throw new MucInternalException(iq, "not-acceptable", "406", "modify", "Only occupants are allowed to send messages to occupants");
        }

        Element message = iq.clone();
        message.setAttribute("from", roomContext.getId() + "/" + senderNick);
        message.setAttribute("to", recipentJID.toString());
        result.add(message);

        return result;
    }

    private static final Criteria CRIT = ElementCriteria.name("iq");

    @Override
    public Criteria getModuleCriteria() {
        return CRIT;
    }

}
