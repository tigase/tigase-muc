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
package tigase.muc;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import tigase.muc.modules.PresenceModule;
import tigase.muc.repository.IMucRepository;
import tigase.server.Packet;
import tigase.util.TigaseStringprepException;
import tigase.xml.Element;
import tigase.xmpp.JID;

/**
 * @author bmalkow
 * 
 */
public class Ghostbuster {

	private class KnownJID {

		private long lastActivity = Long.MIN_VALUE;

		private long lastPing = Long.MIN_VALUE;

	}

	private static final Set<String> intReasons = new HashSet<String>() {

		private static final long serialVersionUID = 1L;

		{
			add("gone");
			add("item-not-found");
			add("recipient-unavailable");
			add("redirect");
			add("remote-server-not-found");
			add("remote-server-timeout");
		}
	};

	public static final Set<String> R = Collections.unmodifiableSet(intReasons);

	private final Map<JID, KnownJID> knownJids = new ConcurrentHashMap<JID, KnownJID>();

	private IMucRepository mucRepository;

	private PresenceModule presenceModule;

	/**
	 * @param packet
	 */
	private boolean checkError(final Packet packet) {
		final Element errorElement = packet.getElement().getChild("error");
		if (errorElement == null)
			return false;

		boolean x = false;
		for (Element reason : errorElement.getChildren()) {
			if (reason.getXMLNS() == null || !reason.getXMLNS().equals("urn:ietf:params:xml:ns:xmpp-stanzas"))
				continue;

			if (Ghostbuster.R.contains(reason.getName())) {
				x = true;
				break;
			}
		}
		if (!x)
			return false;

		return true;
	}

	/**
	 * @param senderJID
	 */
	public void delete(final JID jid) {
		this.knownJids.remove(jid);
	}

	public IMucRepository getMucRepository() {
		return mucRepository;
	}

	public PresenceModule getPresenceModule() {
		return presenceModule;
	}

	/**
	 * @param packet
	 * @throws TigaseStringprepException
	 */
	private void processError(Packet packet) throws TigaseStringprepException {
		if (presenceModule == null || mucRepository == null)
			return;

		this.knownJids.remove(packet.getStanzaFrom());
		for (Room r : mucRepository.getActiveRooms().values()) {
			if (r.isOccupantInRoom(packet.getStanzaFrom())) {
				presenceModule.doQuit(r, packet.getStanzaFrom());
			}
		}
	}

	public void setMucRepository(IMucRepository mucRepository) {
		this.mucRepository = mucRepository;
	}

	public void setPresenceModule(PresenceModule presenceModule) {
		this.presenceModule = presenceModule;
	}

	public void update(Packet packet) throws TigaseStringprepException {
		if (packet.getStanzaFrom() == null || packet.getStanzaFrom().getResource() == null)
			return;

		final String type = packet.getElement().getAttribute("type");

		if (type != null && type.equals("error") && checkError(packet)) {
			processError(packet);
		} else if ("presence".equals(packet.getElemName()) && type != null && type.equals("unavailable")) {
			this.knownJids.remove(packet.getStanzaFrom());
		} else if ("presence".equals(packet.getElemName()) && (type == null || !type.equals("error"))) {
			if (!knownJids.containsKey(packet.getStanzaFrom())) {
				KnownJID k = new KnownJID();
				knownJids.put(packet.getStanzaFrom(), k);
			}
		}

		if (knownJids.containsKey(packet.getStanzaFrom())) {
			knownJids.get(packet.getStanzaFrom()).lastActivity = System.currentTimeMillis();
		}
	}

}
