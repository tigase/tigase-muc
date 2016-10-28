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

import tigase.criteria.Criteria;
import tigase.criteria.ElementCriteria;
import tigase.muc.Affiliation;
import tigase.muc.DateUtil;
import tigase.muc.Role;
import tigase.muc.Room;
import tigase.muc.exceptions.MUCException;
import tigase.muc.history.HistoryProvider;
import tigase.muc.logger.MucLogger;
import tigase.server.Packet;
import tigase.util.TigaseStringprepException;
import tigase.xml.Element;
import tigase.xmpp.Authorization;
import tigase.xmpp.BareJID;
import tigase.xmpp.JID;

import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.logging.Level;

/**
 * @author bmalkow
 */
public class GroupchatMessageModule extends AbstractMucModule {

	public static final String ID = "groupchat";
	private static final Criteria CRIT = ElementCriteria.nameType("message", "groupchat");
	private static final Criteria CRIT_CHAT_STAT = ElementCriteria.xmlns("http://jabber.org/protocol/chatstates");
	private static final Charset UTF8 = Charset.forName("UTF-8");
	private final Set<Criteria> allowedElements = new HashSet<Criteria>();


	protected void addMessageToHistory(Room room, final Element message, String body, JID senderJid, String senderNickname,
									   Date time) {
		try {
			HistoryProvider historyProvider = context.getHistoryProvider();
			if (historyProvider != null) {
				historyProvider.addMessage(room, message, body, senderJid,
						senderNickname, time);
			}
		} catch (Exception e) {
			if (log.isLoggable(Level.WARNING))
				log.log(Level.WARNING, "Can't add message to history!", e);
		}
		try {
			MucLogger mucLogger = context.getMucLogger();
			if ((mucLogger != null) && room.getConfig().isLoggingEnabled()) {
				mucLogger.addMessage(room, body, senderJid, senderNickname, time);
			}
		} catch (Exception e) {
			if (log.isLoggable(Level.WARNING))
				log.log(Level.WARNING, "Can't add message to log!", e);
		}
	}


