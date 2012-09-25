package tigase.component;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Queue;
import java.util.logging.Level;
import java.util.logging.Logger;

import tigase.component.exceptions.ComponentException;
import tigase.component.modules.ModulesManager;
import tigase.disco.XMPPService;
import tigase.server.AbstractMessageReceiver;
import tigase.server.Packet;
import tigase.util.TigaseStringprepException;
import tigase.xml.Element;
import tigase.xmpp.Authorization;
import tigase.xmpp.BareJID;
import tigase.xmpp.StanzaType;

public abstract class AbstractComponent extends AbstractMessageReceiver implements XMPPService {

	private final ElementWriter DEFAULT_WRITER = new ElementWriter() {

		@Override
		public void write(Collection<Packet> elements) {
			if (elements != null) {
				for (Packet element : elements) {
					if (element != null) {
						write(element);
					}
				}
			}
		}

		@Override
		public void write(Packet packet) {
			if (log.isLoggable(Level.FINER))
				log.finer("Sent: " + packet.getElement());
			addOutPacket(packet);
		}

		@Override
		public void writeElement(Collection<Element> elements) {
			if (elements != null) {
				for (Element element : elements) {
					if (element != null) {
						writeElement(element);
					}
				}
			}
		}

		@Override
		public void writeElement(final Element element) {
			if (element != null) {
				try {
					if (log.isLoggable(Level.FINER))
						log.finer("Sent: " + element);
					addOutPacket(Packet.packetInstance(element));
				} catch (TigaseStringprepException e) {
				}
			}
		}
	};

	protected final Logger log = Logger.getLogger(this.getClass().getName());

	protected final ModulesManager modulesManager = new ModulesManager();

	private ElementWriter writer;

	public AbstractComponent() {
		this(null);
	}

	public AbstractComponent(ElementWriter writer) {
		this.writer = writer != null ? writer : DEFAULT_WRITER;
	}

	protected ElementWriter getWriter() {
		return writer;
	};

	/**
	 * @param packet
	 */
	protected void processCommandPacket(Packet packet) {
		Queue<Packet> results = new ArrayDeque<Packet>();

		processScriptCommand(packet, results);

		if (results.size() > 0) {
			for (Packet res : results) {

				// No more recurrential calls!!
				addOutPacketNB(res);

				// processPacket(res);
			} // end of for ()
		}
	}

	@Override
	public void processPacket(Packet packet) {
		if (packet.isCommand()) {
			processCommandPacket(packet);
		} else {
			processStanzaPacket(packet);
		}
	}

	protected void processStanzaPacket(final Packet packet) {
		try {

			boolean handled = this.modulesManager.process(packet, getWriter());

			if (!handled) {
				final String t = packet.getElement().getAttribute("type");
				final StanzaType type = t == null ? null : StanzaType.valueof(t);
				if (type != StanzaType.error) {
					throw new ComponentException(Authorization.FEATURE_NOT_IMPLEMENTED);
				} else {
					if (log.isLoggable(Level.FINER))
						log.finer(packet.getElemName() + " stanza with type='error' ignored");
				}
			}
		} catch (TigaseStringprepException e) {
			if (log.isLoggable(Level.FINER)) {
				log.log(Level.FINER, "Exception thrown for " + packet.toString(), e);
			} else if (log.isLoggable(Level.FINE)) {
				log.log(Level.FINE, "PubSubException on stanza id=" + packet.getAttribute("id") + " " + e.getMessage());
			}
			sendException(packet, new ComponentException(Authorization.JID_MALFORMED));
		} catch (ComponentException e) {
			if (log.isLoggable(Level.FINER)) {
				log.log(Level.FINER, "Exception thrown for " + packet.toString(), e);
			} else if (log.isLoggable(Level.FINE)) {
				log.log(Level.FINE, "PubSubException on stanza id=" + packet.getAttribute("id") + " " + e.getMessage());
			}
			sendException(packet, e);
		}
	}

	protected void sendException(final Packet packet, final ComponentException e) {
		try {

			final String t = packet.getElement().getAttribute("type");
			if (t != null && t == "error") {
				if (log.isLoggable(Level.FINER))
					log.finer(packet.getElemName() + " stanza already with type='error' ignored");
				return;
			}

			Packet result = e.makeElement(packet, true);
			Element el = result.getElement();
			el.setAttribute("from", BareJID.bareJIDInstance(el.getAttribute("from")).toString());
			if (log.isLoggable(Level.FINEST)) {
				log.log(Level.FINEST, "Sending back: " + result.toString());
			}
			getWriter().write(result);
		} catch (Exception e1) {
			if (log.isLoggable(Level.WARNING))
				log.log(Level.WARNING, "Problem during generate error response", e1);
		}
	}

}
