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
package tigase.muc.modules.selfping;

import tigase.component.exceptions.ComponentException;
import tigase.component.modules.impl.XmppPingModule;
import tigase.component.responses.AsyncCallback;
import tigase.kernel.beans.Bean;
import tigase.kernel.beans.Initializable;
import tigase.kernel.beans.Inject;
import tigase.muc.Room;
import tigase.muc.exceptions.MUCException;
import tigase.muc.repository.IMucRepository;
import tigase.server.Packet;
import tigase.util.stringprep.TigaseStringprepException;
import tigase.xml.Element;
import tigase.xmpp.Authorization;
import tigase.xmpp.jid.BareJID;
import tigase.xmpp.jid.JID;

import java.util.Collection;
import java.util.logging.Level;

@Bean(name = "urn:xmpp:ping", active = true)
public class SelfPingModule
		extends XmppPingModule
		implements Initializable {

	private static long idCounter;

	@Inject
	private SelfPingerMonitor pingMonitor;

	@Inject
	private IMucRepository repository;

	public SelfPingModule() {
	}

	public void process(final Packet packet) throws ComponentException {
		try {
			final JID senderJID = packet.getStanzaFrom();
			final BareJID roomJID = packet.getStanzaTo().getBareJID();
			final String recipientNickname = packet.getStanzaTo().getResource();

			if (roomJID.getLocalpart() == null) {
				// process module ping
				super.process(packet);
				return;
			}

			if (recipientNickname == null) {
				// process room ping
				super.process(packet);
				return;
			}

			final Room room = repository.getRoom(roomJID);
			if (room == null) {
				throw new MUCException(Authorization.ITEM_NOT_FOUND, "Room not found");
			}

			final Collection<JID> recipientJids = room.getOccupantsJidsByNickname(recipientNickname);
			if (recipientJids.isEmpty()) {
				throw new SelfPingException(Authorization.NOT_ACCEPTABLE, room.getRoomJID().toString(),
											recipientNickname + " is not in room");
			}

			pingAll(packet.getStanzaId(), packet.getStanzaFrom(), packet.getStanzaTo(), recipientJids);

		} catch (ComponentException e1) {
			throw e1;
//		} catch (TigaseStringprepException e) {
//			throw new MUCException(Authorization.BAD_REQUEST);
		} catch (Exception e) {
			log.log(Level.FINEST, "Error during forwarding IQ", e);
			throw new RuntimeException(e);
		}

//		Packet reposnse = iq.okResult((Element)null, 0);
//		this.write(reposnse);
	}

	@Override
	public void initialize() {
		pingMonitor.setHandler(this::onPingMultirequestFinished);
	}

	protected String nextStanzaId() {
		final String id = "spng-" + (++idCounter);
		return id;
	}

	protected Packet createPingPacket(String id, String from, String to) throws TigaseStringprepException {
		Element ping = new Element("iq", new String[]{"type", "id", "from", "to"}, new String[]{"get", id, from, to});

		ping.addChild(new Element("ping", new String[]{"xmlns"}, new String[]{"urn:xmpp:ping"}));

		Packet packet = Packet.packetInstance(ping);
		packet.setXMLNS(Packet.CLIENT_XMLNS);

		return packet;
	}

	private Element errorElement(Authorization auth, String message) {
		final Element error = new Element("error", new String[]{"type"}, new String[]{auth.getErrorType()});
		error.addChild(new Element(auth.getCondition(), new String[]{"xmlns"}, new String[]{Packet.ERROR_NS}));
		return error;
	}

	private void onPingMultirequestFinished(Request req, SelfPingerMonitor.ResultStatus resultStatus) {
		try {
			final Element ping = new Element("iq", new String[]{"id", "from", "to"},
											 new String[]{req.getId(), req.getJidTo().toString(),
														  req.getJid().toString()});

			switch (resultStatus) {
				case AllSuccess:
					ping.setAttribute("type", "result");
					break;
				case Errors:
					ping.setAttribute("type", "error");
					ping.addChild(
							errorElement(Authorization.SERVICE_UNAVAILABLE, "Some clients responded with error."));
					break;
				case Timeouts:
					ping.setAttribute("type", "error");
					ping.addChild(errorElement(Authorization.SERVICE_UNAVAILABLE, "Some clients not responded."));
					break;
			}
			Packet packet = Packet.packetInstance(ping);
			packet.setXMLNS(Packet.CLIENT_XMLNS);
			write(packet);
		} catch (Exception e) {
			log.log(Level.WARNING, "Cannot send ping response", e);
		}
	}

	private void pingAll(String stanzaId, final JID from, final JID to, final Collection<JID> recipientJids) {
		final Request request = this.pingMonitor.register(from, to, stanzaId);
		recipientJids.forEach(jid -> {
			try {
				final String id = nextStanzaId();
				Packet pingPacket = createPingPacket(id, to.toString(), jid.toString());
				request.registerRequest(jid, id);
				this.write(pingPacket, new PongCallback(jid, id));
			} catch (Exception e) {
				log.log(Level.WARNING, "Problem when pinging", e);
			}

		});
	}

	private class PongCallback
			implements AsyncCallback {

		private final String id;
		private final JID jid;

		PongCallback(JID jid, String id) {
			this.jid = jid;
			this.id = id;
		}

		@Override
		public void onError(Packet responseStanza, String errorCondition) {
			pingMonitor.registerResponse(responseStanza.getFrom(), responseStanza.getStanzaId(), Request.Result.Error);
		}

		@Override
		public void onSuccess(Packet responseStanza) {
			pingMonitor.registerResponse(responseStanza.getFrom(), responseStanza.getStanzaId(), Request.Result.Ok);
		}

		@Override
		public void onTimeout() {
			pingMonitor.registerResponse(jid, id, Request.Result.Timeout);
		}
	}

}
