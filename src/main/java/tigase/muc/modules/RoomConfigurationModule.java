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

import tigase.component.exceptions.RepositoryException;
import tigase.criteria.Criteria;
import tigase.criteria.ElementCriteria;
import tigase.form.Field;
import tigase.form.Form;
import tigase.kernel.beans.Bean;
import tigase.kernel.beans.Inject;
import tigase.muc.*;
import tigase.muc.exceptions.MUCException;
import tigase.muc.history.HistoryProvider;
import tigase.muc.modules.PresenceModule.PresenceWrapper;
import tigase.muc.repository.IMucRepository;
import tigase.server.Packet;
import tigase.util.stringprep.TigaseStringprepException;
import tigase.xml.Element;
import tigase.xmpp.Authorization;
import tigase.xmpp.StanzaType;
import tigase.xmpp.jid.BareJID;
import tigase.xmpp.jid.JID;

import java.util.logging.Level;

/**
 * @author bmalkow
 */
@Bean(name = RoomConfigurationModule.ID, active = true)
public class RoomConfigurationModule
		extends AbstractMucModule {

	public static final String ID = "ownerModule";

	private static final Criteria CRIT = ElementCriteria.name("iq")
			.add(ElementCriteria.name("query", "http://jabber.org/protocol/muc#owner"));
	@Inject
	private MUCConfig config;
	@Inject
	private HistoryProvider historyProvider;
	@Inject(nullAllowed = false)
	private GroupchatMessageModule messageModule;
	@Inject
	private IMucRepository repository;

	public static Element createRoomCreatedEvent(Room room) {
		Element event = new Element("RoomCreated", new String[]{"xmlns"}, new String[]{"tigase:events:muc"});
		event.addChild(new Element("room", room.getRoomJID().toString()));
		event.addChild(new Element("creatorJID", room.getCreatorJid().toString()));
		return event;
	}

	public void destroy(Room room, String jid, String reason) throws TigaseStringprepException, RepositoryException {
		Element destroy = new Element("destroy");

		if (jid != null) {
			destroy.addAttribute("jid", jid);
		}
		if (reason != null) {
			destroy.addChild(new Element("reason", reason));
		}
		destroy(room, destroy);
	}

	@Override
	public String[] getFeatures() {
		return null;
	}

	@Override
	public Criteria getModuleCriteria() {
		return CRIT;
	}

	@Override
	public void process(Packet element) throws MUCException {
		try {
			final StanzaType type = element.getType();

			if (getNicknameFromJid(element.getTo()) != null) {
				throw new MUCException(Authorization.BAD_REQUEST);
			}
			if (type == StanzaType.set) {
				processSet(element);
			} else if (type == StanzaType.get) {
				processGet(element);
			} else {
				throw new MUCException(Authorization.BAD_REQUEST);
			}
		} catch (MUCException e1) {
			throw e1;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private void destroy(Room room, Element destroyElement) throws TigaseStringprepException, RepositoryException {
		for (String occupantNickname : room.getOccupantsNicknames()) {
			for (JID occupantJid : room.getOccupantsJidsByNickname(occupantNickname)) {
				final Element p = new Element("presence");

				p.addAttribute("type", "unavailable");

				PresenceWrapper presence = PresenceWrapper.preparePresenceW(room, occupantJid, p,
																			occupantJid.getBareJID(), occupantNickname,
																			Affiliation.none, Role.none);

				presence.x.addChild(destroyElement);
				write(presence.packet);
			}
		}

		// XXX TODO
		// throw new
		// MUCException(Authorization.FEATURE_NOT_IMPLEMENTED);
		if (log.isLoggable(Level.FINE)) {
			log.fine("Destroying room " + room.getRoomJID());
		}

		repository.destroyRoom(room, destroyElement);
		if (historyProvider != null) {
			if (log.isLoggable(Level.FINE)) {
				log.fine("Removing history of room " + room.getRoomJID());
			}
			historyProvider.removeHistory(room);
		} else if (log.isLoggable(Level.FINE)) {
			log.fine("Cannot remove history of room " + room.getRoomJID() +
							 " because history provider is not available.");
		}
	}

	private Element makeConfigFormIq(final Element request, final Room room, final RoomConfig roomConfig) {
		final Element response = createResultIQ(request);
		Element query = new Element("query", new String[]{"xmlns"},
									new String[]{"http://jabber.org/protocol/muc#owner"});

		response.addChild(query);

		Form form = roomConfig.getConfigForm();
		form.removeField("muc#roomconfig_roomadmins");

		String[] adminsArrays;
		if (room == null) {
			adminsArrays = new String[]{};
		} else {
			adminsArrays = room.getAffiliations()
					.stream()
					.filter(jid -> room.getAffiliation(jid).getAffiliation() == Affiliation.admin)
					.map(bareJID -> bareJID.toString())
					.toArray(String[]::new);
		}

		form.addField(Field.fieldJidMulti("muc#roomconfig_roomadmins", adminsArrays, "Full List of Room Admins"));
		query.addChild(form.getElement());

		return response;
	}

	private void processGet(final Packet element) throws RepositoryException, MUCException {
		try {
			final BareJID roomJID = BareJID.bareJIDInstance(element.getAttributeStaticStr(Packet.TO_ATT));
			JID senderJID = JID.jidInstance(element.getAttributeStaticStr(Packet.FROM_ATT));
			Room room = repository.getRoom(roomJID);

			if (room == null) {
				Packet p = Packet.packetInstance(
						makeConfigFormIq(element.getElement(), null, repository.getDefaultRoomConfig()));
				p.setXMLNS(Packet.CLIENT_XMLNS);
				write(p);
			} else {
				if (room.getAffiliation(senderJID.getBareJID()).getAffiliation() != Affiliation.owner) {
					throw new MUCException(Authorization.FORBIDDEN);
				}

				final Element response = makeConfigFormIq(element.getElement(), room, room.getConfig());

				Packet p = Packet.packetInstance(response);
				p.setXMLNS(Packet.CLIENT_XMLNS);
				write(p);
			}
		} catch (TigaseStringprepException e) {
			throw new MUCException(Authorization.BAD_REQUEST);
		}
	}

	private void processSet(final Packet element) throws RepositoryException, MUCException {
		try {
			final JID roomJID = JID.jidInstance(element.getAttributeStaticStr(Packet.TO_ATT));
			if (roomJID.getLocalpart() == null) {
				throw new MUCException(Authorization.JID_MALFORMED);
			}
			
			JID senderJID = JID.jidInstance(element.getAttributeStaticStr(Packet.FROM_ATT));
			final Element query = element.getElement().getChild("query", "http://jabber.org/protocol/muc#owner");
			Room room = repository.getRoom(roomJID.getBareJID());

			boolean roomCreated = false;
			final Element x = query.getChild("x", "jabber:x:data");
			final Element destroy = query.getChild("destroy");

			if (room == null && destroy != null) {
				throw new MUCException(Authorization.ITEM_NOT_FOUND, "There is no such room.");
			} else if (room == null) {
				roomCreated = true;
				room = repository.createNewRoom(roomJID.getBareJID(), senderJID);
				room.addAffiliationByJid(senderJID.getBareJID(), RoomAffiliation.owner);
			}

			final Affiliation affiliation = room.getAffiliation(senderJID.getBareJID()).getAffiliation();

			if (room.getAffiliation(senderJID.getBareJID()).getAffiliation() != Affiliation.owner) {
				throw new MUCException(Authorization.FORBIDDEN);
			}

			if (destroy != null) {
				if (!affiliation.isDestroyRoom()) {
					throw new MUCException(Authorization.FORBIDDEN);
				}
				destroy(room, destroy);
				write(element.okResult((Element) null, 0));
			} else if (x != null) {
				Form form = new Form(x);

				if ("submit".equals(form.getType())) {
					String ps = form.getAsString(RoomConfig.MUC_ROOMCONFIG_ROOMSECRET_KEY);

					if ((form.getAsBoolean(RoomConfig.MUC_ROOMCONFIG_PASSWORDPROTECTEDROOM_KEY) == Boolean.TRUE) &&
							((ps == null) || (ps.length() == 0))) {
						throw new MUCException(Authorization.NOT_ACCEPTABLE, "Passwords cannot be empty");
					}
					write(element.okResult((Element) null, 0));

					final RoomConfig oldConfig = room.getConfig().clone();

					room.getConfig().copyFrom(form);
					if (room.isRoomLocked()) {
						room.setRoomLocked(false);
						if (log.isLoggable(Level.FINE)) {
							log.fine("Room " + room.getRoomJID() + " is now unlocked");
						}
						String nickname = room.getOccupantsNickname(senderJID);
						if (nickname != null && config.isWelcomeMessagesEnabled() &&
								room.getConfig().isWelcomeMessageEnabled()) {
							sendMucMessage(room, nickname, "Room is now unlocked");
						}
					}

					String[] admins = form.getAsStrings("muc#roomconfig_roomadmins");
					if (admins != null) {
						for (String admin : admins) {
							room.addAffiliationByJid(BareJID.bareJIDInstance(admin), RoomAffiliation.admin);
						}
					}

					String[] compareResult = room.getConfig().compareTo(oldConfig);

					if (compareResult != null) {
						Element z = new Element("x", new String[]{"xmlns"},
												new String[]{"http://jabber.org/protocol/muc#user"});

						for (String code : compareResult) {
							z.addChild(new Element("status", new String[]{"code"}, new String[]{code}));
						}
						this.messageModule.sendMessagesToAllOccupants(room, roomJID, z);
					}
				}
				if (roomCreated) {
					fireEvent(createRoomCreatedEvent(room));
				}
			} else {
				throw new MUCException(Authorization.BAD_REQUEST);
			}
		} catch (TigaseStringprepException e) {
			throw new MUCException(Authorization.BAD_REQUEST);
		}
	}

}
