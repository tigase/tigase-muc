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

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import tigase.criteria.Criteria;
import tigase.criteria.ElementCriteria;
import tigase.muc.ElementWriter;
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

/**
 * @author bmalkow
 * 
 */
public class GroupchatMessageModule extends AbstractModule {

	private static final Criteria CRIT = ElementCriteria.nameType("message", "groupchat");

	private static final Criteria CRIT_CHAT_STAT = ElementCriteria.xmlns("http://jabber.org/protocol/chatstates");

	private final Set<Criteria> allowedElements = new HashSet<Criteria>();

	private final HistoryProvider historyProvider;

	private final MucLogger mucLogger;

	public GroupchatMessageModule(MucConfig config, ElementWriter writer, IMucRepository mucRepository,
			HistoryProvider historyProvider, MucLogger mucLogger) {
		super(config, writer, mucRepository);
		this.historyProvider = historyProvider;
		this.mucLogger = mucLogger;
	}

	/**
	 * @param room
	 * @param cData
	 * @param senderJID
	 * @param nickName
	 * @param sendDate
	 */
	private void addMessageToHistory(Room room, final String message, JID senderJid, String senderNickname, Date time) {
		historyProvider.addMessage(room, message, senderJid, senderNickname, time);

		if (mucLogger != null && room.getConfig().isLoggingEnabled()) {
			mucLogger.addMessage(room, message, senderJid, senderNickname, time);
		}
	}

	/**
	 * @param room
	 * @param cData
	 * @param senderJID
	 * @param nickName
	 * @param sendDate
	 */
	private void addSubjectChangeToHistory(Room room, final String subject, JID senderJid, String senderNickname, Date time) {
		historyProvider.addSubjectChange(room, subject, senderJid, senderNickname, time);

		if (mucLogger != null && room.getConfig().isLoggingEnabled()) {
			mucLogger.addSubjectChange(room, subject, senderJid, senderNickname, time);
		}
	}

	@Override
	public String[] getFeatures() {
		ArrayList<String> f = new ArrayList<String>();
		if (isChatStateAllowed()) {
			f.add("http://jabber.org/protocol/chatstates");
		}
		return f.toArray(new String[] {});
	}

	@Override
	public Criteria getModuleCriteria() {
		return CRIT;
	}

	public boolean isChatStateAllowed() {
		return allowedElements.contains(CRIT_CHAT_STAT);
	}

	@Override
	public void process(Packet element) throws MUCException {
		try {
			final JID senderJID = JID.jidInstance(element.getAttribute("from"));
			final BareJID roomJID = BareJID.bareJIDInstance(element.getAttribute("to"));

			if (getNicknameFromJid(JID.jidInstance(element.getAttribute("to"))) != null) {
				throw new MUCException(Authorization.BAD_REQUEST);
			}

			final Room room = repository.getRoom(roomJID);
			if (room == null) {
				throw new MUCException(Authorization.ITEM_NOT_FOUND);
			}

			Role role = room.getRoleByJid(senderJID);
			if (!role.isSendMessagesToAll() || (room.getConfig().isRoomModerated() && role == Role.visitor)) {
				throw new MUCException(Authorization.FORBIDDEN);
			}

			Element body = null;
			Element subject = null;
			ArrayList<Element> content = new ArrayList<Element>();

			for (Element c : element.getElement().getChildren()) {
				if ("body".equals(c.getName())) {
					body = c;
					content.add(c);
				} else if ("subject".equals(c.getName())) {
					subject = c;
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

			final String nickName = room.getOccupantsNickname(senderJID);
			final JID senderRoomJID = JID.jidInstance(roomJID, nickName);

			if (subject != null) {
				if (!(room.getConfig().isChangeSubject() && role == Role.participant) && !role.isModifySubject())
					throw new MUCException(Authorization.FORBIDDEN);
				String msg = subject.getCData();
				room.setNewSubject(msg, nickName);
			}

			Date sendDate = new Date();

			if (body != null)
				addMessageToHistory(room, body.getCData(), senderJID, nickName, sendDate);
			if (subject != null) {
				addSubjectChangeToHistory(room, subject.getCData(), senderJID, nickName, sendDate);
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

	public void sendMessagesToAllOccupants(final Room room, final JID fromJID, final Element... content)
			throws TigaseStringprepException {
		for (JID occupantsJID : room.getOccupantsJids()) {
			Role role = room.getRoleByJid(occupantsJID);
			if (!role.isReceiveMessages())
				continue;
			Element message = new Element("message", new String[] { "type", "from", "to" }, new String[] { "groupchat",
					fromJID.toString(), occupantsJID.toString() });
			if (content != null) {
				for (Element sub : content) {
					if (sub != null)
						message.addChild(sub);
				}
			}
			writer.write(Packet.packetInstance(message));
		}
	}

	public void setChatStateAllowed(Boolean allowed) {
		if (allowed != null && allowed) {
			log.config("Chat state allowed");
			allowedElements.add(CRIT_CHAT_STAT);
		} else {
			log.config("Chat state disallowed");
			allowedElements.remove(CRIT_CHAT_STAT);
		}
	}
}
