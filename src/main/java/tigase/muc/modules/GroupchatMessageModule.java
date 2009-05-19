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
import java.util.List;
import java.util.Set;

import tigase.criteria.Criteria;
import tigase.criteria.ElementCriteria;
import tigase.muc.IChatRoomLogger;
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
public class GroupchatMessageModule extends AbstractModule {

	private static final Criteria CRIT = ElementCriteria.nameType("message", "groupchat");

	private static final Criteria CRIT_CHAT_STAT = ElementCriteria.xmlns("http://jabber.org/protocol/chatstates");

	private final Set<Criteria> allowedElements = new HashSet<Criteria>();
	private final IChatRoomLogger chatLogger;

	public GroupchatMessageModule(MucConfig config, IMucRepository mucRepository, IChatRoomLogger chatRoomLogger) {
		super(config, mucRepository);
		this.chatLogger = chatRoomLogger;
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
	public List<Element> process(Element element) throws MUCException {
		try {
			final ArrayList<Element> result = new ArrayList<Element>();
			final String senderJid = element.getAttribute("from");
			final String roomId = getRoomId(element.getAttribute("to"));

			if (getNicknameFromJid(element.getAttribute("to")) != null) {
				throw new MUCException(Authorization.BAD_REQUEST);
			}

			final Room room = repository.getRoom(roomId);
			if (room == null) {
				throw new MUCException(Authorization.ITEM_NOT_FOUND);
			}

			Role role = room.getRoleByJid(senderJid);
			if (!role.isSendMessagesToAll() || (room.getConfig().isRoomModerated() && role == Role.visitor)) {
				throw new MUCException(Authorization.FORBIDDEN);
			}

			Element body = null;
			Element subject = null;
			ArrayList<Element> content = new ArrayList<Element>();

			for (Element c : element.getChildren()) {
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

			final String nickName = room.getOccupantsNickname(senderJid);
			final String senderRoomJid = roomId + "/" + nickName;

			if (subject != null) {
				if (!(room.getConfig().isChangeSubject() && role == Role.participant) && !role.isModifySubject())
					throw new MUCException(Authorization.FORBIDDEN);
				String msg = subject.getCData();
				room.setNewSubject(msg, nickName);
			}

			Date sendDate = new Date();

			if (body != null)
				room.addToHistory(body.getCData(), senderJid, nickName, sendDate);
			if (room.getConfig().isLoggingEnabled()) {
				if (this.chatLogger != null && body != null) {
					chatLogger.addMessage(room.getConfig().getLoggingFormat(), room.getRoomId(), sendDate, nickName,
							body.getCData());
				} else if (this.chatLogger != null && subject != null) {
					chatLogger.addSubject(room.getConfig().getLoggingFormat(), room.getRoomId(), sendDate, nickName,
							subject.getCData());

				}
			}
			result.addAll(sendMessagesToAllOccupants(room, senderRoomJid, content.toArray(new Element[] {})));

			return result;
		} catch (MUCException e1) {
			throw e1;
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}

	public List<Element> sendMessagesToAllOccupants(final Room room, final String fromJid, final Element... content) {
		final ArrayList<Element> result = new ArrayList<Element>();
		for (String occupantsJid : room.getOccupantsJids()) {
			Role role = room.getRoleByJid(occupantsJid);
			if (!role.isReceiveMessages())
				continue;
			Element message = new Element("message", new String[] { "type", "from", "to" }, new String[] { "groupchat",
					fromJid, occupantsJid });
			if (content != null) {
				for (Element sub : content) {
					if (sub != null)
						message.addChild(sub);
				}
			}
			result.add(message);
		}
		return result;
	}

	public void setChatStateAllowed(Boolean allowed) {
		System.out.println("xxx " + allowed);
		if (allowed != null && allowed) {
			log.config("Chat state allowed");
			allowedElements.add(CRIT_CHAT_STAT);
		} else {
			log.config("Chat state disallowed");
			allowedElements.remove(CRIT_CHAT_STAT);
		}
	}
}
