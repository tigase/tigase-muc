/*
 * GroupchatMessageModule.java
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

import tigase.component.ElementWriter;
import tigase.criteria.Criteria;
import tigase.criteria.ElementCriteria;
import tigase.muc.Affiliation;
import tigase.muc.DateUtil;
import tigase.muc.MucConfig;
import tigase.muc.Role;
import tigase.muc.Room;
import tigase.muc.exceptions.MUCException;
import tigase.muc.history.HistoryProvider;
import tigase.muc.logger.MucLogger;
import tigase.muc.repository.IMucRepository;
import tigase.server.Packet;
import tigase.util.TigaseStringprepException;
import tigase.xml.Element;
import tigase.xmpp.Authorization;
import tigase.xmpp.BareJID;
import tigase.xmpp.JID;

//~--- JDK imports ------------------------------------------------------------

/**
 * @author bmalkow
 * 
 */
public class GroupchatMessageModule extends AbstractModule {
	private static final Criteria CRIT = ElementCriteria.nameType("message", "groupchat");
	private static final Criteria CRIT_CHAT_STAT = ElementCriteria.xmlns("http://jabber.org/protocol/chatstates");

	// ~--- fields
	// ---------------------------------------------------------------

	private final Set<Criteria> allowedElements = new HashSet<Criteria>();
	private boolean filterEnabled = true;
	private final HistoryProvider historyProvider;
	private final MucLogger mucLogger;

	// ~--- constructors
	// ---------------------------------------------------------

	/**
	 * Constructs ...
	 * 
	 * 
	 * @param config
	 * @param writer
	 * @param mucRepository
	 * @param historyProvider
	 * @param mucLogger
	 */
	public GroupchatMessageModule(MucConfig config, ElementWriter writer, IMucRepository mucRepository,
			HistoryProvider historyProvider, MucLogger mucLogger) {
		super(config, writer, mucRepository);
		this.historyProvider = historyProvider;
		this.mucLogger = mucLogger;
		this.filterEnabled = config.isMessageFilterEnabled();
		if (log.isLoggable(Level.CONFIG)) {
			log.config("Filtering message children is " + (filterEnabled ? "enabled" : "disabled"));
		}
	}

	// ~--- methods
	// --------------------------------------------------------------

	/**
	 * @param room
	 * @param cData
	 * @param senderJID
	 * @param nickName
	 * @param sendDate
	 */
	private void addMessageToHistory(Room room, final Element message, String body, JID senderJid, String senderNickname,
			Date time) {
		if (historyProvider != null) {
			historyProvider.addMessage(room, filterEnabled ? null : message, body, senderJid, senderNickname, time);
		}
		if ((mucLogger != null) && room.getConfig().isLoggingEnabled()) {
			mucLogger.addMessage(room, body, senderJid, senderNickname, time);
		}
	}

	/**
	 * @param room
	 * @param cData
	 * @param senderJID
	 * @param nickName
	 * @param sendDate
	 */
	private void addSubjectChangeToHistory(Room room, Element message, final String subject, JID senderJid,
			String senderNickname, Date time) {
		if (historyProvider != null) {
			historyProvider.addSubjectChange(room, filterEnabled ? null : message, subject, senderJid, senderNickname, time);
		}
		if ((mucLogger != null) && room.getConfig().isLoggingEnabled()) {
			mucLogger.addSubjectChange(room, subject, senderJid, senderNickname, time);
		}
	}

	// ~--- get methods
	// ----------------------------------------------------------

