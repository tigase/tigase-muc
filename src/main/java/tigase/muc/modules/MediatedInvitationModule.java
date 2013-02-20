/*
 * MediatedInvitationModule.java
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

import tigase.muc.Affiliation;
import tigase.muc.exceptions.MUCException;
import tigase.muc.MucConfig;
import tigase.muc.repository.IMucRepository;
import tigase.muc.Role;
import tigase.muc.Room;

import tigase.server.Packet;

import tigase.xml.Element;

import tigase.xmpp.Authorization;
import tigase.xmpp.BareJID;
import tigase.xmpp.JID;

//~--- JDK imports ------------------------------------------------------------

import java.util.List;

/**
 * @author bmalkow
 *
 */
public class MediatedInvitationModule
				extends AbstractModule {
	private static final Criteria CRIT =
		ElementCriteria.name("message").add(ElementCriteria.name("x",
			"http://jabber.org/protocol/muc#user").add(ElementCriteria.name("invite")));

	//~--- constructors ---------------------------------------------------------

	/**
	 * Constructs ...
	 *
	 *
	 * @param config
	 * @param writer
	 * @param mucRepository
	 */
	public MediatedInvitationModule(MucConfig config, ElementWriter writer,
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

			if (getNicknameFromJid(
							JID.jidInstance(element.getAttributeStaticStr(Packet.TO_ATT))) != null) {
				throw new MUCException(Authorization.BAD_REQUEST);
			}

			final Room room = repository.getRoom(roomJID);

			if (room == null) {
				throw new MUCException(Authorization.ITEM_NOT_FOUND);
			}

			final String nickName = room.getOccupantsNickname(senderJID);
			final Role senderRole = room.getRole(nickName);

			if (!senderRole.isInviteOtherUsers()) {
				throw new MUCException(Authorization.NOT_ALLOWED);
			}

			final Affiliation senderAffiliation = room.getAffiliation(senderJID.getBareJID());

			if (room.getConfig().isRoomMembersOnly() &&!senderAffiliation.isEditMemberList()) {
				throw new MUCException(Authorization.FORBIDDEN);
			}

			final Element x = element.getElement().getChild("x",
													"http://jabber.org/protocol/muc#user");
			List<Element> ch = x.getChildren();

			for (Element invite : ch) {
				if (!"invite".equals(invite.getName())) {
					continue;
				}

				final Element reason        = invite.getChild("reason");
				final Element cont          = invite.getChild("continue");
				final String recipient      = invite.getAttributeStaticStr(Packet.TO_ATT);
				final Element resultMessage = new Element("message",
																				new String[] { Packet.FROM_ATT,
								Packet.TO_ATT }, new String[] { roomJID.toString(), recipient });
				final Element resultX = new Element("x", new String[] { Packet.XMLNS_ATT },
																	new String[] { "http://jabber.org/protocol/muc#user" });

				resultMessage.addChild(resultX);
				if (room.getConfig().isRoomMembersOnly() &&
						senderAffiliation.isEditMemberList()) {
					room.addAffiliationByJid(JID.jidInstance(recipient).getBareJID(),
																	 Affiliation.member);
				}

				final Element resultInvite = new Element("invite", new String[] { "from" },
																			 new String[] { senderJID.toString() });

				resultX.addChild(resultInvite);
				if (room.getConfig().isPasswordProtectedRoom()) {
					resultX.addChild(new Element("password", room.getConfig().getPassword()));
				}
				if (reason != null) {
					resultInvite.addChild(reason.clone());
				}
				if (cont != null) {
					resultInvite.addChild(cont.clone());
				}
				writer.write(Packet.packetInstance(resultMessage));
			}
		} catch (MUCException e1) {
			throw e1;
		} catch (Exception e) {
			e.printStackTrace();

			throw new RuntimeException(e);
		}
	}
}


//~ Formatted in Tigase Code Convention on 13/02/20
