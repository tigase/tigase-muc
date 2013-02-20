/*
 * DiscoInfoModule.java
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

//~--- non-JDK imports --------------------------------------------------------

import tigase.component.ElementWriter;

import tigase.criteria.Criteria;
import tigase.criteria.ElementCriteria;

import tigase.muc.exceptions.MUCException;
import tigase.muc.MUCComponent;
import tigase.muc.MucConfig;
import tigase.muc.repository.IMucRepository;
import tigase.muc.Room;

import tigase.server.Iq;
import tigase.server.Packet;

import tigase.util.TigaseStringprepException;

import tigase.xml.Element;

import tigase.xmpp.Authorization;
import tigase.xmpp.JID;

//~--- JDK imports ------------------------------------------------------------

import java.util.Set;

/**
 * @author bmalkow
 *
 */
public class DiscoInfoModule
				extends AbstractModule {
	private static final Criteria CRIT = ElementCriteria.nameType("iq",
																				 "get").add(ElementCriteria.name("query",
																					 "http://jabber.org/protocol/disco#info"));

	//~--- fields ---------------------------------------------------------------

	private final MUCComponent muc;

	//~--- constructors ---------------------------------------------------------

	/**
	 * Constructs ...
	 *
	 *
	 * @param config
	 * @param writer
	 * @param mucRepository
	 * @param component
	 */
	public DiscoInfoModule(MucConfig config, ElementWriter writer,
												 IMucRepository mucRepository, final MUCComponent component) {
		super(config, writer, mucRepository);
		this.muc = component;
	}

	//~--- methods --------------------------------------------------------------

	private static void addFeature(Element query, String feature) {
		query.addChild(new Element("feature", new String[] { "var" },
															 new String[] { feature }));
	}

	//~--- get methods ----------------------------------------------------------

	/**
	 * Method description
	 *
	 *
	 * @return
	 */
	@Override
	public String[] getFeatures() {
		return null;
	}

	/**
	 * Method description
	 *
	 *
	 * @return
	 */
	@Override
	public Criteria getModuleCriteria() {
		return CRIT;
	}

	//~--- methods --------------------------------------------------------------

	/**
	 * Method description
	 *
	 *
	 * @param element
	 *
	 * @throws MUCException
	 */
	@Override
	public void process(Packet element) throws MUCException {
		try {
			String toXML           = element.getAttributeStaticStr(Packet.TO_ATT);
			final String node      = element.getAttributeStaticStr(Iq.IQ_QUERY_PATH, "node");
			final JID requestedJID = JID.jidInstance(toXML);
			Element resultQuery    = new Element("query", new String[] { "xmlns" },
																 new String[] {
																	 "http://jabber.org/protocol/disco#info" });
			Packet result = element.okResult(resultQuery, 0);

			if ((node == null) && (requestedJID.getLocalpart() == null) &&
					(requestedJID.getResource() == null)) {
				Element resultIdentity = new Element("identity", new String[] { "category",
								"name", Packet.TYPE_ATT }, new String[] { "conference", "Multi User Chat",
								"text" });

				resultQuery.addChild(resultIdentity);
				resultQuery.addChild(new Element("feature", new String[] { "var" },
																				 new String[] {
																					 "http://jabber.org/protocol/muc" }));
				resultQuery.addChild(new Element("feature", new String[] { "var" },
																				 new String[] {
																					 "http://jabber.org/protocol/commands" }));

				final Set<String> features = this.muc.getFeaturesFromModule();

				if (features != null) {
					for (String featur : features) {
						resultQuery.addChild(new Element("feature", new String[] { "var" },
																						 new String[] { featur }));
					}
				}
			} else if ((node == null) && (requestedJID.getLocalpart() != null) &&
								 (requestedJID.getResource() == null)) {
				Room room = repository.getRoom(requestedJID.getBareJID());

				if (room == null) {
					throw new MUCException(Authorization.ITEM_NOT_FOUND);
				}

				String roomName        = room.getConfig().getRoomName();
				Element resultIdentity = new Element("identity", new String[] { "category",
								"name", "type" }, new String[] { "conference", (roomName == null)
								? ""
								: roomName, "text" });

				resultQuery.addChild(resultIdentity);
				addFeature(resultQuery, "http://jabber.org/protocol/muc");
				switch (room.getConfig().getRoomAnonymity()) {
				case fullanonymous :
					addFeature(resultQuery, "muc_fullyanonymous");

					break;

				case semianonymous :
					addFeature(resultQuery, "muc_semianonymous");

					break;

				case nonanonymous :
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
			} else if ((node == null) && (requestedJID.getLocalpart() != null) &&
								 (requestedJID.getResource() != null)) {

				// throw new MUCException(Authorization.BAD_REQUEST);
			} else {
				throw new MUCException(Authorization.BAD_REQUEST);
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


//~ Formatted in Tigase Code Convention on 13/02/20
