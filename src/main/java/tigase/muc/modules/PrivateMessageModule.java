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
package tigase.muc.modules;

import java.util.List;

import tigase.criteria.Criteria;
import tigase.criteria.ElementCriteria;
import tigase.muc.MucConfig;
import tigase.muc.Role;
import tigase.muc.Room;
import tigase.muc.exceptions.MUCException;
import tigase.muc.repository.IMucRepository;
import tigase.xml.Element;
import tigase.xmpp.Authorization;

/**
 * @author bmalkow
 * 
 */
public class PrivateMessageModule extends AbstractModule {

	private static final Criteria CRIT = ElementCriteria.nameType("message", "chat");

	public PrivateMessageModule(MucConfig config, IMucRepository mucRepository) {
		super(config, mucRepository);
	}

	@Override
	public String[] getFeatures() {
		return null;
	}

	@Override
	public Criteria getModuleCriteria() {
		return CRIT;
	}

	@Override
	public List<Element> process(Element element) throws MUCException {
		try {
			final String senderJid = element.getAttribute("from");
			final String roomId = getRoomId(element.getAttribute("to"));
			final String recipientNickname = getNicknameFromJid(element.getAttribute("to"));

			if (recipientNickname == null) {
				throw new MUCException(Authorization.BAD_REQUEST);
			}

			final Room room = repository.getRoom(roomId);
			if (room == null) {
				throw new MUCException(Authorization.ITEM_NOT_FOUND);
			}

			final Role senderRole = room.getRoleByJid(senderJid);
			if (!senderRole.isSendPrivateMessages()) {
				throw new MUCException(Authorization.NOT_ALLOWED);
			}

			final String recipientJid = room.getOccupantsJidByNickname(recipientNickname);
			if (recipientJid == null) {
				throw new MUCException(Authorization.ITEM_NOT_FOUND, "Unknown recipient");
			}

			final String senderNickname = room.getOccupantsNickname(senderJid);

			final Element message = element.clone();
			message.setAttribute("from", roomId + "/" + senderNickname);
			message.setAttribute("to", recipientJid);

			return makeArray(message);
		} catch (MUCException e1) {
			throw e1;
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}

}
