/**
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
import tigase.eventbus.HandleEvent;
import tigase.kernel.beans.Bean;
import tigase.kernel.beans.Initializable;
import tigase.kernel.beans.Inject;
import tigase.kernel.beans.UnregisterAware;
import tigase.muc.Affiliation;
import tigase.muc.Room;
import tigase.muc.exceptions.MUCException;
import tigase.muc.repository.IMucRepository;
import tigase.server.Packet;
import tigase.util.Algorithms;
import tigase.util.Base64;
import tigase.util.stringprep.TigaseStringprepException;
import tigase.xml.Element;
import tigase.xmpp.Authorization;
import tigase.xmpp.StanzaType;
import tigase.xmpp.jid.BareJID;
import tigase.xmpp.jid.JID;

import java.security.MessageDigest;
import java.util.logging.Level;

@Bean(name = VCardModule.ID, active = true)
public class VCardModule
		extends AbstractMucModule
		implements Initializable, UnregisterAware {

	public static final String ID = "vcard";

	public static final String NAME = "vCard";
	public static final String XMLNS = "vcard-temp";

	private static final Criteria CRIT = new Criteria() {
		@Override
		public Criteria add(Criteria criteria) {
			return null;
		}

		@Override
		public boolean match(Element element) {
			try {
				String to = element.getAttributeStaticStr(Packet.TO_ATT);
				return to != null && !JID.jidInstance(to).hasResource() &&
						ElementCriteria.name("iq").add(ElementCriteria.name(NAME, XMLNS)).match(element);
			} catch (TigaseStringprepException e) {
				return false;
			}
		}
	};
	private final static String SEPARATOR = ";";
	@Inject(nullAllowed = false)
	private GroupchatMessageModule messageModule;
	@Inject
	private IMucRepository repository;

	private static String calculatePhotoHash(byte[] photo) {
		if (photo == null) {
			return null;
		}
		try {
			MessageDigest sha = MessageDigest.getInstance("SHA-1");
			return Algorithms.bytesToHex(sha.digest(photo));
		} catch (Exception e) {
			return null;
		}
	}

	private static Photo getPhoto(Packet packet) {
		Element vcard = packet.getElement().getChild(NAME, XMLNS);
		if (vcard == null) {
			return null;
		}
		Element photo = vcard.getChild("PHOTO");
		if (photo == null) {
			return null;
		}

		Element type = photo.getChild("TYPE");
		Element binval = photo.getChild("BINVAL");

		return new Photo(type.getCData(), Base64.decode(binval.getCData()));
	}

	@Override
	public Criteria getModuleCriteria() {
		return CRIT;
	}

	@Override
	public String[] getFeatures() {
		return new String[]{XMLNS};
	}

	@Override
	public void process(Packet packet) throws ComponentException, TigaseStringprepException {
		try {
//			if (getNicknameFromJid(packet.getTo()) != null) {
//				throw new MUCException(Authorization.BAD_REQUEST);
//			}
			if (packet.getType() == StanzaType.get) {
				processGet(packet);
			} else if (packet.getType() == StanzaType.set) {
				processSet(packet);
			} else {
				throw new ComponentException(Authorization.BAD_REQUEST);
			}
		} catch (MUCException e1) {
			throw e1;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void initialize() {
		eventBus.registerEvent(VCardChangedEvent.class.getName(), "FIred when room avatar is changed", false);
		eventBus.registerAll(this);
	}

	@Override
	public void beforeUnregister() {
		eventBus.unregisterAll(this);
	}

	@HandleEvent(filter = HandleEvent.Type.remote)
	public void onAvatarChanged(final VCardChangedEvent event) {
		final Room room;
		try {
			room = repository.getRoom(event.getRoomJID());
			if (room != null) {
				room.setAvatarHash(event.getHash());
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private void processGet(final Packet packet) throws MUCException, RepositoryException {
		final BareJID roomJID = packet.getStanzaTo().getBareJID();
		final Room room = repository.getRoom(roomJID);
		if (room == null) {
			throw new MUCException(Authorization.ITEM_NOT_FOUND);
		}

		final Element vCard = new Element(NAME, new String[]{"xmlns"}, new String[]{XMLNS});
		String encodedAvatar = repository.getRoomAvatar(room);
		if (encodedAvatar != null && encodedAvatar.length() > 0) {
			String[] items = encodedAvatar.split(SEPARATOR);
			Element photo = new Element("PHOTO");
			photo.addChild(new Element("TYPE", items[0]));
			photo.addChild(new Element("BINVAL", items[1]));
			vCard.addChild(photo);
		}
		write(packet.okResult(vCard, 0));
	}

	private void processSet(final Packet packet) throws MUCException, RepositoryException {
		final BareJID roomJID = packet.getStanzaTo().getBareJID();
		final Room room = repository.getRoom(roomJID);
		if (room == null) {
			throw new MUCException(Authorization.ITEM_NOT_FOUND);
		}
		if (!room.getConfig().isPersistentRoom()) {
			throw new MUCException(Authorization.FEATURE_NOT_IMPLEMENTED);
		}

		final JID senderJid = packet.getStanzaFrom();
		final String nickName = room.getOccupantsNickname(senderJid);
		final Affiliation senderAffiliation = room.getAffiliation(senderJid.getBareJID()).getAffiliation();

		if (senderAffiliation != Affiliation.owner) {
			throw new MUCException(Authorization.NOT_ALLOWED);
		}

		Photo photo = getPhoto(packet);

		if (photo != null) {
			String hash = calculatePhotoHash(photo.photo);
			repository.updateRoomAvatar(room, photo.type + SEPARATOR + Base64.encode(photo.photo), hash);
			room.setAvatarHash(hash);
			eventBus.fire(new VCardChangedEvent(room.getRoomJID(), hash));
		} else {
			repository.updateRoomAvatar(room, null, null);
			room.setAvatarHash(null);
			eventBus.fire(new VCardChangedEvent(room.getRoomJID(), null));
		}
		write(packet.okResult((Element) null, 0));
		sendConfigurationChangeInfo(room);
	}

	private void sendConfigurationChangeInfo(Room room) {
		try {
			final Element x = new Element("x", new String[]{"xmlns"},
										  new String[]{"http://jabber.org/protocol/muc#user"});
			x.addChild(new Element("status", new String[]{"code"}, new String[]{"104"}));

			this.messageModule.sendMessagesToAllOccupants(room, JID.jidInstance(room.getRoomJID()), x);
		} catch (TigaseStringprepException e) {
			log.log(Level.WARNING, "Invalid JID. ", e);
		}
	}

	private static class Photo {

		private final byte[] photo;
		private final String type;

		public Photo(String type, byte[] photo) {
			this.type = type;
			this.photo = photo;
		}
	}

	public static class VCardChangedEvent {

		private String hash;
		private BareJID roomJID;

		public VCardChangedEvent() {
		}

		public VCardChangedEvent(BareJID roomJID, String hash) {
			this.roomJID = roomJID;
			this.hash = hash;
		}

		public String getHash() {
			return hash;
		}

		public void setHash(String hash) {
			this.hash = hash;
		}

		public BareJID getRoomJID() {
			return roomJID;
		}

		public void setRoomJID(BareJID roomJID) {
			this.roomJID = roomJID;
		}

	}
}
