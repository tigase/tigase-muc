/**
 * Tigase MUC - Multi User Chat component for Tigase
 * Copyright (C) 2007 Tigase, Inc. (office@tigase.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. Look for COPYING file in the top folder.
 * If not, see http://www.gnu.org/licenses/.
 */
package tigase.muc.exceptions;

import tigase.component.exceptions.ComponentException;
import tigase.xmpp.Authorization;

public class MUCException extends ComponentException {

	private static final long serialVersionUID = 1L;

	public MUCException(Authorization errorCondition) {
		super(errorCondition);
	}

	/**
	 * 
	 * @param errorCondition
	 * @param text
	 *            human readable message will be send to client
	 */
	public MUCException(Authorization errorCondition, String text) {
		super(errorCondition, text);
	}

	/**
	 * 
	 * @param errorCondition
	 * @param text
	 *            human readable message will be send to client
	 * @param message
	 *            exception message for logging only
	 */
	public MUCException(Authorization errorCondition, String text, String message) {
		super(errorCondition, text, message);
	}

}
