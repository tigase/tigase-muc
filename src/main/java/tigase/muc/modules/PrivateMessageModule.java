/*
 * PrivateMessageModule.java
 *
 * Tigase Jabber/XMPP Server
 * Copyright (C) 2004-2012 "Artur Hefczyc" <artur.hefczyc@tigase.org>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
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
 */



package tigase.muc.modules;

//~--- non-JDK imports --------------------------------------------------------

import tigase.component.ElementWriter;

import tigase.criteria.Criteria;
import tigase.criteria.ElementCriteria;

import tigase.muc.exceptions.MUCException;
import tigase.muc.MucConfig;
import tigase.muc.repository.IMucRepository;
import tigase.muc.Role;
import tigase.muc.Room;

import tigase.server.Packet;

import tigase.util.TigaseStringprepException;

import tigase.xml.Element;

import tigase.xmpp.Authorization;
import tigase.xmpp.BareJID;
import tigase.xmpp.JID;

//~--- JDK imports ------------------------------------------------------------

import java.util.Collection;

/**
 * @author bmalkow
 *
 */
public class PrivateMessageModule
				extends AbstractModule {
	private static final Criteria CRIT = ElementCriteria.nameType("message", "chat");

	//~--- constructors ---------------------------------------------------------

	/**
	 * Constructs ...
	 *
	 *
	 * @param config
	 * @param writer
	 * @param mucRepository
	 */
	public PrivateMessageModule(MucConfig config, ElementWriter writer,
															IMucRepository mucRepository) {
		super(config, writer, mucRepository);
	}

	//~--- get methods ----------------------------------------------------------

	/**
	 * Method description
	 *
	 *
	 * @return
	 */
	@Override
	public String[] getFeatures() {
		return null;
	}

	/**
	 * Method description
	 *
	 *
	 * @return
	 */
	@Override
	public Criteria getModuleCriteria() {
		return CRIT;
	}

	//~--- methods --------------------------------------------------------------

	/**
	 * Method description
	 *
	 *
	 * @param element
	 *
	 * @throws MUCException
	 */
	@Override
	public void process(Packet element) throws MUCException {
		try {
			final JID senderJID =
				JID.jidInstance(element.getAttributeStaticStr(Packet.FROM_ATT));
			final BareJID roomJID =
				BareJID.bareJIDInstance(element.getAttributeStaticStr(Packet.TO_ATT));
			final String recipientNickname =
				getNicknameFromJid(JID.jidInstance(element.getAttributeStaticStr(Packet.TO_ATT)));

			if (recipientNickname == null) {
				throw new MUCException(Authorization.BAD_REQUEST);
			}

			final Room room = repository.getRoom(roomJID);

			if (room == null) {
				throw new MUCException(Authorization.ITEM_NOT_FOUND);
			}

			final String senderNickname = room.getOccupantsNickname(senderJID);
			final Role senderRole       = room.getRole(senderNickname);

			if (!senderRole.isSendPrivateMessages()) {
				throw new MUCException(Authorization.NOT_ALLOWED);
			}

			final Collection<JID> recipientJids =
				room.getOccupantsJidsByNickname(recipientNickname);

			if (recipientJids.isEmpty()) {
				throw new MUCException(Authorization.ITEM_NOT_FOUND, "Unknown recipient");
			}
			for (JID jid : recipientJids) {
				final Element message = element.getElement().clone();

				message.setAttribute("from", JID.jidInstance(roomJID, senderNickname).toString());
				message.setAttribute("to", jid.toString());
				writer.write(Packet.packetInstance(message));
			}
		} catch (MUCException e1) {
			throw e1;
		} catch (TigaseStringprepException e) {
			throw new MUCException(Authorization.BAD_REQUEST);
		} catch (Exception e) {
			e.printStackTrace();

			throw new RuntimeException(e);
		}
	}
}


//~ Formatted in Tigase Code Convention on 13/02/20
