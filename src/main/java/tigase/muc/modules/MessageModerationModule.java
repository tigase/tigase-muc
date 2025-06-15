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

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import tigase.component.exceptions.ComponentException;
import tigase.component.exceptions.RepositoryException;
import tigase.criteria.Criteria;
import tigase.criteria.ElementCriteria;
import tigase.criteria.Or;
import tigase.kernel.beans.Bean;
import tigase.kernel.beans.Inject;
import tigase.kernel.beans.config.ConfigField;
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
import tigase.xmpp.jid.BareJID;
import tigase.xmpp.jid.JID;
import tigase.xmpp.mam.MAMRepository;

import java.util.Collections;
import java.util.Date;
import java.util.UUID;

@Bean(name = MessageModerationModule.ID, parent = MUCComponent.class,  active = true)
public class MessageModerationModule extends AbstractMucModule {

	public static final String XMLNS_0 = "urn:xmpp:message-moderate:0";
	public static final String XMLNS_1 = "urn:xmpp:message-moderate:1";
	public static final String RETRACT_XMLNS_1 = "urn:xmpp:message-retract:1";

	public static final String ID = XMLNS_1;

	private static final Criteria CRIT = ElementCriteria.nameType("iq", "set")
			.add(ElementCriteria.name("moderate", XMLNS_1))
			.add(ElementCriteria.name("retract", RETRACT_XMLNS_1));
	private static final Criteria CRIT_FALLBACK = ElementCriteria.nameType("iq", "set")
			.add(ElementCriteria.name("apply-to", "urn:xmpp:fasten:0"))
			.add(ElementCriteria.name("moderate", XMLNS_0));
	private static final Criteria CRIT_WITH_FALLBACK = new Or(CRIT, CRIT_FALLBACK);

	private static final String[] FEATURES = new String[] {XMLNS_1
	};

	private static final String[] FEATURES_WITH_FALLBACK = new String[] {XMLNS_1, XMLNS_0
	};

	@ConfigField(desc = "Enable fallback")
	private boolean enableFallback = true;

	@Inject
	private IMucRepository mucRepository;
	@Inject
	private ExtendedMAMRepository mamRepository;
	@Inject
	private GroupchatMessageModule groupchatMessageModule;
	private final TimestampHelper timestampHelper = new TimestampHelper();

	@Override
	public String[] getFeatures() {
		return enableFallback ? FEATURES_WITH_FALLBACK : FEATURES;
	}

	@Override
	public Criteria getModuleCriteria() {
		return enableFallback ? CRIT_WITH_FALLBACK : CRIT;
	}

	@Override
	public void process(Packet packet) throws ComponentException, TigaseStringprepException {
		try {
			Element moderateEl = packet.getElemChild("moderate", XMLNS_1);
			if (moderateEl != null) {
				String id = moderateEl.getAttributeStaticStr("id");
				if (id == null) {
					throw new MUCException(Authorization.BAD_REQUEST, "Missing stanza id");
				}

				String reason = moderateEl.getChildCData(el -> "reason".equals(el.getName()));

				moderate(packet.getStanzaTo().getBareJID(), packet.getStanzaFrom(), id, reason);
				write(packet.okResult((Element) null, 0));
			} else {
				Element applyTo = packet.getElemChild("apply-to", "urn:xmpp:fasten:0");
				String id = applyTo.getAttributeStaticStr("id");
				if (id == null) {
					throw new MUCException(Authorization.BAD_REQUEST, "Missing stanza id to apply to");
				}

				Element moderate = applyTo.getChildStaticStr("moderate", XMLNS_0);
				Element retract = moderate.getChildStaticStr("retract", "urn:xmpp:message-retract:0");
				if (retract == null) {
					throw new MUCException(Authorization.BAD_REQUEST, "Unknown operation.");
				}
				String reason = moderate.getChildCData(el -> "reason".equals(el.getName()));

				moderate(packet.getStanzaTo().getBareJID(), packet.getStanzaFrom(), id, reason);
			}
			write(packet.okResult((Element) null, 0));
		} catch (RepositoryException ex) {
			throw new RuntimeException(ex);
		}
	}
	
