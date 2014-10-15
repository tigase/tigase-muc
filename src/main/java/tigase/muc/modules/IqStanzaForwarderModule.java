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

import tigase.server.Packet;

import tigase.xmpp.Authorization;
import tigase.xmpp.BareJID;
import tigase.xmpp.JID;

import tigase.component.exceptions.ComponentException;
import tigase.criteria.Criteria;
import tigase.muc.Role;
import tigase.muc.Room;
import tigase.muc.exceptions.MUCException;
import tigase.util.TigaseStringprepException;
import tigase.xml.Element;

import java.util.Collection;
import java.util.logging.Level;

/**
 * @author bmalkow
 * 
 */
public class IqStanzaForwarderModule extends AbstractMucModule {

	public static final String ID = "iqforwarder";

	private final Criteria crit = new Criteria() {

		@Override
		public Criteria add(Criteria criteria) {
			return null;
		}

		@Override
		public boolean match(Element element) {
			return checkIfProcessed(element);
		}
	};

	protected boolean checkIfProcessed(Element element) {
		if (element.getName() != "iq")
			return false;
		try {
			return getNicknameFromJid(JID.jidInstance(element.getAttributeStaticStr("to"))) != null;
		} catch (TigaseStringprepException e) {
			return false;
		}
	}

	@Override
	public String[] getFeatures() {
		// TODO Auto-generated method stub
		return null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see tigase.component.modules.Module#getModuleCriteria()
	 */
	@Override
	public Criteria getModuleCriteria() {
		return crit;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see tigase.component.modules.Module#process(tigase.server.Packet)
	 */
	@Override
	public void process(Packet packet) throws ComponentException, TigaseStringprepException {
		try {
			final JID senderJID = packet.getStanzaFrom();
			final BareJID roomJID = packet.getStanzaTo().getBareJID();
			final String recipientNickname = getNicknameFromJid(packet.getStanzaTo());

			if (recipientNickname == null) {
				throw new MUCException(Authorization.BAD_REQUEST);
			}

			final Room room = context.getMucRepository().getRoom(roomJID);
			if (room == null) {
				throw new MUCException(Authorization.ITEM_NOT_FOUND);
			}

			final String senderNickname = room.getOccupantsNickname( senderJID );
			final Role senderRole = room.getRole(senderNickname);

			if ( log.isLoggable( Level.FINEST ) ){
				log.log( Level.FINEST,
						 "Processing IQ stanza, from: {0}, to: {1}, recipientNickname: {2}, senderNickname: {3}, senderRole: {4} ",
						 new Object[] { senderJID, roomJID, recipientNickname, senderNickname, senderRole } );
			}

			if (!senderRole.isSendPrivateMessages()) {
				throw new MUCException(Authorization.NOT_ALLOWED, "Role is not allowed to send private messages");
			}
			if (room.getOccupantsJidsByNickname(senderNickname).size() > 1)
				throw new MUCException(Authorization.NOT_ALLOWED, "Many source resources detected.");

			final Collection<JID> recipientJids = room.getOccupantsJidsByNickname(recipientNickname);
			if (recipientJids == null || recipientJids.isEmpty())
				throw new MUCException(Authorization.ITEM_NOT_FOUND, "Unknown recipient");

			if (recipientJids.size() > 1)
				throw new MUCException(Authorization.NOT_ALLOWED, "Many destination resources detected.");

			JID recipientJid = recipientJids.iterator().next();

			final Element iq = packet.getElement().clone();
			iq.setAttribute("from", roomJID.toString() + "/" + senderNickname);
			iq.setAttribute("to", recipientJid.toString());

			Packet p = Packet.packetInstance(iq);
			p.setXMLNS(Packet.CLIENT_XMLNS);
			write(p);
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
