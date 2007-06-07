/*
 * Java XMPP Library Copyright (C) 2005 by Bartosz M. Małkowski
 * 
 * This program is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the License,
 * or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with this program; if not,
 * write to the Free Software Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * 
 * $Id$
 */
package tigase.muc.xmpp.stanzas;

import tigase.xml.Element;

/**
 * Implements presence stanza.
 * <p>
 * Created: 2006-06-25 11:27:09
 * </p>
 * 
 * @author bmalkow
 * @version $Rev$
 */
public class Presence extends AbstractStanza<PresenceType> {

    /**
     * Construct presence stanza.
     */
    public Presence() {
        super("presence");
    }

    /**
     * Construct presence stanza.
     * 
     * @param packet
     *            packet with presence stanza.
     */
    public Presence(final Element packet) {
        super(packet);
    }

    /**
     * Construct presence stanza.
     * 
     * @param type
     *            presence show type.
     */
    public Presence(final PresenceType type) {
        super("presence");
        setType(type);
    }

    /**
     * Return presence priority.
     * 
     * @return presence priority
     */
    public int getPriority() {
        Element show = getChild("priority");
        if (show == null) {
            return 0;
        }
        try {
            return Integer.valueOf(show.getCData());
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Return status message.
     * 
     * @return status message
     */
    public String getStatus() {
        Element status = getChild("status");
        if (status != null) {
            return status.getCData();
        } else {
            return null;
        }
    }

    /** {@inheritDoc} */
    public PresenceType getType() {
        String type = getAttribute("type");
        return type == null ? null : PresenceType.valueOf(type.toUpperCase());
    }

    /**
     * Set new status message.
     * 
     * @param status
     *            status message.
     */
    public void setStatus(String status) {
        if (status == null) {
            Element el = getChild("status");
            if (el != null) {
                removeChild(el);
            }
        } else {
            Element el = getChild("status");
            if (el == null) {
                el = new Element("status");
                addChild(el);
            }
            el.setCData(status);
        }
    }

}
