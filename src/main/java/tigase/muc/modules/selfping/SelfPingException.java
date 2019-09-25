/*
 *  Tigase MUC - Multi User Chat component for Tigase
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

package tigase.muc.modules.selfping;

import tigase.muc.exceptions.MUCException;
import tigase.server.Packet;
import tigase.xml.Element;
import tigase.xmpp.Authorization;
import tigase.xmpp.PacketErrorTypeException;

public class SelfPingException
		extends MUCException {

	private final String by;

	public SelfPingException(Authorization errorCondition, String by) {
		super(errorCondition);
		this.by = by;
	}

	public SelfPingException(Authorization errorCondition, String by, String text) {
		super(errorCondition, text);
		this.by = by;
	}

	@Override
	public Packet makeElement(Packet packet, boolean insertOriginal) throws PacketErrorTypeException {
		Packet result = getErrorCondition().getResponseMessage(packet, getText(), insertOriginal);
		Element e = result.getElement().getChild("error");
		if (e != null && by != null) {
			e.setAttribute("by", by);
		}
		return result;
	}

	public String getBy() {
		return by;
	}
}
