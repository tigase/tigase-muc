/*  Java XMPP Library
 *  Copyright (C) 2005 by Bartosz M. Małkowski
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
package tigase.muc.xmpp.stanzas;

import tigase.muc.xmpp.StanzaType;

/**
 * Types od Presence stanza.
 * <p>
 * Created: 2005-01-27 19:53:54
 * </p>
 * 
 * @author bmalkow
 * @version $Rev$
 */
public enum PresenceType implements StanzaType {
    /**
     * Signals that the entity is no longer available for communication.
     */
    UNAVAILABLE {
        /** {@inheritDoc} */
        public String toString() {
            return "unavailable";
        }
    },
    /**
     * The sender wishes to subscribe to the recipient's presence.
     */
    SUBSCRIBE {
        /** {@inheritDoc} */
        public String toString() {
            return "subscribe";
        }
    },
    /**
     * The sender has allowed the recipient to receive their presence.
     */
    SUBSCRIBED {
        /** {@inheritDoc} */
        public String toString() {
            return "subscribed";
        }
    },
    /**
     * The sender is unsubscribing from another entity's presence.
     */
    UNSUBSCRIBE {
        /** {@inheritDoc} */
        public String toString() {
            return "unsubscribe";
        }
    },
    /**
     * The subscription request has been denied or a previously-granted
     * subscription has been cancelled.
     */
    UNSUBSCRIBED {
        /** {@inheritDoc} */
        public String toString() {
            return "unsubscribed";
        }
    },
    /**
     * A request for an entity's current presence; SHOULD be generated only by a
     * server on behalf of a user.
     */
    PROBE {
        /** {@inheritDoc} */
        public String toString() {
            return "probe";
        }
    },
    /**
     * An error has occurred regarding processing or delivery of a
     * previously-sent presence stanza.
     */
    ERROR {
        /** {@inheritDoc} */
        public String toString() {
            return "error";
        }
    }
}
