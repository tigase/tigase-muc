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

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

import tigase.criteria.Criteria;
import tigase.muc.ElementWriter;
import tigase.muc.MucConfig;
import tigase.muc.Room;
import tigase.muc.exceptions.MUCException;
import tigase.muc.repository.IMucRepository;
import tigase.muc.repository.RepositoryException;
import tigase.server.Packet;
import tigase.util.TigaseStringprepException;
import tigase.xml.Element;
import tigase.xmpp.BareJID;
import tigase.xmpp.JID;

/**
 * @author bmalkow
 * 
 */
public class GhostUsersModule extends AbstractModule {

	private final Criteria criteria;

	private final Set<String> HANDLED_ERRORS;

	private final PresenceModule presenceModule;

	/**
	 * @param config
	 * @param writer
	 * @param mucRepository
	 * @param presenceModule
	 */
	public GhostUsersModule(MucConfig config, ElementWriter writer, IMucRepository mucRepository, PresenceModule presenceModule) {
		super(config, writer, mucRepository);
		this.presenceModule = presenceModule;
		HashSet<String> s = new HashSet<String>();
		s.add("gone");
		s.add("item-not-found");
		s.add("recipient-unavailable");
		s.add("redirect");
		s.add("remote-server-not-found");
		s.add("remote-server-timeout");
		this.HANDLED_ERRORS = Collections.unmodifiableSet(s);
		this.criteria = new Criteria() {

			private Criteria nextCriteria;

			@Override
			public Criteria add(Criteria criteria) {
				if (this.nextCriteria == null) {
					this.nextCriteria = criteria;
				} else {
					Criteria c = this.nextCriteria;
					c.add(criteria);
				}
				return this;
			}

			@Override
			public boolean match(Element element) {
				try {
					boolean result = isProcessingByModule(element);

					if (result && this.nextCriteria != null) {
						List<Element> children = element.getChildren();
						boolean subres = false;
						if (children != null) {
							for (Element sub : children) {
								if (this.nextCriteria.match(sub)) {
									subres = true;
									break;
								}
							}
						}
						result &= subres;
					}

					return result;
				} catch (Exception e) {
					if (log.isLoggable(Level.WARNING))
						log.log(Level.WARNING, "Problem on checking stanza", e);
					return false;
				}

			}
		};
	}

	@Override
	public String[] getFeatures() {
		return null;
	}

	@Override
	public Criteria getModuleCriteria() {
		return criteria;
	}

	/**
	 * @param element
	 * @return
	 * @throws TigaseStringprepException
	 * @throws MUCException
	 * @throws RepositoryException
	 */
	protected boolean isProcessingByModule(final Element element) throws TigaseStringprepException, RepositoryException,
			MUCException {
		final String stanzaType = element.getAttribute("type");
		if (stanzaType == null || !stanzaType.equals("error"))
			return false;

		final Element errorElement = element.getChild("error");
		if (errorElement == null)
			return false;

		boolean x = false;
		for (Element reason : errorElement.getChildren()) {
			if (reason.getXMLNS() == null || !reason.getXMLNS().equals("urn:ietf:params:xml:ns:xmpp-stanzas"))
				continue;

			if (HANDLED_ERRORS.contains(reason.getName())) {
				x = true;
				break;
			}
		}
		if (!x)
			return false;

		final JID senderJID = JID.jidInstance(element.getAttribute("from"));
		final BareJID roomJID = BareJID.bareJIDInstance(element.getAttribute("to"));

		final Room room = repository.getRoom(roomJID);
		if (room == null)
			return false;

		return room.isOccupantInRoom(senderJID);
	}

	@Override
	public void process(Packet packet) throws MUCException, TigaseStringprepException {
		final JID senderJID = packet.getStanzaFrom();
		final BareJID roomJID = packet.getStanzaTo().getBareJID();
		try {
			final Room room = repository.getRoom(roomJID);

			presenceModule.doQuit(room, senderJID);

		} catch (MUCException e) {
			throw e;
		} catch (TigaseStringprepException e) {
			throw e;
		} catch (RepositoryException e) {
			throw new RuntimeException(e);
		}
	}

}
