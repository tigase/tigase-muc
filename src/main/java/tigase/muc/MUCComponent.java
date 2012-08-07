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

//~--- non-JDK imports --------------------------------------------------------

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import tigase.conf.Configurable;
import tigase.db.RepositoryFactory;
import tigase.db.UserRepository;
import tigase.disco.ServiceEntity;
import tigase.disco.ServiceIdentity;
import tigase.disco.XMPPService;
import tigase.muc.exceptions.MUCException;
import tigase.muc.history.HistoryManagerFactory;
import tigase.muc.history.HistoryProvider;
import tigase.muc.history.MemoryHistoryProvider;
import tigase.muc.logger.MucLogger;
import tigase.muc.modules.DiscoInfoModule;
import tigase.muc.modules.DiscoItemsModule;
import tigase.muc.modules.GroupchatMessageModule;
import tigase.muc.modules.MediatedInvitationModule;
import tigase.muc.modules.ModeratorModule;
import tigase.muc.modules.PresenceModule;
import tigase.muc.modules.PresenceModule.DelayDeliveryThread.DelDeliverySend;
import tigase.muc.modules.PrivateMessageModule;
import tigase.muc.modules.RoomConfigurationModule;
import tigase.muc.modules.SoftwareVersionModule;
import tigase.muc.modules.UniqueRoomNameModule;
import tigase.muc.modules.XmppPingModule;
import tigase.muc.repository.IMucRepository;
import tigase.muc.repository.MucDAO;
import tigase.muc.repository.inmemory.InMemoryMucRepository;
import tigase.server.AbstractMessageReceiver;
import tigase.server.DisableDisco;
import tigase.server.Packet;
import tigase.util.DNSResolver;
import tigase.util.TigaseStringprepException;
import tigase.xml.Element;
import tigase.xmpp.Authorization;
import tigase.xmpp.BareJID;
import tigase.xmpp.JID;
import tigase.xmpp.StanzaType;

//~--- classes ----------------------------------------------------------------

/**
 * Class description
 * 
 * 
 * @version 5.1.0, 2010.11.02 at 01:01:31 MDT
 * @author Artur Hefczyc <artur.hefczyc@tigase.org>
 */
public class MUCComponent extends AbstractMessageReceiver implements DelDeliverySend, XMPPService, Configurable, DisableDisco {

	/** Field description */
	public static final String ADMINS_KEY = "admins";
	public static final String LOG_DIR_KEY = "room-log-directory";
	public static final String MESSAGE_FILTER_ENABLED_KEY = "message-filter-enabled";
	public static final String MUC_ALLOW_CHAT_STATES_KEY = "muc-allow-chat-states";

	// ~--- fields
	// ---------------------------------------------------------------

	public static final String MUC_LOCK_NEW_ROOM_KEY = "muc-lock-new-room";
	protected static final String MUC_REPO_CLASS_PROP_KEY = "muc-repo-class";
	protected static final String MUC_REPO_URL_PROP_KEY = "muc-repo-url";
	public static final String PRESENCE_FILTER_ENABLED_KEY = "presence-filter-enabled";
	private MucConfig config = new MucConfig();
	private MucDAO dao;
	private HistoryProvider historyProvider;
	/** Field description */
	public String[] HOSTNAMES_PROP_VAL = { "localhost", "hostname" };
	protected Logger log = Logger.getLogger(this.getClass().getName());
	private GroupchatMessageModule messageModule;
	private final ModulesManager modulesManager = new ModulesManager();

	private IMucRepository mucRepository;

	// ~--- get methods
	// ----------------------------------------------------------

	private PresenceModule presenceModule;

	private MucLogger roomLogger;

	private ServiceEntity serviceEntity;

	private UserRepository userRepository;

	private final tigase.muc.ElementWriter writer;

