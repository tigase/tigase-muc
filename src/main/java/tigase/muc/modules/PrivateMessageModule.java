/*
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
package tigase.muc.modules;

import tigase.criteria.Criteria;
import tigase.criteria.ElementCriteria;
import tigase.kernel.beans.Bean;
import tigase.kernel.beans.Inject;
import tigase.muc.Role;
import tigase.muc.Room;
import tigase.muc.RoomConfig;
import tigase.muc.exceptions.MUCException;
import tigase.muc.repository.IMucRepository;
import tigase.server.Packet;
import tigase.util.stringprep.TigaseStringprepException;
import tigase.xml.Element;
import tigase.xmpp.Authorization;
import tigase.xmpp.jid.BareJID;
import tigase.xmpp.jid.JID;

import java.util.Collection;

/**
 * @author bmalkow
 */
@Bean(name = PrivateMessageModule.ID, active = true)
public class PrivateMessageModule
		extends AbstractMucModule {

	public static final String ID = "privatemessages";
	private static final Criteria CRIT = ElementCriteria.nameType("message", "chat");
	@Inject
	private IMucRepository repository;

	@Override
	public String[] getFeatures() {
		return null;
	}

	@Override
	public Criteria getModuleCriteria() {
		return CRIT;
	}

	@Override
	public void process(Packet element) throws MUCException {
		try {
			final JID senderJID = JID.jidInstance(element.getAttributeStaticStr(Packet.FROM_ATT));
			final BareJID roomJID = BareJID.bareJIDInstance(element.getAttributeStaticStr(Packet.TO_ATT));
			final String recipientNickname = getNicknameFromJid(
					JID.jidInstance(element.getAttributeStaticStr(Packet.TO_ATT)));

			if (recipientNickname == null) {
				throw new MUCException(Authorization.BAD_REQUEST);
			}

			final Room room = repository.getRoom(roomJID);

			if (room == null) {
				throw new MUCException(Authorization.ITEM_NOT_FOUND);
			}

			final String senderNickname = room.getOccupantsNickname(senderJID);
			final Role senderRole = room.getRole(senderNickname);

			if (!isAllowedToSendPrivateMessage(room.getConfig(), senderRole)) {
				throw new MUCException(Authorization.NOT_ALLOWED,
									   "Your role is '" + senderRole + "'. You can't send private message.");
			}

			validateRTBL(senderJID.getBareJID(), room.getAffiliation(senderNickname).getAffiliation());

			final Collection<JID> recipientJids = room.getOccupantsJidsByNickname(recipientNickname);

			if (recipientJids.isEmpty()) {
				throw new MUCException(Authorization.ITEM_NOT_FOUND, "Unknown recipient");
			}
			for (JID jid : recipientJids) {
				final Element message = element.getElement().clone();

				message.setAttribute("from", JID.jidInstance(roomJID, senderNickname).toString());
				message.setAttribute("to", jid.toString());
				Packet p = Packet.packetInstance(message);
				p.setXMLNS(Packet.CLIENT_XMLNS);
				write(p);
			}
		} catch (MUCException e1) {
			throw e1;
		} catch (TigaseStringprepException e) {
			throw new MUCException(Authorization.BAD_REQUEST);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private boolean isAllowedToSendPrivateMessage(final RoomConfig config, final Role role) {
		switch (config.getPrivateMessageACL()) {
			case none:
				return false;
			case anyone:
				return role.isSendPrivateMessages();
			case participants:
				return role.isSendMessagesToAll();
			case moderators:
				return role == Role.moderator;
		}
		return true;
	}
}