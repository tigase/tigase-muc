/*
 * Tigase Jabber/XMPP Multi-User Chat Component
 * Copyright (C) 2008 "Bartosz M. Małkowski" <bartosz.malkowski@tigase.org>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. Look for COPYING file in the top folder.
 * If not, see http://www.gnu.org/licenses/.
 *
 * $Rev$
 * Last modified by $Author$
 * $Date$
 */
package tigase.muc.exceptions;

import tigase.server.Packet;
import tigase.xmpp.Authorization;
import tigase.xmpp.PacketErrorTypeException;

public class MUCException extends Exception {

	private static final long serialVersionUID = 1L;

	// private static final String xmlns =
	// "urn:ietf:params:xml:ns:xmpp-stanzas";

	private Authorization errorCondition;

	private String text;

	public MUCException(final Authorization errorCondition) {
		this(errorCondition, (String) null);
	}

	public MUCException(Authorization errorCondition, String message) {
		this.errorCondition = errorCondition;
		this.text = message;
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
		final StringBuilder sb = new StringBuilder();
		sb.append("[");
		sb.append(errorCondition.name()).append(" ");
		if (text != null) {
			sb.append("\"").append(text).append("\" ");
		}

		sb.append("]");
		return sb.toString();
	}

	/**
	 * @return Returns the name.
	 */
	public String getName() {
		return errorCondition.getCondition();
	}

	public String getText() {
		return text;
	}

	/**
	 * @return Returns the type.
	 */
	public String getType() {
		return errorCondition.getErrorType();
	}

	public Packet makeElement(Packet packet, boolean insertOriginal) throws PacketErrorTypeException {
		Packet result = errorCondition.getResponseMessage(packet, text, insertOriginal);
		return result;
	}

}
