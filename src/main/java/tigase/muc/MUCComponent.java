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

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;

import tigase.component.AbstractComponent;
import tigase.component.AbstractComponent.ModuleRegisteredHandler;
import tigase.component.PacketWriter;
import tigase.component.adhoc.AbstractAdHocCommandModule;
import tigase.component.eventbus.DefaultEventBus;
import tigase.component.eventbus.EventBus;
import tigase.component.exceptions.RepositoryException;
import tigase.component.modules.Module;
import tigase.component.modules.ModuleProvider;
import tigase.component.modules.impl.JabberVersionModule;
import tigase.component.modules.impl.XmppPingModule;
import tigase.db.RepositoryFactory;
import tigase.db.UserRepository;
import tigase.form.Field;
import tigase.muc.history.HistoryManagerFactory;
import tigase.muc.history.HistoryProvider;
import tigase.muc.logger.MucLogger;
import tigase.muc.modules.AdHocCommandModule;
import tigase.muc.modules.DiscoveryModule;
import tigase.muc.modules.GroupchatMessageModule;
import tigase.muc.modules.IqStanzaForwarderModule;
import tigase.muc.modules.MediatedInvitationModule;
import tigase.muc.modules.ModeratorModule;
import tigase.muc.modules.PresenceModule;
import tigase.muc.modules.PresenceModuleImpl;
import tigase.muc.modules.PrivateMessageModule;
import tigase.muc.modules.RoomConfigurationModule;
import tigase.muc.modules.UniqueRoomNameModule;
import tigase.muc.repository.IMucRepository;
import tigase.muc.repository.MucDAO;
import tigase.muc.repository.inmemory.InMemoryMucRepository;
import tigase.server.Packet;
import tigase.xmpp.BareJID;

public class MUCComponent extends AbstractComponent<MucContext> implements ModuleRegisteredHandler {

	public static final String DEFAULT_ROOM_CONFIG_PREFIX_KEY = "default_room_config/";

	public static final String LOG_DIR_KEY = "room-log-directory";

	public static final String MESSAGE_FILTER_ENABLED_KEY = "message-filter-enabled";

	public static final String MUC_ALLOW_CHAT_STATES_KEY = "muc-allow-chat-states";

	public static final String MUC_LOCK_NEW_ROOM_KEY = "muc-lock-new-room";

	public static final String MUC_MULTI_ITEM_ALLOWED_KEY = "muc-multi-item-allowed";

	protected static final String MUC_REPO_CLASS_PROP_KEY = "muc-repo-class";

	protected static final String MUC_REPO_URL_PROP_KEY = "muc-repo-url";

	/**
	 * 
	 * @deprecated Use
	 *             {@linkplain CopyOfMUCComponent#SEARCH_GHOSTS_EVERY_MINUTE_KEY
	 *             SEARCH_GHOSTS_MINUTE_KEY} instead.
	 */
	@Deprecated
	public static final String PING_EVERY_MINUTE_KEY = "ping-every-minute";

	public static final String PRESENCE_FILTER_ENABLED_KEY = "presence-filter-enabled";

	public static final String SEARCH_GHOSTS_EVERY_MINUTE_KEY = "search-ghosts-every-minute";

	protected String chatLoggingDirectory;

	protected Boolean chatStateAllowed;

	protected EventBus eventBus = new DefaultEventBus();

	protected Ghostbuster2 ghostbuster;

	protected HistoryProvider historyProvider;

	protected boolean messageFilterEnabled;

	protected MucLogger mucLogger;

	protected IMucRepository mucRepository;

	protected boolean multiItemMode;

	protected Boolean newRoomLocked;

	protected boolean presenceFilterEnabled;

	protected boolean publicLoggingEnabled;

	protected boolean searchGhostsEveryMinute = false;

