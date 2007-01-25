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
import java.util.Map;

import org.tigase.jaxmpp.plugins.AbstractPlugin;
import org.tigase.jaxmpp.plugins.PluginWorkStage;
import org.tigase.jaxmpp.plugins.query.Query;
import org.tigase.jaxmpp.xmpp.core.exceptions.XMPPException;
import org.tigase.muc.room.Room;

import tigase.xml.Element;

/**
 * Implements ReceptionPlugin who knows anything about rooms and routes stanza to
 * rooms.
 * <p>
 * Created: 2007-01-25 13:15:33
 * </p>
 * 
 * @author bmalkow
 * @version $Rev$
 */
public class ReceptionPlugin extends AbstractPlugin {

    /**
     * Map of rooms. Keyed by room name.
     */
    private Map<String, Room> rooms = new HashMap<String, Room>();

    /**
     * Construct plugin.
     */
    public ReceptionPlugin() {
    }

    /** {@inheritDoc} */
    public boolean execute(Element packet) throws XMPPException {
        System.out.println(" RECEPTION: " + packet);
        return true;
    }

    /** {@inheritDoc} */
    public String getName() {
        return "Multi User Chat Plugin";
    }

    /** {@inheritDoc} */
    public Query[] getQueries() {
        return new Query[] { new Query("/presence"), new Query("/message"), new Query("/iq") };
    }

    /** {@inheritDoc} */
    public PluginWorkStage getStage() {
        return null;
    }

}
