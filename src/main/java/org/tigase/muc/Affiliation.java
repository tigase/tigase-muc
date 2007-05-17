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

/**
 * A long-lived association or connection with a room; the possible affiliations
 * are "owner", "admin", "member", and "outcast" (naturally it is also possible
 * to have no affiliation); affiliation is distinct from role. An affiliation
 * lasts across a user's visits to a room.
 * <p>
 * Created: 2007-01-26 16:30:15
 * </p>
 * 
 * @author bmalkow
 * @version $Rev$
 */
public enum Affiliation {
    /**
     * The Jabber user who created the room or a Jabber user who has been
     * designated by the room creator or owner as someone with owner privileges
     * (if allowed); is allowed to change defining room features as well as
     * perform all administrative functions.
     */
    OWNER,
    /**
     * A user empowered by the room owner to perform administrative functions
     * such as banning users; however, is not allowed to change defining room
     * features.
     */
    ADMIN,
    /**
     * A user who is on the "whitelist" for a members-only room or who is
     * registered with an open room.
     */
    MEMBER,
    /**
     * A user who is on the "whitelist" for a members-only room or who is
     * registered with an open room.
     */
    OUTCAST
}
