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
import tigase.muc.Role;
import tigase.muc.Room;
import tigase.muc.exceptions.MUCException;
import tigase.muc.repository.IMucRepository;
import tigase.server.Packet;
import tigase.util.stringprep.TigaseStringprepException;
import tigase.xml.Element;
import tigase.xmpp.Authorization;
import tigase.xmpp.jid.BareJID;
import tigase.xmpp.jid.JID;

import java.util.Collection;
import java.util.logging.Level;

/**
 * @author bmalkow
 */
@Bean(name = IqStanzaForwarderModule.ID, active = true)
public class IqStanzaForwarderModule
		extends AbstractMucModule {

	public static final String ID = "iqforwarder";
	private final static Criteria SELF_PING_CRIT = ElementCriteria.nameType("iq", "get")
			.add(ElementCriteria.name("ping", "urn:xmpp:ping"));
	@Inject
	private IMucRepository repository;
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

			final Room room = repository.getRoom(roomJID);
			if (room == null) {
				throw new MUCException(Authorization.ITEM_NOT_FOUND);
			}

			final String senderNickname = room.getOccupantsNickname(senderJID);
			final Role senderRole = room.getRole(senderNickname);

			if (log.isLoggable(Level.FINEST)) {
				log.log(Level.FINEST,
						"Processing IQ stanza, from: {0}, to: {1}, recipientNickname: {2}, senderNickname: {3}, senderRole: {4} ",
						new Object[]{senderJID, roomJID, recipientNickname, senderNickname, senderRole});
			}

			if (!senderRole.isSendPrivateMessages()) {
				throw new MUCException(Authorization.NOT_ALLOWED, "Role is not allowed to send private messages");
			}
			if (room.getOccupantsJidsByNickname(senderNickname).size() > 1) {
				throw new MUCException(Authorization.NOT_ALLOWED, "Many source resources detected.");
			}

			final Collection<JID> recipientJids = room.getOccupantsJidsByNickname(recipientNickname);
			if (recipientJids == null || recipientJids.isEmpty()) {
				throw new MUCException(Authorization.ITEM_NOT_FOUND, "Unknown recipient");
			}

			if (recipientJids.size() > 1) {
				throw new MUCException(Authorization.NOT_ALLOWED, "Many destination resources detected.");
			}

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
			log.log(Level.FINEST, "Error during forwarding IQ", e);
			throw new RuntimeException(e);
		}
	}

	protected boolean checkIfProcessed(Element element) {
		if (element.getName() != "iq") {
			return false;
		}
		try {
			final String recipientNickname = getNicknameFromJid(JID.jidInstance(element.getAttributeStaticStr("to")));
			if (recipientNickname == null) {
				return false;
			}
			final JID senderJID = JID.jidInstance(element.getAttributeStaticStr("from"));
			final BareJID roomJID = BareJID.bareJIDInstance(element.getAttributeStaticStr("to"));
			final Room room = repository.getRoom(roomJID);
			if (room == null) {
				return false;
			}
			final String senderNickname = room.getOccupantsNickname(senderJID);
			if (senderNickname == null) {
				return false;
			}

			if (isSelfPing(element)) {
				return !recipientNickname.equals(senderNickname);
			} else {
				return true;
			}
		} catch (TigaseStringprepException e) {
			return false;
		} catch (RepositoryException e) {
			return false;
		} catch (MUCException e) {
			return false;
		}
	}

	private boolean isSelfPing(Element element) {
		return SELF_PING_CRIT.match(element);
	}

}