	/**
	 * 
	 */
	public MUCComponent() {
		this.writer = new ElementWriter() {

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
	}

	public MUCComponent(ElementWriter writer) {
		this.writer = writer;
	}

	/**
	 * Method description
	 * 
	 * 
	 * @param params
	 * 
	 * @return
	 */
	@Override
	public Map<String, Object> getDefaults(Map<String, Object> params) {
		Map<String, Object> props = super.getDefaults(params);

		if (params.get(GEN_VIRT_HOSTS) != null) {
			HOSTNAMES_PROP_VAL = ((String) params.get(GEN_VIRT_HOSTS)).split(",");
		} else {
			HOSTNAMES_PROP_VAL = DNSResolver.getDefHostNames();
		}

		props.put(MESSAGE_FILTER_ENABLED_KEY, Boolean.TRUE);
		props.put(PRESENCE_FILTER_ENABLED_KEY, Boolean.FALSE);

		String[] hostnames = new String[HOSTNAMES_PROP_VAL.length];
		int i = 0;

		for (String host : HOSTNAMES_PROP_VAL) {
			hostnames[i++] = getName() + "." + host;
		}

		props.put(HOSTNAMES_PROP_KEY, hostnames);

		// By default use the same repository as all other components:
		String repo_class = (params.get(GEN_USER_DB) != null) ? (String) params.get(GEN_USER_DB) : DERBY_REPO_CLASS_PROP_VAL;
		String repo_uri = (params.get(GEN_USER_DB_URI) != null) ? (String) params.get(GEN_USER_DB_URI)
				: DERBY_REPO_URL_PROP_VAL;

		props.put(MUC_REPO_CLASS_PROP_KEY, repo_class);
		props.put(MUC_REPO_URL_PROP_KEY, repo_uri);

		props.put(HistoryManagerFactory.DB_CLASS_KEY, repo_class);
		props.put(HistoryManagerFactory.DB_URI_KEY, repo_uri);

		String[] admins;

		if (params.get(GEN_ADMINS) != null) {
			admins = ((String) params.get(GEN_ADMINS)).split(",");
		} else {
			admins = new String[] { "admin@" + getDefHostName() };
		}

		props.put(ADMINS_KEY, admins);
		props.put(LOG_DIR_KEY, new String("./logs/"));
		props.put(MUC_ALLOW_CHAT_STATES_KEY, Boolean.FALSE);
		props.put(MUC_LOCK_NEW_ROOM_KEY, Boolean.TRUE);

		return props;
	}

	/**
	 * Method description
	 * 
	 * 
	 * @return
	 */
	@Override
	public List<Element> getDiscoFeatures() {
		return null;
	}

	/**
	 * Method description
	 * 
	 * 
	 * @param node
	 * @param jid
	 * 
	 * @return
	 */
	@Override
	public Element getDiscoInfo(String node, JID jid) {
		return null;
	}

	/**
	 * Method description
	 * 
	 * 
	 * @param node
	 * @param jid
	 * 
	 * @return
	 */
	@Override
	public List<Element> getDiscoItems(String node, JID jid) {
		if (node == null) {
			Element result = serviceEntity.getDiscoItem(null, getName() + "." + jid.toString());

			return Arrays.asList(result);
		} else {
			return null;
		}
	}

	// ~--- methods
	// --------------------------------------------------------------

	/**
	 * Method description
	 * 
	 * 
	 * @return
	 */
	public Set<String> getFeaturesFromModule() {
		HashSet<String> result = new HashSet<String>();
		result.addAll(this.modulesManager.getFeatures());
		return result;
	}

	public IMucRepository getMucRepository() {
		return mucRepository;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see tigase.server.BasicComponent#getName()
	 */
	@Override
	public String getName() {
		return "muc";
	}

	protected void init() {
		System.out.println("INIT MUC");
		this.modulesManager.register(new PrivateMessageModule(this.config, writer, this.mucRepository));
		messageModule = this.modulesManager.register(new GroupchatMessageModule(this.config, writer, this.mucRepository,
				historyProvider, roomLogger));
		presenceModule = this.modulesManager.register(new PresenceModule(this.config, writer, this.mucRepository,
				this.historyProvider, this, roomLogger));
		this.modulesManager.register(new RoomConfigurationModule(this.config, writer, this.mucRepository, messageModule));
		this.modulesManager.register(new ModeratorModule(this.config, writer, this.mucRepository));
		this.modulesManager.register(new SoftwareVersionModule(writer));
		this.modulesManager.register(new XmppPingModule(writer));
		this.modulesManager.register(new DiscoItemsModule(this.config, writer, this.mucRepository, scriptCommands, this));
		this.modulesManager.register(new DiscoInfoModule(this.config, writer, this.mucRepository, this));
		this.modulesManager.register(new MediatedInvitationModule(this.config, writer, this.mucRepository));
		this.modulesManager.register(new UniqueRoomNameModule(this.config, writer, this.mucRepository));
	}

	/**
	 * @param packet
	 */
	private void processCommandPacket(Packet packet) {
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

	/**
	 * Method description
	 * 
	 * 
	 * @param packet
	 */
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
			boolean handled = this.modulesManager.process(packet, writer);

			if (!handled) {
				final String t = packet.getElement().getAttribute("type");
				final StanzaType type = t == null ? null : StanzaType.valueof(t);
				if (type != StanzaType.error) {
					throw new MUCException(Authorization.FEATURE_NOT_IMPLEMENTED);
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
			sendException(packet, new MUCException(Authorization.JID_MALFORMED));
		} catch (MUCException e) {
			if (log.isLoggable(Level.FINER)) {
				log.log(Level.FINER, "Exception thrown for " + packet.toString(), e);
			} else if (log.isLoggable(Level.FINE)) {
				log.log(Level.FINE, "PubSubException on stanza id=" + packet.getAttribute("id") + " " + e.getMessage());
			}
			sendException(packet, e);
		}
	}

	/**
	 * Method description
	 * 
	 * 
	 * @param packet
	 */
	@Override
	public void sendDelayedPacket(Packet packet) {
		addOutPacket(packet);
	}

	// ~--- set methods
	// ----------------------------------------------------------

	private void sendException(final Packet packet, final MUCException e) {
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
			writer.write(result);
		} catch (Exception e1) {
			if (log.isLoggable(Level.WARNING))
				log.log(Level.WARNING, "Problem during generate error response", e1);
		}
	}

	/**
	 * @param config2
	 */
	public void setConfig(MucConfig config2) {
		this.config = config2;
	}

	/**
	 * Method description
	 * 
	 * 
	 * @param mucRepository
	 */
	public void setMucRepository(IMucRepository mucRepository) {
		this.mucRepository = mucRepository;
	}

	// ~--- methods
	// --------------------------------------------------------------

	/**
	 * Method description
	 * 
	 * 
	 * @param props
	 */
	@Override
	public void setProperties(Map<String, Object> props) {
		super.setProperties(props);

		if (props.size() == 1) {
			// If props.size() == 1, it means this is a single property update
			// and this component does not support single property change for
			// the rest
			// of it's settings
			return;
		}

		// String[] hostnames = (String[]) props.get(HOSTNAMES_PROP_KEY);
		// if (hostnames == null || hostnames.length == 0) {
		// log.warning("Hostnames definition is empty, setting 'localhost'");
		// hostnames = new String[] { getName() + ".localhost" };
		// }
		// clearRoutings();
		// for (String host : hostnames) {
		// addRouting(host);
		// }
		serviceEntity = new ServiceEntity(getName(), null, "Multi User Chat");
		serviceEntity.addIdentities(new ServiceIdentity("conference", "text", "Multi User Chat"));
		serviceEntity.addFeatures("http://jabber.org/protocol/muc");
		this.config.init(props);

		try {
			this.historyProvider = HistoryManagerFactory.getHistoryManager(props);
			this.historyProvider.init(props);
		} catch (Exception e) {
			this.historyProvider = new MemoryHistoryProvider();
			throw new RuntimeException(e);
		}

		if (props.containsKey(MucLogger.MUC_LOGGER_CLASS_KEY)) {
			String loggerClassName = (String) props.get(MucLogger.MUC_LOGGER_CLASS_KEY);
			try {
				log.config("Using Room Logger: " + loggerClassName);
				this.roomLogger = (MucLogger) Class.forName(loggerClassName).newInstance();
				this.roomLogger.init(props);
			} catch (Exception e) {
				System.err.println("");
				System.err.println("  --------------------------------------");
				System.err.println("  ERROR! Terminating the server process.");
				System.err.println("  Problem initializing the MUC Component: " + e);
				System.err.println("  Please fix the problem and start the server again.");
				e.printStackTrace();
				System.exit(1);
			}
		}

		this.config.setPublicLoggingEnabled(this.roomLogger != null || this.historyProvider.isPersistent());
		log.config("Public Logging Allowed: " + this.config.isPublicLoggingEnabled());

		if (userRepository == null) {
			userRepository = (UserRepository) props.get(SHARED_USER_REPO_PROP_KEY);

			try {
				if (userRepository == null) {
					String cls_name = (String) props.get(MUC_REPO_CLASS_PROP_KEY);
					String res_uri = (String) props.get(MUC_REPO_URL_PROP_KEY);

					this.userRepository = RepositoryFactory.getUserRepository(cls_name, res_uri, null);
					userRepository.initRepository(res_uri, null);
				}

				dao = new MucDAO(this.config, this.userRepository);
				mucRepository = new InMemoryMucRepository(this.config, dao);
			} catch (Exception e) {
				log.severe("Can't initialize MUC repository: " + e);
				e.printStackTrace();

				// System.exit(1);
			}

			init();
		}

		this.messageModule.setChatStateAllowed((Boolean) props.get(MUC_ALLOW_CHAT_STATES_KEY));
		this.presenceModule.setLockNewRoom((Boolean) props.get(MUC_LOCK_NEW_ROOM_KEY));
		log.info("Tigase MUC Component ver. " + MucVersion.getVersion() + " started.");
	}

}

// ~ Formatted in Sun Code Convention

// ~ Formatted by Jindent --- http://www.jindent.com
