/*
 * Tigase Jabber/XMPP Multi-User Chat Component
 * Copyright (C) 2008 "Bartosz M. Małkowski" <bartosz.malkowski@tigase.org>
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
package tigase.muc.exceptions;

import tigase.util.JIDUtils;
import tigase.xml.Element;
import tigase.xmpp.Authorization;

public class MUCException extends Exception {

	private static final long serialVersionUID = 1L;

	private final Authorization errorCondition;

	private final String message;

	private String xmlns = "urn:ietf:params:xml:ns:xmpp-stanzas";

	public MUCException(final Authorization errorCondition) {
		this(errorCondition, (String) null);
	}

	public MUCException(final Authorization errorCondition, final String message) {
		this.errorCondition = errorCondition;
		this.message = message;
	}

	/**
	 * @return Returns the code.
	 */
	public String getCode() {
		return String.valueOf(this.errorCondition.getErrorCode());
	}

	public Authorization getErrorCondition() {
		return errorCondition;
	}

	@Override
	public String getMessage() {
		return message;
	}

	/**
	 * @return Returns the name.
	 */
	public String getName() {
		return errorCondition.getCondition();
	}

	/**
	 * @return Returns the type.
	 */
	public String getType() {
		return errorCondition.getErrorType();
	}

	public Element makeElement(final Element item, final boolean insertOriginal) {
		Element answer = insertOriginal ? item.clone() : new Element(item.getName());
		String id = item.getAttribute("id");
		if (id != null)
			answer.addAttribute("id", id);
		answer.addAttribute("type", "error");
		answer.addAttribute("to", item.getAttribute("from"));
		answer.addAttribute("from", JIDUtils.getNodeID(item.getAttribute("to")));

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
	private Element makeErrorElement() {
		Element error = new Element("error");
		error.setAttribute("code", String.valueOf(this.errorCondition.getErrorCode()));
		error.setAttribute("type", this.errorCondition.getErrorType());
		error.addChild(new Element(this.errorCondition.getCondition(), new String[] { "xmlns" }, new String[] { xmlns }));
		return error;
	}

}
