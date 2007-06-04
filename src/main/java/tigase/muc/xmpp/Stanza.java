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
 *  $Id: Stanza.java 80 2007-01-04 11:05:50Z bmalkow $
 */
package tigase.muc.xmpp;

/**
 * Interface for XML stanza.
 * <p>
 * Created: 2005-01-27 19:39:43
 * </p>
 * 
 * @author bmalkow
 * @version $Rev: 80 $
 */
public interface Stanza<E extends StanzaType> {

    /**
     * Set recipents Jabber Identifier.
     * 
     * @param to
     *            JID of recipent
     */
    void setTo(JID to);

    /**
     * Return recipents Jabber Identifier.
     * 
     * @return JID of recipent.
     */
    JID getTo();

    /**
     * Set sender Jabber Identifier.
     * 
     * @param from
     *            JID of sender.
     */
    void setFrom(JID from);

    /**
     * Return sender Jabber Identifier.
     * 
     * @return JID of sender.
     */
    JID getFrom();

    /**
     * Set <code>id</code> parameter. Need in request-response.
     * 
     * @param id
     *            identifier
     */
    void setId(String id);

    /**
     * Return <code>id</code> parameter.
     * 
     * @return identifier
     */
    String getId();

    /**
     * Set stanza type kind of {@link StanzaType}.
     * 
     * @see StanzaType
     * @param type
     *            type of stanza
     */
    void setType(E type);

    /**
     * Get stanza type.
     * 
     * @see {@link StanzaType}
     * @return type of stanza
     */
    E getType();

    /**
     * Set XML natural language name.
     * 
     * @param xmlLang
     *            language name.
     */
    void setXmlLang(String xmlLang);

    /**
     * Return XML natural language name.
     * 
     * @return language name
     */
    String getXmlLang();
}