	private void moderate(@NonNull BareJID roomJID, @NonNull JID senderJID, @NonNull String messageId, @Nullable String reason)
			throws RepositoryException, MUCException, TigaseStringprepException {
		Room room = getRoom(roomJID);
		String nickname = room.getOccupantsNickname(senderJID);
		if (nickname == null) {
			throw new MUCException(Authorization.FORBIDDEN, "You need to be a room occupant");
		}
		if (room.getRole(nickname) != Role.moderator) {
			throw new MUCException(Authorization.FORBIDDEN, "You do not allowed to moderate this room.");
		}
		
		MAMRepository.Item item = mamRepository.getItem(room.getRoomJID(), messageId);
		if (item == null) {
			throw new MUCException(Authorization.ITEM_NOT_FOUND, "No message with " + messageId + " to moderate");
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

		message.withElement("retracted", RETRACT_XMLNS_1, retractedEl -> {
			retractedEl.withAttribute("stamp", timestampHelper.format(new Date()));
			retractedEl.withElement("moderated", XMLNS_1, moderatedEl -> {
				moderatedEl.addAttribute("by", senderJID.getBareJID().toString());
				moderatedEl.withElement("occupant-id", "urn:xmpp:occupant-id:0", occupantIdEl -> {
					occupantIdEl.withAttribute("id", room.generateOccupantId(senderJID.getBareJID()));
				});
			});
			if (reason != null) {
				retractedEl.addChild(new Element("reason", reason));
			}
		});

		if (enableFallback) {
			message.withElement("moderated", XMLNS_0, moderated -> {
				moderated.setAttribute("by", JID.jidInstanceNS(room.getRoomJID(), nickname).toString());
				moderated.withElement("retracted", "urn:xmpp:message-retract:0",
									  retracted -> retracted.setAttribute("stamp", timestampHelper.format(new Date())));
				if (reason != null) {
					moderated.addChild(new Element("reason", reason));
				}
			});
		}

		mamRepository.updateMessage(room.getRoomJID(), messageId, message, null);

		Element moderationMessage = new Element("message", new String[]{"id", "type"},
												new String[]{UUID.randomUUID().toString(), "groupchat"});
		moderationMessage.withElement("retract", RETRACT_XMLNS_1, retract -> {
			retract.setAttribute("id", messageId);
			retract.withElement("moderated", XMLNS_1, moderatedEl -> {
				moderatedEl.addAttribute("by", JID.jidInstanceNS(roomJID, nickname).toString());
				moderatedEl.withElement("occupant-id", "urn:xmpp:occupant-id:0", occupantIdEl -> {
					occupantIdEl.withAttribute("id", room.generateOccupantId(senderJID.getBareJID()));
				});
			});
			if (reason != null) {
				retract.addChild(new Element("reason", reason));
			}
		});

		if (enableFallback) {
			moderationMessage.withElement("apply-to", "urn:xmpp:fasten:0", applyToResult -> {
				applyToResult.withAttribute("id", messageId).withElement("moderated", XMLNS_0, moderated -> {
					moderated.setAttribute("by", JID.jidInstanceNS(room.getRoomJID(), nickname).toString());
					moderated.withElement("retract", "urn:xmpp:message-retract:0", (String) null);
					if (reason != null) {
						moderated.addChild(new Element("reason", reason));
					}
				});
			});
		}

		groupchatMessageModule.sendMessagesToAllOccupants(room, JID.jidInstance(room.getRoomJID()), Packet.packetInstance(moderationMessage));
	}

	protected @NonNull Room getRoom(BareJID roomJID) throws MUCException, RepositoryException {
		Room room = mucRepository.getRoom(roomJID);
		if (room == null) {
			throw new MUCException(Authorization.ITEM_NOT_FOUND, "Room does not exist.");
		}
		return room;
	}
}
