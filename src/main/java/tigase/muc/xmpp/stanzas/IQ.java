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

import tigase.xml.Element;

/**
 * Implementation of IQ stanza.
 * <p>
 * Created: 2005-03-24 13:23:26
 * </p>
 * 
 * @author bmalkow
 * @version $Rev$
 */
public class IQ extends AbstractStanza<IQType> {

    /**
     * Construct IQ stanza with type.
     * 
     * @param iqType
     *            IQ stanza type
     */
    public IQ(final IQType iqType) {
        super("iq");
        setType(iqType);
    };

    /**
     * Construct a IQ from XML element.
     * 
     * @param packet
     *            XML element.
     */
    public IQ(final Element packet) {
        super(packet);
    }

    /**
     * Create Result IQ stanza. TO addres is copied from FROM. ID is copied from
     * current stanza.
     * 
     * @return new IQ stanza.
     */
    public IQ createResultIQ() {
        IQ result = new IQ(IQType.RESULT);
        result.setId(getId());
        result.setTo(getFrom());
        return result;
    }

    /** {@inheritDoc} */
    public IQType getType() {
        String type = getAttribute("type");
        return type == null ? null : IQType.valueOf(type.toUpperCase());
    }

}