	protected PacketWriter writer = new PacketWriter() {
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
			if (log.isLoggable(Level.FINER)) {
				log.finer("Sent: " + packet.getElement());
			}
			addOutPacket(packet);
		}

	};

	public MUCComponent() {
		this.ghostbuster = new Ghostbuster2(this);
		addModuleRegisteredHandler(this);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see tigase.component.AbstractComponent#createContext()
	 */
	@Override
	protected MucContext createContext() {
		return new MucContext() {

			private final BareJID serviceName = BareJID.bareJIDInstanceNS("multi-user-chat");

			@Override
			public String getChatLoggingDirectory() {
				return MUCComponent.this.chatLoggingDirectory;
			}

			@Override
			public String getComponentCategory() {
				return MUCComponent.this.getDiscoCategory();
			}

			@Override
			public String getComponentName() {
				return MUCComponent.this.getDiscoDescription();
			}

			@Override
			public String getComponentType() {
				return MUCComponent.this.getDiscoCategoryType();
			}

			@Override
			public String getComponentVersion() {
				return MUCComponent.this.getComponentVersion();
			}

			@Override
			public EventBus getEventBus() {
				return MUCComponent.this.eventBus;
			}

			@Override
			public Ghostbuster2 getGhostbuster() {
				return MUCComponent.this.ghostbuster;
			}

			@Override
			public HistoryProvider getHistoryProvider() {
				return MUCComponent.this.historyProvider;
			}

			@Override
			public ModuleProvider getModuleProvider() {
				return MUCComponent.this.modulesManager;
			}

			@Override
			public MucLogger getMucLogger() {
				return MUCComponent.this.mucLogger;
			}

			@Override
			public IMucRepository getMucRepository() {
				return MUCComponent.this.mucRepository;
			}

			@Override
			public BareJID getServiceName() {
				return serviceName;
			}

			@Override
			public PacketWriter getWriter() {
				return MUCComponent.this.writer;
			}

			@Override
			public boolean isChatStateAllowed() {
				return MUCComponent.this.chatStateAllowed;
			}

			@Override
			public boolean isMessageFilterEnabled() {
				return MUCComponent.this.messageFilterEnabled;
			}

			@Override
			public boolean isMultiItemMode() {
				return MUCComponent.this.multiItemMode;
			}

			@Override
			public boolean isNewRoomLocked() {
				return MUCComponent.this.newRoomLocked;
			}

			@Override
			public boolean isPresenceFilterEnabled() {
				return MUCComponent.this.presenceFilterEnabled;
			}

			@Override
			public boolean isPublicLoggingEnabled() {
				return MUCComponent.this.publicLoggingEnabled;
			}
		};
	}

	protected IMucRepository createMucRepository(MucContext componentConfig, MucDAO dao) throws RepositoryException {
		return new InMemoryMucRepository(componentConfig, dao);
	}

	@Override
	public synchronized void everyHour() {
		super.everyHour();
		if (!searchGhostsEveryMinute)
			executePingInThread();
	}

	@Override
	public synchronized void everyMinute() {
		super.everyMinute();
		if (searchGhostsEveryMinute)
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

	@Override
	public String getComponentVersion() {
		String version = this.getClass().getPackage().getImplementationVersion();
		return version == null ? "0.0.0" : version;
	}

	@Override
	protected Map<String, Class<? extends Module>> getDefaultModulesList() {
		Map<String, Class<? extends Module>> result = new HashMap<String, Class<? extends Module>>();

		result.put(XmppPingModule.ID, XmppPingModule.class);
		result.put(JabberVersionModule.ID, JabberVersionModule.class);

		result.put(tigase.component.modules.impl.DiscoveryModule.ID, DiscoveryModule.class);
		result.put(GroupchatMessageModule.ID, GroupchatMessageModule.class);
		result.put(IqStanzaForwarderModule.ID, IqStanzaForwarderModule.class);
		result.put(MediatedInvitationModule.ID, MediatedInvitationModule.class);
		result.put(ModeratorModule.ID, ModeratorModule.class);
		result.put(PresenceModule.ID, PresenceModuleImpl.class);
		result.put(PrivateMessageModule.ID, PrivateMessageModule.class);
		result.put(RoomConfigurationModule.ID, RoomConfigurationModule.class);
		result.put(UniqueRoomNameModule.ID, UniqueRoomNameModule.class);
		result.put(AbstractAdHocCommandModule.ID, AdHocCommandModule.class);

		return result;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see tigase.component.AbstractComponent#getDefaults(java.util.Map)
	 */
	@Override
	public Map<String, Object> getDefaults(Map<String, Object> params) {
		Map<String, Object> props = super.getDefaults(params);

		props.put(LOG_DIR_KEY, new String("./logs/"));
		props.put(MESSAGE_FILTER_ENABLED_KEY, Boolean.TRUE);
		props.put(PRESENCE_FILTER_ENABLED_KEY, Boolean.FALSE);
		props.put(SEARCH_GHOSTS_EVERY_MINUTE_KEY, Boolean.FALSE);

		props.put(MUC_ALLOW_CHAT_STATES_KEY, Boolean.FALSE);
		props.put(MUC_LOCK_NEW_ROOM_KEY, Boolean.TRUE);
		props.put(MUC_MULTI_ITEM_ALLOWED_KEY, Boolean.TRUE);

		return props;
	}

	@Override
	public String getDiscoCategory() {
		return "conference";
	}

	@Override
	public String getDiscoCategoryType() {
		return "text";
	}

	@Override
	public String getDiscoDescription() {
		return "Multi User Chat";
	}

	public IMucRepository getMucRepository() {
		return mucRepository;
	}

	@Override
	public int hashCodeForPacket(Packet packet) {
		if ((packet.getStanzaFrom() != null) && (packet.getPacketFrom() != null)
				&& !getComponentId().equals(packet.getPacketFrom())) {
			return packet.getStanzaFrom().hashCode();
		}

		if (packet.getStanzaTo() != null) {
			return packet.getStanzaTo().hashCode();
		}

		return 1;
	}

	@Override
	public boolean isSubdomain() {
		return true;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * tigase.component.AbstractComponent.ModuleRegisteredHandler#onModuleRegistered
	 * (java.lang.String, tigase.component.modules.Module)
	 */
	@Override
	public void onModuleRegistered(String id, Module module) {
		if (id.equals(PresenceModule.ID) && module instanceof PresenceModule) {
			ghostbuster.setPresenceModule((PresenceModule) module);
		}
	}

	@Override
	public int processingInThreads() {
		return Runtime.getRuntime().availableProcessors() * 4;
	}

	@Override
	public int processingOutThreads() {
		return Runtime.getRuntime().availableProcessors() * 4;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * tigase.component.AbstractComponent#processStanzaPacket(tigase.server.
	 * Packet)
	 */
	@Override
	protected void processStanzaPacket(Packet packet) {
		try {
			ghostbuster.update(packet);
		} catch (Exception e) {
			log.log(Level.WARNING, "There is no Dana, there is only Zuul", e);
		}
		super.processStanzaPacket(packet);
	}

	@Override
	public void setProperties(Map<String, Object> props) {
		if (props.size() == 1) {
			// If props.size() == 1, it means this is a single property update
			// and this component does not support single property change for
			// the rest
			// of it's settings
			return;
		}

		if (props.containsKey(PING_EVERY_MINUTE_KEY)) {
			this.searchGhostsEveryMinute = (Boolean) props.get(PING_EVERY_MINUTE_KEY);
		} else {
			this.searchGhostsEveryMinute = (Boolean) props.get(SEARCH_GHOSTS_EVERY_MINUTE_KEY);
		}

		if (props.containsKey(MUCComponent.MESSAGE_FILTER_ENABLED_KEY))
			this.messageFilterEnabled = (Boolean) props.get(MUCComponent.MESSAGE_FILTER_ENABLED_KEY);

		if (props.containsKey(MUCComponent.PRESENCE_FILTER_ENABLED_KEY))
			this.presenceFilterEnabled = (Boolean) props.get(MUCComponent.PRESENCE_FILTER_ENABLED_KEY);

		if (props.containsKey(MUCComponent.MUC_MULTI_ITEM_ALLOWED_KEY))
			this.multiItemMode = (Boolean) props.get(MUCComponent.MUC_MULTI_ITEM_ALLOWED_KEY);

		if (props.containsKey(MUCComponent.MUC_ALLOW_CHAT_STATES_KEY))
			this.chatStateAllowed = (Boolean) props.get(MUCComponent.MUC_ALLOW_CHAT_STATES_KEY);

		if (props.containsKey(MUCComponent.MUC_LOCK_NEW_ROOM_KEY))
			this.newRoomLocked = (Boolean) props.get(MUCComponent.MUC_LOCK_NEW_ROOM_KEY);

		if (props.containsKey(LOG_DIR_KEY)) {
			log.config("Setting Chat Logging Directory");
			this.chatLoggingDirectory = (String) props.get(LOG_DIR_KEY);
		}

		if (mucRepository == null) {
			log.config("Initializing MUC Repository");
			try {
				final String cls_name = (String) props.get(MUC_REPO_CLASS_PROP_KEY);
				final String res_uri = (String) props.get(MUC_REPO_URL_PROP_KEY);

				UserRepository userRepository;
				if (cls_name != null && res_uri != null) {
					userRepository = RepositoryFactory.getUserRepository(cls_name, res_uri, null);
				} else {
					userRepository = (UserRepository) props.get(RepositoryFactory.SHARED_USER_REPO_PROP_KEY);
				}
				MucDAO dao = new MucDAO(context, userRepository);
				mucRepository = createMucRepository(context, dao);
			} catch (Exception e) {
				log.log(Level.WARNING, "Cannot initialize MUC Repository", e);
			}
		}

		if (props.containsKey(HistoryManagerFactory.DB_CLASS_KEY)) {
			log.config("Initializing History Provider");
			try {
				this.historyProvider = HistoryManagerFactory.getHistoryManager(props);
				this.historyProvider.init(props);
			} catch (Exception e) {
				log.log(Level.WARNING, "Cannot initialize History Provider", e);
			}
		}

		if (props.containsKey(MucLogger.MUC_LOGGER_CLASS_KEY)) {
			log.config("Initializing MUC Logger");
			String loggerClassName = (String) props.get(MucLogger.MUC_LOGGER_CLASS_KEY);
			try {
				if (log.isLoggable(Level.CONFIG))
					log.config("Using Room Logger: " + loggerClassName);
				this.mucLogger = (MucLogger) Class.forName(loggerClassName).newInstance();
				this.mucLogger.init(context);
			} catch (Exception e) {
				log.log(Level.WARNING, "Cannot initialize MUC Logger", e);
			}
		}

		try {
			updateDefaultRoomConfig(props);
		} catch (Exception e) {
			log.log(Level.WARNING, "Cannot update Default Room Config", e);
		}

		super.setProperties(props);
	}

	private void updateDefaultRoomConfig(Map<String, Object> props) throws RepositoryException {
		boolean found = false;
		for (Entry<String, Object> x : props.entrySet()) {
			if (x.getKey().startsWith(DEFAULT_ROOM_CONFIG_PREFIX_KEY)) {
				found = true;
				break;
			}
		}

		if (!found)
			return;

		log.config("Updating Default Room Config");

		final RoomConfig defaultRoomConfig = mucRepository.getDefaultRoomConfig();
		boolean changed = false;
		for (Entry<String, Object> x : props.entrySet()) {
			if (x.getKey().startsWith(DEFAULT_ROOM_CONFIG_PREFIX_KEY)) {
				String var = x.getKey().substring(DEFAULT_ROOM_CONFIG_PREFIX_KEY.length());

				Field field = defaultRoomConfig.getConfigForm().get(var);
				if (field != null) {
					changed = true;
					field.setValues(new String[] { (String) x.getValue() });
				} else if (log.isLoggable(Level.WARNING)) {
					log.warning("Default config room doesn't contains variable '" + var + "'!");
				}
			}
		}
		if (changed) {
			if (log.isLoggable(Level.CONFIG))
				log.config("Default room configuration is udpated");
			mucRepository.updateDefaultRoomConfig(defaultRoomConfig);
		}

	}

}
