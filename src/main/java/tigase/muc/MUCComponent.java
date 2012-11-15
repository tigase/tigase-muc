/*
 * Tigase Jabber/XMPP Multi-User Chat Component
 * Copyright (C) 2008 "Bartosz M. Małkowski" <bartosz.malkowski@tigase.org>
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
 * $Rev$
 * Last modified by $Author$
 * $Date$
 */

package tigase.muc;

//~--- non-JDK imports --------------------------------------------------------

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.script.Bindings;

import tigase.component.AbstractComponent;
import tigase.component.ElementWriter;
import tigase.conf.Configurable;
import tigase.db.RepositoryFactory;
import tigase.db.UserRepository;
import tigase.disco.ServiceEntity;
import tigase.disco.ServiceIdentity;
import tigase.disco.XMPPService;
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
import tigase.server.DisableDisco;
import tigase.server.Packet;
import tigase.server.ReceiverTimeoutHandler;
import tigase.util.DNSResolver;
import tigase.xml.Element;
import tigase.xmpp.JID;

//~--- classes ----------------------------------------------------------------

/**
 * Class description
 * 
 * 
 * @version 5.1.0, 2010.11.02 at 01:01:31 MDT
 * @author Artur Hefczyc <artur.hefczyc@tigase.org>
 */
