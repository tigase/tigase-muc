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
package tigase.muc.xmpp;

import java.io.Serializable;

/**
 * Represents a Jabber ID (JID) object.
 * <p>
 * Created: 2005-01-18 19:58:39
 * </p>
 * 
 * @author bmalkow
 * @version $Rev$
 */
public class JID implements Serializable {

    /**
     * Comment for <code>serialVersionUID</code>.
     */
    private static final long serialVersionUID = 1L;

    /**
     * This method creates <code>JID</code> object from a <code>String</code>.
     * 
     * @param jid
     *            <code>String</code> value holding JID
     * @return <code>JID</code> object if <code>jid</code> is correct.
     */
    public static JID fromString(String jid) {
        if (jid == null) {
            return null;
        }
        String usr = null;
        String hst = null;
        String rsr = null;

        int pos = jid.indexOf('@');
        if (pos != -1) {
            usr = jid.substring(0, pos);
            jid = jid.substring(pos + 1);
        } else if (pos == 0) {
            throw new RuntimeException("Invalid JID: found @ but no username specified");
        }

        pos = jid.indexOf('/');
        if (pos == -1) {
            hst = jid;
        } else {
            hst = jid.substring(0, pos);
            jid = jid.substring(pos + 1);
            rsr = jid;
        }

        return new JID(usr, hst, rsr);
    }

    /**
     * host name.
     */
    private String host;

    /**
     * resource name.
     */
    private String resource;

    /**
     * user name.
     */
    private String username;

    /**
     * Construct a JID.
     */
    private JID() {
    }

    /**
     * Creates a new <code>JID</code> object with empty resource.
     * 
     * @param username
     *            <code>String</code> holding username or <code>null</code>
     * @param host
     *            <code>String</code> holding host name or <code>null</code>
     */
    public JID(final String username, final String host) {
        this(username, host, null);
    }

    /**
     * Creates a new <code>JID</code> object.
     * 
     * @param username
     *            <code>String</code> holding username or <code>null</code>
     * @param host
     *            <code>String</code> holding host name or <code>null</code>
     * @param resource
     *            <code>String</code> holding resource or <code>null</code>
     */
    public JID(final String username, final String host, final String resource) {
        this.username = username;
        this.host = host;
        this.resource = resource;

        if ("".equals(this.username)) {
            this.username = null;
        }
        if ("".equals(this.host)) {
            this.host = null;
        }
        if ("".equals(this.resource)) {
            this.resource = null;
        }

        if (this.host == null) {
            throw new RuntimeException("Illegal host value.");
        }
    }

    /** {@inheritDoc} */
    @Override
    public boolean equals(Object other) {
        if (!(other instanceof JID)) {
            return false;
        }
        if (this == other) {
            return true;
        }
        if ((this.toString() == null) || (other.toString() == null)) {
            return false;
        }
        return this.toString().equals(other.toString());
    }

    /**
     * Return a bare JID. Without roesource name.
     * 
     * @return bare JID
     */
    public JID getBareJID() {
        JID result = new JID();
        result.host = this.host;
        result.username = this.username;
        return result;

    }

    /**
     * Returns the host name if was specified, otherwise <code>null</code>.
     * 
     * @return <code>String</code> holding the host name.
     */
    public String getHost() {
        return host;
    }

    /**
     * Returns the resource if was specified, otherwise <code>null</code>.
     * 
     * @return <code>String</code> holding the resource.
     */
    public String getResource() {
        return resource;
    }

    /**
     * Returns the username if was specified, otherwise <code>null</code>.
     * 
     * @return <code>String</code> holding the username.
     */
    public String getUsername() {
        return username;
    }

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        return toString().hashCode();
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        StringBuffer out = new StringBuffer();
        if (this.username != null) {
            out.append(this.username);
            out.append("@");
        }
        out.append(this.host);
        if (this.resource != null) {
            out.append("/");
            out.append(this.resource);
        }
        return out.toString();
    }

}