	/**
	 * Method description
	 * 
	 * 
	 * @return
	 */
	@Override
	public String[] getFeatures() {
		ArrayList<String> f = new ArrayList<String>();

		if (isChatStateAllowed()) {
			f.add("http://jabber.org/protocol/chatstates");
		}

		return f.toArray(new String[] {});
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

	/**
	 * Method description
	 * 
	 * 
	 * @return
	 */
	public boolean isChatStateAllowed() {
		return allowedElements.contains(CRIT_CHAT_STAT);
	}

	// ~--- methods
	// --------------------------------------------------------------

	/**
	 * Method description
	 * 
	 * 
	 * @param packet
	 * 
	 * @throws MUCException
	 */
	@Override
	public void process(Packet packet) throws MUCException {
		try {
			final JID senderJID = JID.jidInstance(packet.getAttributeStaticStr(Packet.FROM_ATT));
			final BareJID roomJID = BareJID.bareJIDInstance(packet.getAttributeStaticStr(Packet.TO_ATT));

			if (getNicknameFromJid(JID.jidInstance(packet.getAttributeStaticStr(Packet.TO_ATT))) != null) {
				throw new MUCException(Authorization.BAD_REQUEST);
			}

			final Room room = repository.getRoom(roomJID);

			if (room == null) {
				throw new MUCException(Authorization.ITEM_NOT_FOUND);
			}

			final String nickName = room.getOccupantsNickname(senderJID);
			final Role role = room.getRole(nickName);
			final Affiliation affiliation = room.getAffiliation(senderJID.getBareJID());

			if (!role.isSendMessagesToAll() || (room.getConfig().isRoomModerated() && (role == Role.visitor))) {
				throw new MUCException(Authorization.FORBIDDEN);
			}

			Element body = null;
			Element subject = null;
			Element delay = null;
			ArrayList<Element> content = new ArrayList<Element>();
			List<Element> ccs = packet.getElement().getChildren();

			if (ccs != null) {
				for (Element c : ccs) {
					if ("delay".equals(c.getName())) {
						delay = c;
					} else if ("body".equals(c.getName())) {
						body = c;
						content.add(c);
					} else if ("subject".equals(c.getName())) {
						subject = c;
						content.add(c);
					} else if (!filterEnabled) {
						content.add(c);
					} else {
						for (Criteria crit : allowedElements) {
							if (crit.match(c)) {
								content.add(c);

								break;
							}
						}
					}
				}
			}

			final JID senderRoomJID = JID.jidInstance(roomJID, nickName);

			if (subject != null) {
				if (!(room.getConfig().isChangeSubject() && (role == Role.participant)) && !role.isModifySubject()) {
					throw new MUCException(Authorization.FORBIDDEN);
				}

				String msg = subject.getCData();

				room.setNewSubject(msg, nickName);
			}

			Date sendDate;

			if ((delay != null) && (affiliation == Affiliation.owner)) {
				sendDate = DateUtil.parse(delay.getAttributeStaticStr("stamp"));
			} else {
				sendDate = new Date();
			}
			if (body != null) {
				addMessageToHistory(room, packet.getElement(), body.getCData(), senderJID, nickName, sendDate);
			}
			if (subject != null) {
				addSubjectChangeToHistory(room, packet.getElement(), subject.getCData(), senderJID, nickName, sendDate);
			}
			sendMessagesToAllOccupants(room, senderRoomJID, content.toArray(new Element[] {}));
		} catch (MUCException e1) {
			throw e1;
		} catch (TigaseStringprepException e) {
			throw new MUCException(Authorization.BAD_REQUEST);
		} catch (Exception e) {
			e.printStackTrace();

			throw new RuntimeException(e);
		}
	}

	/**
	 * Method description
	 * 
	 * 
	 * @param room
	 * @param fromJID
	 * @param content
	 * 
	 * @throws TigaseStringprepException
	 */
	public void sendMessagesToAllOccupants(final Room room, final JID fromJID, final Element... content)
			throws TigaseStringprepException {
		for (String nickname : room.getOccupantsNicknames()) {
			final Role role = room.getRole(nickname);

			if (!role.isReceiveMessages()) {
				continue;
			}

			final Collection<JID> occupantJids = room.getOccupantsJidsByNickname(nickname);

			for (JID jid : occupantJids) {

				Packet message = Packet.packetInstance(new Element("message", new String[] { "type", "from", "to" },
						new String[] { "groupchat", fromJID.toString(), jid.toString() }));
				message.setXMLNS(Packet.CLIENT_XMLNS);

				// Packet message = Message.getMessage(fromJID, jid,
				// StanzaType.groupchat, null, null, null, null);

				if (content != null) {
					for (Element sub : content) {
						if (sub != null) {
							message.getElement().addChild(sub);
						}
					}
				}
				writer.write(message);
			}
		}
	}

	// ~--- set methods
	// ----------------------------------------------------------

	/**
	 * Method description
	 * 
	 * 
	 * @param allowed
	 */
	public void setChatStateAllowed(Boolean allowed) {
		if ((allowed != null) && allowed) {
			if (log.isLoggable(Level.CONFIG)) {
				log.config("Chat state allowed");
			}
			allowedElements.add(CRIT_CHAT_STAT);
		} else {
			if (log.isLoggable(Level.CONFIG)) {
				log.config("Chat state disallowed");
			}
			allowedElements.remove(CRIT_CHAT_STAT);
		}
	}
}

// ~ Formatted in Tigase Code Convention on 13/02/20
