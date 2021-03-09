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
import tigase.muc.MUCConfig;
import tigase.muc.Role;
import tigase.muc.Room;
import tigase.muc.exceptions.MUCException;
import tigase.muc.repository.IMucRepository;
import tigase.server.Packet;
import tigase.util.Base64;
import tigase.util.stringprep.TigaseStringprepException;
import tigase.xml.Element;
import tigase.xmpp.Authorization;
import tigase.xmpp.StanzaType;
import tigase.xmpp.jid.BareJID;
import tigase.xmpp.jid.JID;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
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
	private MUCConfig config;
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
			final boolean isRequest = this.isRequest(packet);

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

			// if that is response for vCard request and it come from the BareJID, we should look for occupant with nickname with this bare jid
			final String senderNickname = Optional.ofNullable(room.getOccupantsNickname(senderJID))
					.or(() -> (!isRequest)
							  ? room.getOccupantsNicknames(senderJID.getBareJID()).stream().findFirst()
							  : Optional.empty())
					.orElseThrow(() -> new MUCException(Authorization.NOT_ACCEPTABLE,
														"" + getNicknameFromJid(senderJID) + " is not in room"));
			final Role senderRole = room.getRole(senderNickname);

			if (log.isLoggable(Level.FINEST)) {
				log.log(Level.FINEST,
						"Processing IQ stanza, from: {0}, to: {1}, recipientNickname: {2}, senderNickname: {3}, senderRole: {4} ",
						new Object[]{senderJID, roomJID, recipientNickname, senderNickname, senderRole});
			}

			if (!senderRole.isSendPrivateMessages()) {
				throw new MUCException(Authorization.NOT_ALLOWED, "Role is not allowed to send private messages");
			}

			forwardPacket(packet, room, senderNickname, senderJID, recipientNickname, isRequest);
		} catch (MUCException e1) {
			throw e1;
		} catch (TigaseStringprepException e) {
			throw new MUCException(Authorization.BAD_REQUEST);
		} catch (Exception e) {
			log.log(Level.FINEST, "Error during forwarding IQ", e);
			throw new RuntimeException(e);
		}
	}

	protected boolean isRequest(Packet packet) throws MUCException {
		final StanzaType type = packet.getType();
		if (type == null) {
			throw new MUCException(Authorization.BAD_REQUEST, "IQ stanza is required to have a type");
		}
		switch (type) {
			case result:
			case error:
				return false;
			case set:
			case get:
				return true;
			default:
				throw new MUCException(Authorization.BAD_REQUEST, "IQ stanza has invalid type");
		}
	}

	protected static String generateJidShortcut(JID jid) throws ComponentException {
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-1");
			byte[] arr = md.digest(jid.toString().getBytes(StandardCharsets.UTF_8));
			return Base64.encode(Arrays.copyOfRange(arr, arr.length - 6, arr.length));
		} catch (NoSuchAlgorithmException e) {
			throw new ComponentException(Authorization.INTERNAL_SERVER_ERROR, e.getMessage());
		}
	}

	protected void forwardPacket(Packet packet, Room room, String senderNickname, JID senderJID, String recipientNickname, boolean isRequest) throws ComponentException, TigaseStringprepException {
		if (config.isMultiItemModeForwardBest()) {
			final String id = packet.getStanzaId();
			if (id == null) {
				throw new MUCException(Authorization.BAD_REQUEST, "IQ stanza is required to have id attribute");
			}

			if (isRequest) {
				JID recipientJid = room.getOccupantJidForIqRequestForward(recipientNickname).orElseThrow(() -> new MUCException(Authorization.ITEM_NOT_FOUND, "Unknown recipient"));
				// if this request is for vCard then we should route it to the bare JID of the recipient occupant
				if (packet.getElemChild("vCard", "vcard-temp") != null) {
					recipientJid = recipientJid.copyWithoutResource();
				}
				String idPrefix = generateJidShortcut(senderJID);

				forwardPacket(packet, room.getRoomJID(), senderNickname, recipientJid, idPrefix + "-" + id);
			} else if (id.length() >= 8) {
				String idPrefix = id.substring(0,8);
				room.getOccupantJidForIqResponseForward(recipientNickname, jid -> {
					try {
						return idPrefix.equals(generateJidShortcut(jid));
					} catch (ComponentException ex) {
						return false;
					}
				}).ifPresent(recipientJid -> {
					try {
						forwardPacket(packet, room.getRoomJID(), senderNickname, recipientJid, id.substring(9));
					} catch (TigaseStringprepException ex) {
						log.log(Level.FINEST, "Could not forward response to request sender", ex);
					}
				});
			} else if (id.startsWith("spng-")) {
				// that is handled by self-ping, we do not have any other way to detect self-ping..
			} else {
				throw new MUCException(Authorization.ITEM_NOT_FOUND, "Unknown recipient");
			}
		} else {
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

			forwardPacket(packet, room.getRoomJID(), senderNickname, recipientJids.iterator().next(), null);
		}
	}

	protected void forwardPacket(Packet packet, BareJID roomJID, String senderNickname, JID recipientJid, String id)
			throws TigaseStringprepException {
		final Element iq = packet.getElement().clone();
		if (id != null) {
			iq.setAttribute("id", id);
		}

		Packet p = Packet.packetInstance(iq, JID.jidInstance(roomJID, senderNickname), recipientJid);
		p.setXMLNS(Packet.CLIENT_XMLNS);
		write(p);
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