	protected void addSubjectChangeToHistory(Room room, Element message, final String subject, JID senderJid,
											 String senderNickname, Date time) {
		try {
			HistoryProvider historyProvider = context.getHistoryProvider();
			if (historyProvider != null) {
				historyProvider.addSubjectChange(room, message, subject, senderJid,
						senderNickname, time);
			}
		} catch (Exception e) {
			if (log.isLoggable(Level.WARNING))
				log.log(Level.WARNING, "Can't add subject change to history!", e);
		}

		try {
			MucLogger mucLogger = context.getMucLogger();
			if ((mucLogger != null) && room.getConfig().isLoggingEnabled()) {
				mucLogger.addSubjectChange(room, subject, senderJid, senderNickname, time);
			}
		} catch (Exception e) {
			if (log.isLoggable(Level.WARNING))
				log.log(Level.WARNING, "Can't add subject change to log!", e);
		}

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see tigase.component.modules.AbstractModule#afterRegistration()
	 */
	@Override
	public void afterRegistration() {
		super.afterRegistration();
	}

	/**
	 * Method description
	 *
	 * @return
	 */
	@Override
	public String[] getFeatures() {
		ArrayList<String> f = new ArrayList<String>();

		f.add("http://jabber.org/protocol/muc");

		if (isChatStateAllowed()) {
			f.add("http://jabber.org/protocol/chatstates");
		}

		return f.toArray(new String[]{});
	}

	/**
	 * Method description
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
	 * @return
	 */
	public boolean isChatStateAllowed() {
		return allowedElements.contains(CRIT_CHAT_STAT);
	}

	protected Packet preparePacket(String messageId, String xmlLang, Element... content) throws TigaseStringprepException {
		Element e = new Element("message", new String[]{"type"}, new String[]{"groupchat"});
		if (messageId != null) {
			e.setAttribute("id", messageId);
		}
		if (xmlLang != null) {
			e.setAttribute("xml:lang", xmlLang);
		}
		if (content != null)
			e.addChildren(Arrays.asList(content));
		Packet message = Packet.packetInstance(e);
		message.setXMLNS(Packet.CLIENT_XMLNS);
		return message;
	}

	/**
	 * Method description
	 *
	 * @param packet
	 * @throws MUCException
	 */
	@Override
	public void process(Packet packet) throws MUCException {
		try {
			final JID senderJID = JID.jidInstance(packet.getAttributeStaticStr(Packet.FROM_ATT));
			final BareJID roomJID = BareJID.bareJIDInstance(packet.getAttributeStaticStr(Packet.TO_ATT));

			if (getNicknameFromJid(JID.jidInstance(packet.getAttributeStaticStr(Packet.TO_ATT))) != null) {
				throw new MUCException(Authorization.BAD_REQUEST, "Groupchat message can't be addressed to occupant.");
			}

			final Room room = context.getMucRepository().getRoom(roomJID);

			if (room == null) {
				throw new MUCException(Authorization.ITEM_NOT_FOUND, "There is no such room.");
			}

			final String nickName = room.getOccupantsNickname(senderJID);
			final Role role = room.getRole(nickName);
			final Affiliation affiliation = room.getAffiliation(senderJID.getBareJID());

			if (log.isLoggable(Level.FINEST)) {
				log.finest("Processing groupchat message. room=" + roomJID + "; senderJID=" + senderJID + "; senderNickname="
						+ nickName + "; role=" + role + "; affiliation=" + affiliation + ";");
			}

			if (!role.isSendMessagesToAll() || (room.getConfig().isRoomModerated() && (role == Role.visitor))) {
				if (log.isLoggable(Level.FINE))
					log.fine("Insufficient privileges to send grouchat message: role=" + role + "; roomModerated="
							+ room.getConfig().isRoomModerated() + "; stanza=" + packet.getElement().toStringNoChildren());
				throw new MUCException(Authorization.FORBIDDEN, "Insufficient privileges to send groupchat message.");
			}

			String xmlLang = packet.getElement().getAttributeStaticStr("xml:lang");
			Element body = null;
			Element subject = null;
			Element delay = null;
			String id = packet.getAttributeStaticStr(Packet.ID_ATT);
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
					} else if (!context.isMessageFilterEnabled()) {
						content.add(c);
					} else if (context.isChatStateAllowed() && CRIT_CHAT_STAT.match(c)) {
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

			Date sendDate;

			if ((delay != null) && (affiliation == Affiliation.owner)) {
				sendDate = DateUtil.parse(delay.getAttributeStaticStr("stamp"));
			} else {
				sendDate = new Date();
			}

			if (subject != null) {
				if (!(room.getConfig().isChangeSubject() && (role == Role.participant)) && !role.isModifySubject()) {
					if (log.isLoggable(Level.FINE))
						log.fine("Insufficient privileges to change subject: role=" + role + "; allowToChangeSubject="
								+ room.getConfig().isChangeSubject() + "; stanza=" + packet.getElement().toStringNoChildren());
					throw new MUCException(Authorization.FORBIDDEN, "Insufficient privileges to change subject.");
				}

				String msg = subject.getCData();

				room.setNewSubject(msg, nickName);
				room.setSubjectChangeDate(sendDate);
			}

			if (id == null && context.isAddMessageIdIfMissing()) {
				if (subject != null) {
					id = generateSubjectId(sendDate, subject == null ? "" : subject.getCData());
				} else {
					id = UUID.randomUUID().toString();
				}
			}


			Packet msg = preparePacket(id, xmlLang, content.toArray(new Element[]{}));

			if (body != null) {
				addMessageToHistory(room, msg.getElement(), body.getCData(), senderJID, nickName, sendDate);
			}
			if (subject != null) {
				addSubjectChangeToHistory(room, msg.getElement(), subject.getCData(), senderJID, nickName, sendDate);
			}

			sendMessagesToAllOccupants(room, senderRoomJID, msg);
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
		Packet msg = preparePacket(null, null, content);
		sendMessagesToAllOccupants(room, fromJID, msg);
	}

	public void sendMessagesToAllOccupants(final Room room, final JID fromJID, String xmlLang, final Element... content)
			throws TigaseStringprepException {
		Packet msg = preparePacket(null, xmlLang, content);
		sendMessagesToAllOccupants(room, fromJID, msg);
	}

	public static String generateSubjectId(Date ts, String subject) {
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-1");
			md.update(String.valueOf(ts.getTime()).getBytes());
			if (subject != null) {
				md.update(subject.getBytes());
			}
			StringBuilder sb = new StringBuilder();
			for (byte b : md.digest()) {
				sb.append(Character.forDigit((b & 0xF0) >> 4, 16));
				sb.append(Character.forDigit(b & 0xF, 16));
			}
			return sb.toString();
		} catch (NoSuchAlgorithmException ex) {
			return null;
		}
	}
//
//	/**
//	 * Method description
//	 *
//	 *
//	 * @param room
//	 * @param fromJID
//	 * @param sendDate
//	 * @param content
//	 *
//	 * @throws TigaseStringprepException
//	 */
//	public void sendMessagesToAllOccupants(final Room room, final JID fromJID, String messageId, final Element... content)
//			throws TigaseStringprepException {
//		room.fireOnMessageToOccupants(fromJID, content);
//
//		sendMessagesToAllOccupantsJids(room, fromJID, messageId, content);
//	}
//
//	public void sendMessagesToAllOccupantsJids(final Room room, final JID fromJID, final Element... content)
//			throws TigaseStringprepException {
//		sendMessagesToAllOccupantsJids(room, fromJID, null, content);
//	}
//
//	public void sendMessagesToAllOccupantsJids(final Room room, final JID fromJID, String messageId, final Element... content)
//			throws TigaseStringprepException {
//
//		for (String nickname : room.getOccupantsNicknames()) {
//			final Role role = room.getRole(nickname);
//
//			if (!role.isReceiveMessages()) {
//				continue;
//			}
//
//			final Collection<JID> occupantJids = room.getOccupantsJidsByNickname(nickname);
//
//			for (JID jid : occupantJids) {
//				Element e = new Element("message", new String[] { "type", "from", "to" }, new String[] { "groupchat",
//						fromJID.toString(), jid.toString() });
//				if (messageId != null)
//					e.setAttribute("id", messageId);
//				Packet message = Packet.packetInstance(e);
//				message.setXMLNS(Packet.CLIENT_XMLNS);
//
//				// Packet message = Message.getMessage(fromJID, jid,
//				// StanzaType.groupchat, null, null, null, null);
//
//				if (content != null) {
//					for (Element sub : content) {
//						if (sub != null) {
//							message.getElement().addChild(sub);
//						}
//					}
//				}
//
//				write(message);
//			}
//		}
//	}

	public void sendMessagesToAllOccupants(final Room room, final JID fromJID, final Packet msg) throws TigaseStringprepException {
		sendMessagesToAllOccupantsJids(room, fromJID, msg);

		room.fireOnMessageToOccupants(fromJID, msg);
	}

	public void sendMessagesToAllOccupantsJids(final Room room, final JID fromJID, final Packet msg)
			throws TigaseStringprepException {

		for (String nickname : room.getOccupantsNicknames()) {
			final Role role = room.getRole(nickname);

			if (!role.isReceiveMessages()) {
				continue;
			}

			final Collection<JID> occupantJids = room.getOccupantsJidsByNickname(nickname);

			for (JID jid : occupantJids) {
				Packet message = msg.copyElementOnly();//Packet.packetInstance(e);
				message.initVars(fromJID, jid);
				message.setXMLNS(Packet.CLIENT_XMLNS);

				write(message);
			}
		}
	}
}
