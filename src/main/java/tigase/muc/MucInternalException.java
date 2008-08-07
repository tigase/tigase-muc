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
package tigase.muc;

import tigase.xml.Element;
import tigase.xmpp.Authorization;

/**
 * 
 * <p>
 * Created: 2007-05-25 11:55:48
 * </p>
 * 
 * @author bmalkow
 * @version $Rev$
 */
public class MucInternalException extends Exception {

	private static final long serialVersionUID = 1L;

	private String code;

	private Authorization errorCondition;

	private Element item;

	private String message;

	private String name;

	private String type;

	private String xmlns = "urn:ietf:params:xml:ns:xmpp-stanzas";

	public MucInternalException(final Element item, final Authorization errorCondition) {
		this(item, errorCondition, null);
	}

	public MucInternalException(final Element item, final Authorization errorCondition, final String message) {
		this.item = item;
		this.errorCondition = errorCondition;
		this.message = message;

		this.name = errorCondition.getCondition();
		this.code = String.valueOf(errorCondition.getErrorCode());
		this.type = errorCondition.getErrorType();
	}

	/**
	 * @return Returns the code.
	 */
	public String getCode() {
		return code;
	}

	public Authorization getErrorCondition() {
		return errorCondition;
	}

	/**
	 * @return Returns the item.
	 */
	public Element getItem() {
		return item;
	}

	/**
	 * @return Returns the name.
	 */
	public String getName() {
		return name;
	}

	/**
	 * @return Returns the type.
	 */
	public String getType() {
		return type;
	}

	public Element makeElement() {
		return makeElement(true);
	}

	public Element makeElement(boolean insertOriginal) {
		Element answer = insertOriginal ? item.clone() : new Element(item.getName());
		answer.addAttribute("id", item.getAttribute("id"));
		answer.addAttribute("type", "error");
		answer.addAttribute("to", item.getAttribute("from"));
		answer.addAttribute("from", item.getAttribute("to"));

		if (this.message != null) {
			Element text = new Element("text", this.message, new String[] { "xmlns" },
					new String[] { "urn:ietf:params:xml:ns:xmpp-stanzas" });
			answer.addChild(text);
		}

		answer.addChild(makeErrorElement());
		return answer;
	}

	/**
	 * @return
	 */
	public Element makeErrorElement() {
		Element error = new Element("error");
		error.setAttribute("code", code);
		error.setAttribute("type", type);
		error.addChild(new Element(name, new String[] { "xmlns" }, new String[] { xmlns }));
		return error;
	}

}
