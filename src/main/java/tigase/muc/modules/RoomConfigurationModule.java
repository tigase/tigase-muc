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

import java.util.List;

import tigase.criteria.Criteria;
import tigase.criteria.ElementCriteria;
import tigase.form.Form;
import tigase.muc.Affiliation;
import tigase.muc.MucConfig;
import tigase.muc.Room;
import tigase.muc.RoomConfig;
import tigase.muc.exceptions.MUCException;
import tigase.muc.repository.IMucRepository;
import tigase.muc.repository.RepositoryException;
import tigase.xml.Element;
import tigase.xmpp.Authorization;

/**
 * @author bmalkow
 * 
 */
public class RoomConfigurationModule extends AbstractModule {

	private static final Criteria CRIT = ElementCriteria.name("iq").add(
			ElementCriteria.name("query", "http://jabber.org/protocol/muc#owner"));

	private final GroupchatMessageModule messageModule;

	public RoomConfigurationModule(MucConfig config, IMucRepository mucRepository, GroupchatMessageModule messageModule) {
		super(config, mucRepository);
		this.messageModule = messageModule;
	}

	@Override
	public String[] getFeatures() {
		return null;
	}

	@Override
	public Criteria getModuleCriteria() {
		return CRIT;
	}

	private Element makeConfigFormIq(final Element request, final RoomConfig roomConfig) {
		final Element response = createResultIQ(request);
		Element query = new Element("query", new String[] { "xmlns" }, new String[] { "http://jabber.org/protocol/muc#owner" });
		response.addChild(query);
		query.addChild(roomConfig.getConfigForm().getElement());
		return response;
	}

	@Override
	public List<Element> process(Element element) throws MUCException {
		try {
			if (getNicknameFromJid(element.getAttribute("to")) != null) {
				throw new MUCException(Authorization.BAD_REQUEST);
			}

			final String type = element.getAttribute("type");
			if ("set".equals(type)) {
				return processSet(element);
			} else if ("get".equals(type)) {
				return processGet(element);
			} else {
				throw new MUCException(Authorization.BAD_REQUEST);
			}
		} catch (MUCException e1) {
			throw e1;
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}

	private List<Element> processGet(final Element element) throws RepositoryException, MUCException {
		final String roomId = getRoomId(element.getAttribute("to"));
		final String senderJid = element.getAttribute("from");

		Room room = repository.getRoom(roomId);

		if (room == null) {
			return makeArray(makeConfigFormIq(element, repository.getDefaultRoomConfig()));
		}
		if (room.getAffiliation(senderJid) != Affiliation.owner) {
			throw new MUCException(Authorization.FORBIDDEN);
		}

		final Element response = makeConfigFormIq(element, room.getConfig());
		return makeArray(response);
	}

	private List<Element> processSet(final Element element) throws RepositoryException, MUCException {
		final String roomId = getRoomId(element.getAttribute("to"));
		final String senderJid = element.getAttribute("from");
		final Element query = element.getChild("query", "http://jabber.org/protocol/muc#owner");

		Room room = repository.getRoom(roomId);
		if (room == null) {
			room = repository.createNewRoom(roomId, senderJid);
		} else {
			if (room.getAffiliation(senderJid) != Affiliation.owner) {
				throw new MUCException(Authorization.FORBIDDEN);
			}
		}

		List<Element> result = makeArray(createResultIQ(element));

		final Element x = query.getChild("x", "jabber:x:data");
		final Element destroy = query.getChild("destroy");
		if (destroy != null) {
			// XXX TODO
			throw new MUCException(Authorization.FEATURE_NOT_IMPLEMENTED);
		} else if (x != null) {
			Form form = new Form(x);
			if ("submit".equals(form.getType())) {
				String ps = form.getAsString(RoomConfig.MUC_ROOMCONFIG_ROOMSECRET_KEY);
				if (form.getAsBoolean(RoomConfig.MUC_ROOMCONFIG_PASSWORDPROTECTEDROOM_KEY) == Boolean.TRUE
						&& (ps == null || ps.length() == 0)) {
					throw new MUCException(Authorization.NOT_ACCEPTABLE, "Passwords cannot be empty");
				}

				final RoomConfig oldConfig = room.getConfig().clone();
				if (room.isRoomLocked()) {
					room.setRoomLocked(false);
					log.fine("Room " + room.getRoomId() + " is now unlocked");
					result.add(prepateMucMessage(room, room.getOccupantsNickname(senderJid), "Room is now unlocked"));
				}
				room.getConfig().copyFrom(form);
				String[] compareResult = room.getConfig().compareTo(oldConfig);
				if (compareResult != null) {
					Element z = new Element("x", new String[] { "xmlns" },
							new String[] { "http://jabber.org/protocol/muc#user" });
					for (String code : compareResult) {
						z.addChild(new Element("status", new String[] { "code" }, new String[] { code }));
					}
					result.addAll(this.messageModule.sendMessagesToAllOccupants(room, roomId, z));
				}
			}
		} else {
			throw new MUCException(Authorization.BAD_REQUEST);
		}
		return result;
	}
}
