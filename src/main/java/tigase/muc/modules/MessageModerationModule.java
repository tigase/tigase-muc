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

import tigase.component.exceptions.ComponentException;
import tigase.component.exceptions.RepositoryException;
import tigase.criteria.Criteria;
import tigase.criteria.ElementCriteria;
import tigase.kernel.beans.Bean;
import tigase.kernel.beans.Inject;
import tigase.muc.MUCComponent;
import tigase.muc.Role;
import tigase.muc.Room;
import tigase.muc.exceptions.MUCException;
import tigase.muc.history.ExtendedMAMRepository;
import tigase.muc.repository.IMucRepository;
import tigase.server.Packet;
import tigase.util.datetime.TimestampHelper;
import tigase.util.stringprep.TigaseStringprepException;
import tigase.xml.Element;
import tigase.xmpp.Authorization;
import tigase.xmpp.StanzaType;
import tigase.xmpp.jid.BareJID;
import tigase.xmpp.jid.JID;
import tigase.xmpp.mam.MAMRepository;

import java.util.Collections;
import java.util.Date;
import java.util.UUID;

@Bean(name = MessageModerationModule.ID, parent = MUCComponent.class,  active = true)
public class MessageModerationModule extends AbstractMucModule {

	public static final String XMLNS = "urn:xmpp:message-moderate:0";

	public static final String ID = XMLNS;

	private static final Criteria CRIT = ElementCriteria.name("iq")
			.add(ElementCriteria.name("apply-to", "urn:xmpp:fasten:0"))
			.add(ElementCriteria.name("moderate", XMLNS));

	private static final String[] FEATURES = new String[] {
			XMLNS
	};

	@Inject
	private IMucRepository mucRepository;
	@Inject
	private ExtendedMAMRepository mamRepository;
	@Inject
	private GroupchatMessageModule groupchatMessageModule;
	private final TimestampHelper timestampHelper = new TimestampHelper();

	@Override
	public String[] getFeatures() {
		return FEATURES;
	}

	@Override
	public Criteria getModuleCriteria() {
		return CRIT;
	}

	@Override
	public void process(Packet packet) throws ComponentException, TigaseStringprepException {
		try {
			if (packet.getType() != StanzaType.set) {
				throw new MUCException(Authorization.BAD_REQUEST, "Invalid packet type.");
			}

			Room room = getRoom(packet.getStanzaTo().getBareJID());
			String nickname = room.getOccupantsNickname(packet.getStanzaFrom());
			if (nickname == null) {
				throw new MUCException(Authorization.FORBIDDEN, "You need to be a room occupant");
			}
			if (room.getRole(nickname) != Role.moderator) {
				throw new MUCException(Authorization.FORBIDDEN, "You do not allowed to moderate this room.");
			}

			Element applyTo = packet.getElemChild("apply-to", "urn:xmpp:fasten:0");
			String id = applyTo.getAttributeStaticStr("id");
			if (id == null) {
				throw new MUCException(Authorization.BAD_REQUEST, "Missing stanza id to apply to");
			}
			
			Element moderate = applyTo.getChildStaticStr("moderate", XMLNS);
			Element retract = moderate.getChildStaticStr("retract", "urn:xmpp:message-retract:0");
			if (retract == null) {
				throw new MUCException(Authorization.BAD_REQUEST, "Unknown operation.");
			}
			String reason = moderate.getChildCData(el -> "reason".equals(el.getName()));

			MAMRepository.Item item = mamRepository.getItem(room.getRoomJID(), id);
			if (item == null) {
				throw new MUCException(Authorization.ITEM_NOT_FOUND, "No message with " + id + " to moderate");
			}

			// replacing original message with tombstone
			Element message = item.getMessage();
			if (message == null) {
				throw new MUCException(Authorization.UNEXPECTED_REQUEST, "Missing message in the archive");
			}

			Element originalOccupantId = message.findChild(el -> el.getName() == "occupant-id" && el.getXMLNS() == "urn:xmpp:occupant-id:0");
			message.setChildren(Collections.emptyList());
			if (originalOccupantId != null) {
				message.addChild(originalOccupantId);
			}
			message.withElement("moderated", XMLNS, moderated -> {
				moderated.setAttribute("by", JID.jidInstanceNS(room.getRoomJID(), nickname).toString());
				moderated.withElement("retracted", "urn:xmpp:message-retract:0",
									  retracted -> retracted.setAttribute("stamp", timestampHelper.format(new Date())));
				if (reason != null) {
					moderated.addChild(new Element("reason", reason));
				}
			});
			mamRepository.updateMessage(room.getRoomJID(), id, message, null);

			// storing locally and sending moderated notification
			Element moderationMessage = new Element("message", new String[]{"id", "type"},
													new String[]{UUID.randomUUID().toString(), "groupchat"});
			moderationMessage.withElement("apply-to", "urn:xmpp:fasten:0", applyToResult -> {
				applyToResult.withAttribute("id", id).withElement("moderated", XMLNS, moderated -> {
					moderated.setAttribute("by", JID.jidInstanceNS(room.getRoomJID(), nickname).toString());
					moderated.addChild(retract.clone());
					if (reason != null) {
						moderated.addChild(new Element("reason", reason));
					}
				});
			});

			String moderationMsgStableId = UUID.randomUUID().toString();
			groupchatMessageModule.addMessageToHistory(room, moderationMessage, null, packet.getStanzaFrom(), null,
													   new Date(), moderationMsgStableId);

			moderationMessage.addChild(new Element("stanza-id", new String[]{"xmlns", "id", "by"},
												   new String[]{"urn:xmpp:sid:0", moderationMsgStableId,
																room.getRoomJID().toString()}));

			groupchatMessageModule.sendMessagesToAllOccupants(room, JID.jidInstance(room.getRoomJID()), Packet.packetInstance(moderationMessage));

			write(packet.okResult((Element) null, 0));
		} catch (RepositoryException ex) {
			throw new RuntimeException(ex);
		}
	}

	protected Room getRoom(BareJID roomJID) throws MUCException, RepositoryException {
		Room room = mucRepository.getRoom(roomJID);
		if (room == null) {
			throw new MUCException(Authorization.ITEM_NOT_FOUND, "Room does not exist.");
		}
		return room;
	}
}
