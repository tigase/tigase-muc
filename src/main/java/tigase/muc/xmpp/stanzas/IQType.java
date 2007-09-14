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
package tigase.muc.xmpp.stanzas;

import tigase.muc.xmpp.StanzaType;

/**
 * Types of IQ stanza.
 * <p>
 * Created: 2005-01-27 20:01:53
 * </p>
 * 
 * @author bmalkow
 * @version $Rev$
 */
public enum IQType implements StanzaType {
    /**
     * The stanza is a request for information or requirements.
     */
    GET {
        /** {@inheritDoc} */
        public String toString() {
            return "get";
        }
    },
    /**
     * The stanza provides required data, sets new values, or replaces existing
     * values.
     */
    SET {
        /** {@inheritDoc} */
        public String toString() {
            return "set";
        }
    },
    /**
     * The stanza is a response to a successful get or set request.
     */
    RESULT {
        /** {@inheritDoc} */
        public String toString() {
            return "result";
        }
    },
    /**
     * An error has occurred regarding processing or delivery of a
     * previously-sent get or set Stanza Errors.
     */
    ERROR {
        /** {@inheritDoc} */
        public String toString() {
            return "error";
        }
    },
}