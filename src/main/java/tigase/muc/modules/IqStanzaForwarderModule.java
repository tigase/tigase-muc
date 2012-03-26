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

import tigase.criteria.Criteria;
import tigase.muc.ElementWriter;
import tigase.muc.MucConfig;
import tigase.muc.Role;
import tigase.muc.Room;
import tigase.muc.exceptions.MUCException;
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
public class IqStanzaForwarderModule extends AbstractModule {

	private Criteria crit;

	public IqStanzaForwarderModule(MucConfig config, ElementWriter writer, IMucRepository mucRepository) {
		super(config, writer, mucRepository);
		this.crit = new Criteria() {

			@Override
			public Criteria add(Criteria criteria) {
				return null;
			}

			@Override
			public boolean match(Element element) {
				return checkIfProcessed(element);
			}
		};
	}

	/**
	 * @param element
	 * @return
	 */
	protected boolean checkIfProcessed(Element element) {
		if (element.getName() != "iq")
			return false;
		try {
			return getNicknameFromJid(JID.jidInstance(element.getAttribute("to"))) != null;
		} catch (TigaseStringprepException e) {
			return false;
		}
	}

	@Override
	public String[] getFeatures() {
		return null;
	}

	@Override
	public Criteria getModuleCriteria() {
		return crit;
	}

	@Override
	public boolean isProcessedByModule(Element element) {
		try {
			return getNicknameFromJid(JID.jidInstance(element.getAttribute("to"))) != null;
		} catch (TigaseStringprepException e) {
			return false;
		}
	}

	@Override
	public void process(Packet element) throws MUCException {
		try {
			final JID senderJID = JID.jidInstance(element.getAttribute("from"));
			final BareJID roomJID = BareJID.bareJIDInstance(element.getAttribute("to"));
			final String recipientNickname = getNicknameFromJid(JID.jidInstance(element.getAttribute("to")));

			if (recipientNickname == null) {
				throw new MUCException(Authorization.BAD_REQUEST);
			}

			final Room room = repository.getRoom(roomJID);
			if (room == null) {
				throw new MUCException(Authorization.ITEM_NOT_FOUND);
			}

			final Role senderRole = room.getRoleByJid(senderJID);
			if (!senderRole.isSendPrivateMessages()) {
				throw new MUCException(Authorization.NOT_ALLOWED);
			}

			final JID recipientJid = room.getOccupantsJidsByNickname(recipientNickname);
			if (recipientJid == null)
				throw new MUCException(Authorization.ITEM_NOT_FOUND, "Unknown recipient");

			final String senderNickname = room.getOccupantsNickname(senderJID);

			final Element iq = element.getElement().clone();
			iq.setAttribute("from", roomJID.toString() + "/" + senderNickname);
			iq.setAttribute("to", recipientJid.toString());

			writer.write(Packet.packetInstance(iq));
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