public class MUCComponent extends AbstractComponent<MucConfig> implements DelDeliverySend, XMPPService, Configurable,
		DisableDisco {

	/** Field description */
	public static final String ADMINS_KEY = "admins";
	public static final String LOG_DIR_KEY = "room-log-directory";
	public static final String MESSAGE_FILTER_ENABLED_KEY = "message-filter-enabled";
	public static final String MUC_ALLOW_CHAT_STATES_KEY = "muc-allow-chat-states";
	public static final String MUC_LOCK_NEW_ROOM_KEY = "muc-lock-new-room";
	protected static final String MUC_REPO_CLASS_PROP_KEY = "muc-repo-class";
	protected static final String MUC_REPO_URL_PROP_KEY = "muc-repo-url";
	private static final String MUC_REPOSITORY_VAR = "mucRepository";
	private static final String OWNER_MODULE_VAR = "ownerModule";
	public static final String PING_EVERY_MINUTE_KEY = "ping-every-minute";
	public static final String PRESENCE_FILTER_ENABLED_KEY = "presence-filter-enabled";
	private static final String PRESENCE_MODULE_VAR = "presenceModule";

	private MucDAO dao;

	private final Ghostbuster ghostbuster;

	private HistoryProvider historyProvider;

	/** Field description */
	public String[] HOSTNAMES_PROP_VAL = { "localhost", "hostname" };

	protected Logger log = Logger.getLogger(this.getClass().getName());

	private GroupchatMessageModule messageModule;

	private IMucRepository mucRepository;

	private RoomConfigurationModule ownerModule;

	private boolean pingEveryMinute = false;

	private PresenceModule presenceModule;

	private MucLogger roomLogger;

	private ServiceEntity serviceEntity;

	private UserRepository userRepository;

	/**
	 * 
	 */
	public MUCComponent() {
		super();
		this.ghostbuster = new Ghostbuster(this);
	}

	public MUCComponent(ElementWriter writer) {
		super(writer);
		this.ghostbuster = new Ghostbuster(this);
	}

	boolean addOutPacket(Packet packet, ReceiverTimeoutHandler handler, long delay, TimeUnit unit) {
		return super.addOutPacketWithTimeout(packet, handler, delay, unit);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * tigase.component.AbstractComponent#createComponentConfigInstance(tigase
	 * .component.AbstractComponent)
	 */
	@Override
	protected MucConfig createComponentConfigInstance(AbstractComponent<?> abstractComponent) {
		return new MucConfig(abstractComponent);
	}

	@Override
	public synchronized void everyHour() {
		super.everyHour();
		if (!pingEveryMinute)
			executePingInThread();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see tigase.server.AbstractMessageReceiver#everyMinute()
	 */
	@Override
	public synchronized void everyMinute() {
		super.everyMinute();
		if (pingEveryMinute)
			executePingInThread();
	}

	private void executePingInThread() {
		if (ghostbuster != null) {
			(new Thread() {
				@Override
				public void run() {
					try {
						ghostbuster.ping();
					} catch (Exception e) {
						log.log(Level.SEVERE, "Can't ping known JIDs", e);
					}

				}
			}).start();
		}
	}

	public MucConfig getConfig() {
		return componentConfig;
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
		props.put(PING_EVERY_MINUTE_KEY, Boolean.FALSE);

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

	protected void init() {
		final ElementWriter writer = getWriter();

		presenceModule = new PresenceModule(this.componentConfig, writer, this.mucRepository, this.historyProvider, this,
				roomLogger);

		ghostbuster.setPresenceModule(presenceModule);

		this.modulesManager.register(new PrivateMessageModule(this.componentConfig, writer, this.mucRepository));
		messageModule = this.modulesManager.register(new GroupchatMessageModule(this.componentConfig, writer,
				this.mucRepository, historyProvider, roomLogger));
		this.modulesManager.register(presenceModule);
		ownerModule = this.modulesManager.register(new RoomConfigurationModule(this.componentConfig, writer,
				this.mucRepository, this.historyProvider, messageModule));
		this.modulesManager.register(new ModeratorModule(this.componentConfig, writer, this.mucRepository));
		this.modulesManager.register(new SoftwareVersionModule(writer));
		this.modulesManager.register(new XmppPingModule(writer));
		this.modulesManager.register(new DiscoItemsModule(this.componentConfig, writer, this.mucRepository, scriptCommands,
				this));
		this.modulesManager.register(new DiscoInfoModule(this.componentConfig, writer, this.mucRepository, this));
		this.modulesManager.register(new MediatedInvitationModule(this.componentConfig, writer, this.mucRepository));
		this.modulesManager.register(new UniqueRoomNameModule(this.componentConfig, writer, this.mucRepository));
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see tigase.server.BasicComponent#initBindings(javax.script.Bindings)
	 */
	@Override
	public void initBindings(Bindings binds) {
		super.initBindings(binds);
		binds.put(PRESENCE_MODULE_VAR, presenceModule);
		binds.put(OWNER_MODULE_VAR, ownerModule);
		binds.put(MUC_REPOSITORY_VAR, mucRepository);
	}

        @Override
        public boolean isSubdomain() {
                return true;
        }
        
        
	@Override
	public int processingInThreads() {
		return Runtime.getRuntime().availableProcessors();
	}

	@Override
	public int processingOutThreads() {
		return Runtime.getRuntime().availableProcessors();
	}

	@Override
	protected void processStanzaPacket(final Packet packet) {
		try {
			ghostbuster.update(packet);
		} catch (Exception e) {
			log.log(Level.WARNING, "There is no Dana, there is only Zuul", e);
		}
		super.processStanzaPacket(packet);
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

	// ~--- methods
	// --------------------------------------------------------------

	/**
	 * Method description
	 * 
	 * 
	 * @param mucRepository
	 */
	public void setMucRepository(IMucRepository mucRepository) {
		this.mucRepository = mucRepository;
	}

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

		this.pingEveryMinute = (Boolean) props.get(PING_EVERY_MINUTE_KEY);

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
				if (log.isLoggable(Level.CONFIG))
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

		this.componentConfig.setPublicLoggingEnabled(this.roomLogger != null || this.historyProvider.isPersistent());
		if (log.isLoggable(Level.CONFIG))
			log.config("Public Logging Allowed: " + this.componentConfig.isPublicLoggingEnabled());

		if (userRepository == null) {
			userRepository = (UserRepository) props.get(SHARED_USER_REPO_PROP_KEY);

			try {
				if (userRepository == null) {
					String cls_name = (String) props.get(MUC_REPO_CLASS_PROP_KEY);
					String res_uri = (String) props.get(MUC_REPO_URL_PROP_KEY);

					this.userRepository = RepositoryFactory.getUserRepository(cls_name, res_uri, null);
					userRepository.initRepository(res_uri, null);
				}

				dao = new MucDAO(this.componentConfig, this.userRepository);
				mucRepository = new InMemoryMucRepository(this.componentConfig, dao);
			} catch (Exception e) {
				if (log.isLoggable(Level.SEVERE))
					log.severe("Can't initialize MUC repository: " + e);
				e.printStackTrace();

				// System.exit(1);
			}

			init();
		}

		this.messageModule.setChatStateAllowed((Boolean) props.get(MUC_ALLOW_CHAT_STATES_KEY));
		this.presenceModule.setLockNewRoom((Boolean) props.get(MUC_LOCK_NEW_ROOM_KEY));
		if (log.isLoggable(Level.INFO))
			log.info("Tigase MUC Component ver. " + MucVersion.getVersion() + " started.");
	}

}
