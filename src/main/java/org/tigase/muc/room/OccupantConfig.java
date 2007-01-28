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

import tigase.db.TigaseDBException;
import tigase.db.UserNotFoundException;
import tigase.db.UserRepository;

/**
 * 
 * <p>
 * Created: 2007-01-28 16:11:13
 * </p>
 * 
 * @author bmalkow
 * @version $Rev$
 */
public class OccupantConfig {

    private UserRepository repository;

    private JID occupantJID;

    private JID roomJID;

    public OccupantConfig(final JID roomJID, final JID occupantJID, UserRepository repository) {
        this.repository = repository;
        this.roomJID = roomJID;
        this.occupantJID = occupantJID.getBareJID();
    }

    public void storeAffiliation(Affiliations affiliations) {
        try {
            this.repository.setData(roomJID.toString(), occupantJID.toString(), "affiliations",
                    affiliations == null ? null : affiliations.name());
        } catch (UserNotFoundException e) {
        } catch (TigaseDBException e) {
            e.printStackTrace();
        }
    }

    public Affiliations restoreAffiliation() {
        try {
            String affName = this.repository.getData(roomJID.toString(), occupantJID.toString(), "affiliations");
            return Affiliations.valueOf(affName);
        } catch (UserNotFoundException e) {
            return null;
        } catch (TigaseDBException e) {
            throw new RuntimeException("", e);
        } catch (Exception e) {
            return null;
        }
    }

    public void storeRole(Roles role) {
        try {
            this.repository.setData(roomJID.toString(), occupantJID.toString(), "role", role == null ? null : role
                    .name());
        } catch (UserNotFoundException e) {
        } catch (TigaseDBException e) {
            throw new RuntimeException("", e);
        }
    }

    public Roles restoreRole() {
        try {
            String roleName = this.repository.getData(roomJID.toString(), occupantJID.toString(), "role");
            return Roles.valueOf(roleName);
        } catch (UserNotFoundException e) {
            return null;
        } catch (TigaseDBException e) {
            throw new RuntimeException("", e);
        } catch (Exception e) {
            return null;
        }
    }

}
