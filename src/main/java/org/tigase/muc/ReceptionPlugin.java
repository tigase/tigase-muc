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
import java.util.logging.Logger;

import org.tigase.jaxmpp.plugins.AbstractPlugin;
import org.tigase.jaxmpp.plugins.PluginWorkStage;
import org.tigase.jaxmpp.plugins.query.Query;
import org.tigase.jaxmpp.xmpp.core.JID;
import org.tigase.jaxmpp.xmpp.core.exceptions.XMPPException;
import org.tigase.jaxmpp.xmpp.im.presence.Presence;
import org.tigase.muc.room.Room;

import tigase.db.UserRepository;
import tigase.xml.Element;

/**
 * Implements ReceptionPlugin who knows anything about rooms and routes stanza
 * to rooms.
 * <p>
 * Created: 2007-01-25 13:15:33
 * </p>
 * 
 * @author bmalkow
 * @version $Rev$
 */
public class ReceptionPlugin extends AbstractPlugin {

    /**
     * 
     */
    private String hostName;

    /**
     * Logger.
     */
    private Logger logger = Logger.getLogger(this.getClass().getName());

    /**
     * Map of rooms. Keyed by room name.
     */
    private Map<String, Room> rooms = new HashMap<String, Room>();

    /**
     * Construct plugin.
     */
    public ReceptionPlugin() {
    }

    private UserRepository repository;

    /** {@inheritDoc} */
    public boolean execute(Element packet) throws XMPPException {
        String roomName = JID.fromString(packet.getAttribute("to")).getUsername();
        Room room = this.rooms.get(roomName);
        if (room == null) {
            logger.info("Creating room with name: " + roomName);
            room = new Room(this.repository, new Presence(packet), this);
            this.rooms.put(roomName, room);
        } else {
            room.process(packet);
        }

        return true;
    }

    /**
     * Get MUC hostname.
     * 
     * @return hostname.
     */
    public String getHostName() {
        return hostName;
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

    /**
     * Send element.
     * 
     * @param element
     *            element to send.
     */
    public void send(Element element) {
        toSend(element);
    }

    /**
     * Set MUC hostname.
     * 
     * @param hostName
     *            hostname to set.
     */
    public void setHostName(String hostName) {
        this.hostName = hostName;
    }

    /**
     * @param room
     */
    public void roomCanByDischarge(Room room) {
    }

    public UserRepository getRepository() {
        return repository;
    }

    public void setRepository(UserRepository repository) {
        this.repository = repository;
    }

}
