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

import java.util.Set;

import tigase.criteria.Criteria;
import tigase.criteria.ElementCriteria;
import tigase.muc.ElementWriter;
import tigase.muc.MUCComponent;
import tigase.muc.MucConfig;
import tigase.muc.Room;
import tigase.muc.exceptions.MUCException;
import tigase.muc.repository.IMucRepository;
import tigase.server.Packet;
import tigase.util.TigaseStringprepException;
import tigase.xml.Element;
import tigase.xmpp.Authorization;
import tigase.xmpp.BareJID;

/**
 * @author bmalkow
 * 
 */
public class DiscoInfoModule extends AbstractModule {

	private static final Criteria CRIT = ElementCriteria.nameType("iq", "get").add(
			ElementCriteria.name("query", "http://jabber.org/protocol/disco#info"));

	private static void addFeature(Element query, String feature) {
		query.addChild(new Element("feature", new String[] { "var" }, new String[] { feature }));
	}

	private final MUCComponent muc;

	public DiscoInfoModule(MucConfig config, ElementWriter writer, IMucRepository mucRepository, final MUCComponent component) {
		super(config, writer, mucRepository);
		this.muc = component;
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
			String toXML = element.getAttribute("to");
			final BareJID roomJID = BareJID.bareJIDInstance(toXML);
			Element resultQuery = new Element("query", new String[] { "xmlns" },
					new String[] { "http://jabber.org/protocol/disco#info" });

			Packet result = element.okResult(resultQuery, 0);

			if (roomJID.getLocalpart() == null) {
				Element resultIdentity = new Element("identity", new String[] { "category", "name", "type" }, new String[] {
						"conference", "Multi User Chat", "text" });
				resultQuery.addChild(resultIdentity);
				resultQuery.addChild(new Element("feature", new String[] { "var" },
						new String[] { "http://jabber.org/protocol/muc" }));
				final Set<String> features = this.muc.getFeaturesFromModule();
				if (features != null) {
					for (String featur : features) {
						resultQuery.addChild(new Element("feature", new String[] { "var" }, new String[] { featur }));
					}
				}
			} else {
				Room room = repository.getRoom(roomJID);
				if (room == null) {
					throw new MUCException(Authorization.ITEM_NOT_FOUND);
				}

				String roomName = room.getConfig().getRoomName();
				Element resultIdentity = new Element("identity", new String[] { "category", "name", "type" }, new String[] {
						"conference", roomName == null ? "" : roomName, "text" });
				resultQuery.addChild(resultIdentity);

				addFeature(resultQuery, "http://jabber.org/protocol/muc");

				switch (room.getConfig().getRoomAnonymity()) {
				case fullanonymous:
					addFeature(resultQuery, "muc_fullyanonymous");
					break;
				case semianonymous:
					addFeature(resultQuery, "muc_semianonymous");
					break;
				case nonanonymous:
					addFeature(resultQuery, "muc_nonanonymous");
					break;
				}

				if (room.getConfig().isRoomModerated()) {
					addFeature(resultQuery, "muc_moderated");
				} else {
					addFeature(resultQuery, "muc_unmoderated");
				}

				if (room.getConfig().isRoomMembersOnly()) {
					addFeature(resultQuery, "muc_membersonly");
				} else {
					addFeature(resultQuery, "muc_open");
				}

				if (room.getConfig().isPersistentRoom()) {
					addFeature(resultQuery, "muc_persistent");
				} else {
					addFeature(resultQuery, "muc_temporary");
				}

				if (!room.getConfig().isRoomconfigPublicroom()) {
					addFeature(resultQuery, "muc_hidden");
				} else {
					addFeature(resultQuery, "muc_public");
				}

				if (room.getConfig().isPasswordProtectedRoom()) {
					addFeature(resultQuery, "muc_passwordprotected");
				} else {
					addFeature(resultQuery, "muc_unsecured");
				}

			}

			writer.write(result);
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
