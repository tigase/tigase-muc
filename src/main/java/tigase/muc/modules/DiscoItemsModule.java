/*
 * DiscoItemsModule.java
 *
 * Tigase Jabber/XMPP Server
 * Copyright (C) 2004-2012 "Artur Hefczyc" <artur.hefczyc@tigase.org>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
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
import tigase.server.script.CommandIfc;

import tigase.util.TigaseStringprepException;

import tigase.xml.Element;

import tigase.xmpp.Authorization;
import tigase.xmpp.BareJID;
import tigase.xmpp.JID;

//~--- JDK imports ------------------------------------------------------------

import java.util.List;
import java.util.Map;

/**
 * @author bmalkow
 *
 */
public class DiscoItemsModule
				extends AbstractModule {
	private static final Criteria CRIT = ElementCriteria.nameType("iq",
																				 "get").add(ElementCriteria.name("query",
																					 "http://jabber.org/protocol/disco#items"));

	//~--- fields ---------------------------------------------------------------

	private final MUCComponent mucComponent;

	//~--- constructors ---------------------------------------------------------

	/**
	 * Constructs ...
	 *
	 *
	 * @param config
	 * @param writer
	 * @param mucRepository
	 * @param scriptCommands
	 * @param mucComponent
	 */
	public DiscoItemsModule(MucConfig config, ElementWriter writer,
													IMucRepository mucRepository,
													Map<String, CommandIfc> scriptCommands,
													MUCComponent mucComponent) {
		super(config, writer, mucRepository);
		this.mucComponent = mucComponent;
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
			final JID requestedJID = JID.jidInstance(element.getAttribute("to"));
			final String node      = element.getAttributeStaticStr(Iq.IQ_QUERY_PATH, "node");
			Element resultQuery    = new Element("query", new String[] { "xmlns" },
																 new String[] {
																	 "http://jabber.org/protocol/disco#items" });
			Packet result = element.okResult(resultQuery, 0);

			if ((node != null) && node.equals("http://jabber.org/protocol/commands")) {
				List<Element> items = mucComponent.getScriptItems(node, element.getStanzaTo(),
																element.getStanzaFrom());

				resultQuery.addChildren(items);
			} else if ((node == null) && (requestedJID.getLocalpart() == null) &&
								 (requestedJID.getResource() == null)) {

				// discovering rooms
				// (http://xmpp.org/extensions/xep-0045.html#disco-rooms)
				BareJID[] roomsId = repository.getPublicVisibleRoomsIdList();

				for (final BareJID jid : roomsId) {
					if (jid.getDomain().equals(requestedJID.getDomain())) {
						final String name = repository.getRoomName(jid.toString());

						resultQuery.addChild(new Element("item", new String[] { "jid", "name" },
																						 new String[] { jid.toString(), (name != null)
										? name
										: jid.getLocalpart() }));
					}
				}
			} else if ((node == null) && (requestedJID.getLocalpart() != null) &&
								 (requestedJID.getResource() == null)) {

				// querying for Room Items
				// (http://xmpp.org/extensions/xep-0045.html#disco-roomitems)
				Room room = repository.getRoom(requestedJID.getBareJID());

				if (room == null) {
					throw new MUCException(Authorization.ITEM_NOT_FOUND);
				}

				String nickname = room.getOccupantsNickname(element.getStanzaFrom());

				if (nickname == null) {
					throw new MUCException(Authorization.FORBIDDEN);
				}
				for (String nick : room.getOccupantsNicknames()) {
					resultQuery.addChild(new Element("item", new String[] { "jid", "name" },
																					 new String[] { room.getRoomJID() + "/" + nick,
									nick }));
				}
			} else if ((node == null) && (requestedJID.getLocalpart() != null) &&
								 (requestedJID.getResource() != null)) {

				// Querying a Room Occupant
				throw new MUCException(Authorization.BAD_REQUEST);
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


//~ Formatted in Tigase Code Convention on 13/02/16
