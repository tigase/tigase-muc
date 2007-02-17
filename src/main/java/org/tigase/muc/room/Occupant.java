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

import org.tigase.jaxmpp.xmpp.core.JID;
import org.tigase.jaxmpp.xmpp.im.presence.Presence;

import tigase.db.UserRepository;

/**
 * 
 * <p>
 * Created: 2007-01-25 20:41:09
 * </p>
 * 
 * @author bmalkow
 * @version $Rev$
 */
public class Occupant {

    private Roles role;

    private Affiliations affiliation;

    /**
     * Real occupant JID.
     */
    private JID jid;

    private Presence currentPresence;

    private OccupantConfig config;

    public Occupant(final JID roomJID, final UserRepository repository, final Presence presence) {
        this(new OccupantConfig(roomJID, presence.getFrom(), repository), presence);
    }

    public Occupant(final OccupantConfig config, final Presence presence) {
        this.jid = presence.getFrom();
        this.currentPresence = presence;
        this.config = config;
        this.affiliation = this.config.restoreAffiliation();
        this.role = this.config.restoreRole();
    }

    public JID getJid() {
        return jid;
    }

    public Presence getCurrentPresence() {
        return currentPresence;
    }

    public void setCurrentPresence(Presence currentPresence) {
        this.currentPresence = currentPresence;
    }

    public Affiliations getAffiliation() {
        return affiliation;
    }

    public void setAffiliation(Affiliations affiliation) {
        this.affiliation = affiliation;
        this.config.storeAffiliation(affiliation);
    }

    public Roles getRole() {
        return role;
    }

    public void setRole(Roles role) {
        this.role = role;
        this.config.storeRole(role);
    }

    /**
     * @return
     */
    public String getNickname() {
        return getCurrentPresence().getTo().getResource();
    }

}
