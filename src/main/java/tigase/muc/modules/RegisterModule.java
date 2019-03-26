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
import tigase.kernel.beans.Bean;
import tigase.kernel.beans.Inject;
import tigase.muc.Affiliation;
import tigase.muc.MUCComponent;
import tigase.muc.Room;
import tigase.muc.RoomAffiliation;
import tigase.muc.exceptions.MUCException;
import tigase.muc.repository.IMucRepository;
import tigase.server.Command;
import tigase.server.DataForm;
import tigase.server.Packet;
import tigase.util.stringprep.TigaseStringprepException;
import tigase.xml.Element;
import tigase.xmpp.Authorization;
import tigase.xmpp.StanzaType;
import tigase.xmpp.jid.BareJID;
import tigase.xmpp.jid.JID;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.logging.Level;
import java.util.stream.Stream;

/**
 * Module provides ability to register nicknames in MUC rooms and use "send to offline" feature allowing users have their
 * group chat messages delivered to their XMPP server even when they are offline.
 */
@Bean(name = "register", parent = MUCComponent.class, active = true)
public class RegisterModule extends AbstractMucModule {

	private static final String QUERY_NAME = "query";
	private static final String REGISTER_XMLNS = "jabber:iq:register";
	private static final String NICKNAME_FIELD_NAME = "muc#register_roomnick";
	private static final String OFFLINE_FIELD_NAME = "{http://tigase.org/protocol/muc}offline";

	private static final Criteria CRITERIA = ElementCriteria.name("iq").add(ElementCriteria.name(QUERY_NAME, REGISTER_XMLNS));

	@Inject
	private IMucRepository mucRepository;

	@Override
	public String[] getFeatures() {
		return Stream.concat(Arrays.stream(super.getFeatures()), Stream.of("http://tigase.org/protocol/muc#offline"))
				.toArray(String[]::new);
	}

	@Override
	public Criteria getModuleCriteria() {
		return CRITERIA;
	}

	@Override
	public void process(Packet packet) throws ComponentException, TigaseStringprepException {
		try {
			final StanzaType type = packet.getType();

			if (type == StanzaType.set) {
				processSet(packet);
			} else if (type == StanzaType.get) {
				processGet(packet);
			} else {
				throw new MUCException(Authorization.BAD_REQUEST);
			}
		} catch (MUCException e1) {
			throw e1;
		} catch (Exception e) {
			log.log(Level.FINEST, "Error during registration of nickname", e);
			throw new RuntimeException(e);
		}
	}

	protected void processGet(Packet packet) throws RepositoryException, MUCException {
		JID sender = packet.getStanzaFrom();
		Room room = getRoom(packet.getStanzaTo().getBareJID());

		checkPermission(room, sender);

		Element queryEl = packet.getElemChild(QUERY_NAME,REGISTER_XMLNS);
		Element resultEl = queryEl.clone();
		resultEl.setChildren(Collections.emptyList());

		addForm(resultEl, room, sender);

		write(packet.okResult(resultEl, 0));
	}

	protected void processSet(Packet packet) throws RepositoryException, MUCException {
		JID sender = packet.getStanzaFrom();
		Room room = getRoom(packet.getStanzaTo().getBareJID());

		checkPermission(room, sender);

		Element queryEl = packet.getElement().getChild(QUERY_NAME, REGISTER_XMLNS);
		if (queryEl.getChild("remove") != null) {
			room.addAffiliationByJid(sender.getBareJID(), RoomAffiliation.none);
		} else {
			String nickname = DataForm.getFieldValue(queryEl, NICKNAME_FIELD_NAME);
			String currentNickname = room.getOccupantsNickname(packet.getStanzaFrom());
			if (currentNickname == null || !currentNickname.equals(nickname)) {
				throw new MUCException(Authorization.NOT_ACCEPTABLE,
									   "You may only register a nickname which you are using now.");
			}
			boolean offline = Optional.ofNullable(DataForm.getFieldValue(queryEl, OFFLINE_FIELD_NAME))
					.map(String::trim)
					.map(str -> "1".equals(str) || "true".equals(str))
					.orElse(false);

			RoomAffiliation roomAffiliation = room.getAffiliation(packet.getStanzaFrom().getBareJID());
			boolean changed = roomAffiliation.isPersistentOccupant() != offline;
			if (changed) {
				// apply changes..
				Affiliation affiliation = roomAffiliation.getAffiliation();
				if (offline && affiliation == Affiliation.none) {
					affiliation = Affiliation.member;
				}
				RoomAffiliation newRoomAffiliation = RoomAffiliation.from(affiliation, offline, nickname);
				room.addAffiliationByJid(sender.getBareJID(), newRoomAffiliation);
			}
		}
		write(packet.okResult((String) null, 0));
	}

	protected Room getRoom(BareJID roomJID) throws MUCException, RepositoryException {
		Room room = mucRepository.getRoom(roomJID);
		if (room == null) {
			throw new MUCException(Authorization.ITEM_NOT_FOUND, "Room does not exist.");
		}
		return room;
	}

	protected void checkPermission(Room room, JID jid) throws MUCException {
		Affiliation affiliation = room.getAffiliation(jid.getBareJID()).getAffiliation();
		if (affiliation != Affiliation.none && affiliation != Affiliation.outcast &&  !room.isOccupantInRoom(jid)) {
			throw new MUCException(Authorization.NOT_ALLOWED, "You are not allowed to register.");
		}
	}

	protected void addForm(Element parent, Room room, JID sender) {
		RoomAffiliation roomAffiliation = room.getAffiliation(sender.getBareJID());
		switch (roomAffiliation.getAffiliation()) {
			case none:
			case outcast:
				break;
			default:
				parent.addChild(new Element("registered"));
		}
		new DataForm.Builder(parent, Command.DataType.form).addInstructions(new String[]{
				"Please provide following information to register with this room for offline message delivery."})
				.addTitle("Registration form")
				.withFields(builder -> {
					builder.addField(DataForm.FieldType.Hidden, "FORM_TYPE")
							.setValue("http://jabber.org/protocol/muc#register")
							.build();
					builder.addField(DataForm.FieldType.TextSingle, NICKNAME_FIELD_NAME)
							.setLabel("Nickname")
							.setValue(Optional.ofNullable(roomAffiliation.getRegisteredNickname()).orElseGet(() -> room.getOccupantsNickname(sender)))
							.build();
					if (!room.getConfig().isRoomModerated()) {
						builder.addField(DataForm.FieldType.Boolean, OFFLINE_FIELD_NAME)
								.setLabel("Request offline message delivery")
								.setValue(roomAffiliation.isPersistentOccupant())
								.build();
					}
				})
				.build();
	}
}
