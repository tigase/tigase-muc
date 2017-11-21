/*
 * MediatedInvitationModule.java
 *
 * Tigase Jabber/XMPP Multi-User Chat Component
 * Copyright (C) 2004-2017 "Tigase, Inc." <office@tigase.com>
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

import tigase.component.exceptions.RepositoryException;
import tigase.criteria.Criteria;
import tigase.criteria.ElementCriteria;
import tigase.criteria.Or;
import tigase.kernel.beans.Bean;
import tigase.kernel.beans.Inject;
import tigase.muc.Affiliation;
import tigase.muc.Role;
import tigase.muc.Room;
import tigase.muc.exceptions.MUCException;
import tigase.muc.repository.IMucRepository;
import tigase.server.Packet;
import tigase.util.stringprep.TigaseStringprepException;
import tigase.xml.Element;
import tigase.xmpp.Authorization;
import tigase.xmpp.StanzaType;
import tigase.xmpp.jid.BareJID;
import tigase.xmpp.jid.JID;

import java.util.List;
import java.util.logging.Level;

/**
 * @author bmalkow
 */
@Bean(name = MediatedInvitationModule.ID, active = true)
public class MediatedInvitationModule
		extends AbstractMucModule {

	public static final String ID = "invitations";
	private static final Criteria CRIT = ElementCriteria.name("message")
			.add(ElementCriteria.name("x", "http://jabber.org/protocol/muc#user")
						 .add(new Or(ElementCriteria.name("invite"), ElementCriteria.name("decline"))));
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

			if (getNicknameFromJid(JID.jidInstance(element.getAttributeStaticStr(Packet.TO_ATT))) != null) {
				throw new MUCException(Authorization.BAD_REQUEST);
			}

			final Room room = repository.getRoom(roomJID);
			if (room == null) {
				throw new MUCException(Authorization.ITEM_NOT_FOUND,
									   "Room " + roomJID + " does not exists on this server.");
			}

			final String nickName = room.getOccupantsNickname(senderJID);
			final Role senderRole = room.getRole(nickName);
			final Affiliation senderAffiliation = room.getAffiliation(senderJID.getBareJID());

			final Element x = element.getElement().getChild("x", "http://jabber.org/protocol/muc#user");

			List<Element> ch = x.getChildren();
			for (Element child : ch) {
				if (element.getType() == StanzaType.error && "invite".equals(child.getName())) {
					processInvitationErrorResponse(child, element.getErrorCondition(), roomJID, senderJID);
				} else if ("invite".equals(child.getName()) && element.getType() != StanzaType.error) {
					doInvite(element, child, room, roomJID, senderJID, senderRole, senderAffiliation);
				} else if ("decline".equals(child.getName()) && element.getType() != StanzaType.error) {
					doDecline(child, roomJID, senderJID);
				}
			}
		} catch (MUCException e1) {
			throw e1;
		} catch (Exception e) {
			log.log(Level.FINEST, "Error during processing invitation", e);
			throw new RuntimeException(e);
		}
	}

	private void doDecline(Element decline, BareJID roomJID, JID senderJID) throws TigaseStringprepException {
		final Element reason = decline.getChild("reason");
		final JID recipient = JID.jidInstance(decline.getAttributeStaticStr(Packet.TO_ATT));

		Packet resultMessage = Packet.packetInstance(
				new Element("message", new String[]{Packet.FROM_ATT, Packet.TO_ATT},
							new String[]{roomJID.toString(), recipient.toString()}));
		resultMessage.setXMLNS(Packet.CLIENT_XMLNS);

		final Element resultX = new Element("x", new String[]{Packet.XMLNS_ATT},
											new String[]{"http://jabber.org/protocol/muc#user"});
		resultMessage.getElement().addChild(resultX);
		final Element resultDecline = new Element("decline", new String[]{"from"}, new String[]{senderJID.toString()});
		resultX.addChild(resultDecline);
		if (reason != null) {
			resultDecline.addChild(reason.clone());
		}
		write(resultMessage);
	}

	private void doInvite(Packet message, Element invite, Room room, BareJID roomJID, JID senderJID, Role senderRole,
						  Affiliation senderAffiliation)
			throws RepositoryException, TigaseStringprepException, MUCException {

		if (room == null) {
			throw new MUCException(Authorization.ITEM_NOT_FOUND);
		}

		if (!room.getConfig().isInvitingAllowed() && senderAffiliation.lowerThan(Affiliation.admin)) {
			throw new MUCException(Authorization.FORBIDDEN, "Occupants are not allowed to invite others");
		}

		if (!senderRole.isInviteOtherUsers()) {
			throw new MUCException(Authorization.FORBIDDEN,
								   "Your role is '" + senderRole + "'. You cannot invite others.");
		}

		final Element reason = invite.getChild("reason");
		final Element cont = invite.getChild("continue");
		final JID recipient = JID.jidInstance(invite.getAttributeStaticStr(Packet.TO_ATT));

		Packet resultMessage = Packet.packetInstance(
				new Element("message", new String[]{Packet.FROM_ATT, Packet.TO_ATT},
							new String[]{roomJID.toString(), recipient.toString()}));
		resultMessage.setXMLNS(Packet.CLIENT_XMLNS);

		final String id = message.getAttributeStaticStr("id");
		if (id != null) {
			resultMessage.getElement().addAttribute("id", id);
		}

		final Element resultX = new Element("x", new String[]{Packet.XMLNS_ATT},
											new String[]{"http://jabber.org/protocol/muc#user"});

		resultMessage.getElement().addChild(resultX);
		if (room.getConfig().isRoomMembersOnly() &&
				(senderAffiliation.isEditMemberList() || room.getConfig().isInvitingAllowed())) {
			room.addAffiliationByJid(recipient.getBareJID(), Affiliation.member);
		}

		final Element resultInvite = new Element("invite", new String[]{"from"}, new String[]{senderJID.toString()});

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

		Element bdy = message.getElement().getChild("body");
		if (bdy != null) {
			resultMessage.getElement().addChild(bdy.clone());
		}

		write(resultMessage);
	}

	private void processInvitationErrorResponse(Element invite, String errorCondition, BareJID roomJID, JID senderJID)
			throws TigaseStringprepException {
		final JID recipient = JID.jidInstance(invite.getAttributeStaticStr(Packet.FROM_ATT));

		Packet resultMessage = Packet.packetInstance(
				new Element("message", new String[]{Packet.FROM_ATT, Packet.TO_ATT},
							new String[]{roomJID.toString(), recipient.toString()}));
		resultMessage.setXMLNS(Packet.CLIENT_XMLNS);

		final Element resultX = new Element("x", new String[]{Packet.XMLNS_ATT},
											new String[]{"http://jabber.org/protocol/muc#user"});
		resultMessage.getElement().addChild(resultX);
		final Element resultDecline = new Element("decline", new String[]{"from"}, new String[]{senderJID.toString()});
		resultX.addChild(resultDecline);

		Element reason = new Element("reason", "Your invitation is returned with error" +
				(errorCondition == null ? "." : (": " + errorCondition)));

		resultDecline.addChild(reason);
		write(resultMessage);
	}
}