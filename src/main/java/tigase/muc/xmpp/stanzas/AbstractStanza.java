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

import tigase.muc.xmpp.JID;
import tigase.muc.xmpp.Stanza;
import tigase.muc.xmpp.StanzaType;
import tigase.xml.Element;

/**
 * Abstract class for implement concrete XML stanzas.
 * <p>
 * Created: 2005-01-27 20:15:51
 * </p>
 * 
 * @author bmalkow
 * @version $Rev$
 */
public abstract class AbstractStanza<E extends StanzaType> extends Element implements Stanza<E> {

	/**
	 * Construct a Stanza class from XML element.
	 * 
	 * @param packet
	 *            XML element
	 */
	public AbstractStanza(final Element packet) {
		super(packet);
	}

	/**
	 * Construct an empty Stanza.
	 * 
	 * @param name
	 *            name of stanza
	 */
	public AbstractStanza(final String name) {
		super(name);
	}

	/**
	 * Construct a Stanza.
	 * 
	 * @param argName
	 *            name of stanza.
	 * @param argCData
	 *            text stanza body.
	 * @param attNames
	 *            array of attributes names
	 * @param attValues
	 *            array of attributes values
	 */
	public AbstractStanza(final String argName, final String argCData, final StringBuilder[] attNames,
			final StringBuilder[] attValues) {
		super(argName, argCData, attNames, attValues);
	}

	/** {@inheritDoc} */
	public JID getFrom() {
		String jid = getAttribute("from");
		return jid == null ? null : JID.fromString(jid);
	}

	/** {@inheritDoc} */
	public String getId() {
		return getAttribute("id");
	}

	/** {@inheritDoc} */
	public JID getTo() {
		String jid = getAttribute("to");
		return jid == null ? null : JID.fromString(jid);
	}

	/** {@inheritDoc} */
	public String getXmlLang() {
		return getAttribute("xml:lang");
	}

	/** {@inheritDoc} */
	public void setFrom(JID from) {
		if (from == null) {
			attributes.remove("from");
		} else {
			setAttribute("from", from.toString());
		}
	}

	/** {@inheritDoc} */
	public void setId(String id) {
		if (id == null) {
			attributes.remove("id");
		} else {
			setAttribute("id", id);
		}
	}

	/** {@inheritDoc} */
	public void setTo(JID to) {
		if (to == null) {
			attributes.remove("to");
		} else {
			setAttribute("to", to.toString());
		}
	}

	/** {@inheritDoc} */
	public void setType(E type) {
		if (type == null) {
			attributes.remove("type");
		} else {
			setAttribute("type", type.toString());
		}
	}

	/** {@inheritDoc} */
	public void setXmlLang(String xmlLang) {
		if (xmlLang == null) {
			attributes.remove("xml:lang");
		} else {
			setAttribute("xml:lang", xmlLang);
		}
	}
}