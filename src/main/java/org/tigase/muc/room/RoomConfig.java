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
import tigase.db.UserExistsException;
import tigase.db.UserRepository;

/**
 * 
 * <p>
 * Created: 2007-01-28 14:32:57
 * </p>
 * 
 * @author bmalkow
 * @version $Rev$
 */
public class RoomConfig {

    private UserRepository configRepository;

    private JID roomJID;

    public RoomConfig(JID roomJID, final UserRepository configRepository) {
        this.configRepository = configRepository;
        this.roomJID = roomJID;
    }

    public boolean init() {
        try {
            this.configRepository.addUser(roomJID.toString());
            return true;
        } catch (UserExistsException e) {
            return false;
        } catch (TigaseDBException e) {
            throw new RuntimeException("", e);
        }
    }

    public JID getRoomJID() {
        return roomJID;
    }

    public String getRoomName() {
        return roomJID.getUsername();
    }

    /**
     * @return
     */
    public UserRepository getRepository() {
        return this.configRepository;
    }

}
