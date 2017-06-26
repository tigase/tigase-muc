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

import tigase.component.AbstractComponent;
import tigase.component.AbstractContext;
import tigase.component.exceptions.ComponentException;
import tigase.component.exceptions.RepositoryException;
import tigase.component.modules.Module;
import tigase.component.modules.impl.AdHocCommandModule;
import tigase.component.modules.impl.JabberVersionModule;
import tigase.component.modules.impl.XmppPingModule;
import tigase.conf.ConfigurationException;
import tigase.db.RepositoryFactory;
import tigase.db.UserRepository;
import tigase.form.Field;
import tigase.muc.history.HistoryManagerFactory;
import tigase.muc.history.HistoryProvider;
import tigase.muc.logger.MucLogger;
import tigase.muc.modules.*;
import tigase.muc.repository.IMucRepository;
import tigase.muc.repository.MucDAO;
import tigase.muc.repository.inmemory.InMemoryMucRepository;
import tigase.server.Packet;
import tigase.xmpp.BareJID;

import javax.script.Bindings;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;

public class MUCComponent
		extends AbstractComponent<MucContext> {

	public static final String DEFAULT_ROOM_CONFIG_KEY = "default_room_config";
	public static final String DEFAULT_ROOM_CONFIG_PREFIX_KEY = DEFAULT_ROOM_CONFIG_KEY + "/";
	public static final String LOG_DIR_KEY = "room-log-directory";
	public static final String MESSAGE_FILTER_ENABLED_KEY = "message-filter-enabled";
	public static final String MUC_ALLOW_CHAT_STATES_KEY = "muc-allow-chat-states";
	public static final String MUC_LOCK_NEW_ROOM_KEY = "muc-lock-new-room";
	public static final String MUC_MULTI_ITEM_ALLOWED_KEY = "muc-multi-item-allowed";
	/**
	 * @deprecated Use {@linkplain MUCComponent#SEARCH_GHOSTS_EVERY_MINUTE_KEY SEARCH_GHOSTS_MINUTE_KEY} instead.
	 */
	@Deprecated
	public static final String PING_EVERY_MINUTE_KEY = "ping-every-minute";
	public static final String PRESENCE_FILTER_ENABLED_KEY = "presence-filter-enabled";
	public static final String SEARCH_GHOSTS_EVERY_MINUTE_KEY = "search-ghosts-every-minute";
	protected static final String MUC_REPO_CLASS_PROP_KEY = "muc-repo-class";
	protected static final String MUC_REPO_URL_PROP_KEY = "muc-repo-url";
	private static final String GHOSTBUSTER_ENABLED_KEY = "ghostbuster-enabled";
	private static final String MUC_REPOSITORY_VAR = "mucRepository";
	private static final String OWNER_MODULE_VAR = "ownerModule";
	private static final String PRESENCE_MODULE_VAR = "presenceModule";
	private static final String MUC_CUSTOM_DISCO_FILTER = "roomFilterClass";
	private static final String MUC_ADD_ID_TO_MESSAGE_IF_MISSING_KEY = "muc-add-id-to-message-if-missing";
	private static final String WELCOME_MESSAGE_ENABLED_KEY = "welcome-message";
	protected boolean addMessageIdIfMissing = true;
	protected String chatLoggingDirectory;
	protected Boolean chatStateAllowed;
	protected Ghostbuster2 ghostbuster;
	protected HistoryProvider historyProvider;
	protected boolean messageFilterEnabled;
	protected MucLogger mucLogger;
	protected IMucRepository mucRepository;
	protected boolean multiItemMode;
	protected Boolean newRoomLocked;
	protected boolean presenceFilterEnabled;
	protected boolean searchGhostsEveryMinute = false;
	private boolean welcomeMessagesEnabled = true;

	protected static void addIfExists(Bindings binds, String name, Object value) {
		if (value != null) {
			binds.put(name, value);
		}
	}

	public MUCComponent() {
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see tigase.component.AbstractComponent#createContext()
	 */
	@Override
	protected MucContext createContext() {
		return new MucContextImpl(this);
	}

	protected IMucRepository createMucRepository(MucContext componentConfig, MucDAO dao) throws RepositoryException {
		return new InMemoryMucRepository(componentConfig, dao);
	}

	@Override
	public synchronized void everyHour() {
		super.everyHour();
		if (!searchGhostsEveryMinute) {
			executePingInThread();
		}
	}

	@Override
	public synchronized void everyMinute() {
		super.everyMinute();
		if (searchGhostsEveryMinute) {
			executePingInThread();
		}
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
		result.put(AdHocCommandModule.ID, AdHocCommandModule.class);

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
		props.put(GHOSTBUSTER_ENABLED_KEY, Boolean.TRUE);

		props.put(MUC_ALLOW_CHAT_STATES_KEY, Boolean.FALSE);
		props.put(MUC_LOCK_NEW_ROOM_KEY, Boolean.TRUE);
		props.put(MUC_MULTI_ITEM_ALLOWED_KEY, Boolean.TRUE);

		// By default use the same repository as all other components:
		String repo_uri = (params.get(RepositoryFactory.GEN_USER_DB_URI) != null) ? (String) params.get(
				RepositoryFactory.GEN_USER_DB_URI) : "memory";

		props.put(HistoryManagerFactory.DB_URI_KEY, repo_uri);

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
		if ((packet.getStanzaFrom() != null) && (packet.getPacketFrom() != null) &&
				!getComponentId().equals(packet.getPacketFrom())) {
			return packet.getStanzaFrom().hashCode();
		}

		if (packet.getStanzaTo() != null) {
			return packet.getStanzaTo().hashCode();
		}

		return 1;
	}

	@Override
	public void initBindings(Bindings binds) {
		super.initBindings(binds);

		addIfExists(binds, PRESENCE_MODULE_VAR, modulesManager.getModule(PresenceModule.ID));
		addIfExists(binds, OWNER_MODULE_VAR, modulesManager.getModule(RoomConfigurationModule.ID));
		addIfExists(binds, MUC_REPOSITORY_VAR, mucRepository);
	}

	@Override
	public boolean isDiscoNonAdmin() {
		return true;
	}

	@Override
	public boolean isSubdomain() {
		return true;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void processPacket(Packet packet) {
		if (ghostbuster != null) {
			try {
				ghostbuster.update(packet);
			} catch (Exception e) {
				log.log(Level.WARNING, "There is no Dana, there is only Zuul", e);
			}
		}
		super.processPacket(packet);
	}

	@Override
	public int processingInThreads() {
		return Runtime.getRuntime().availableProcessors() * 4;
	}

	@Override
	public int processingOutThreads() {
		return Runtime.getRuntime().availableProcessors() * 4;
	}

	@Override
	public void release() {
		super.release();

		if (historyProvider != null) {
			historyProvider.destroy();
			historyProvider = null;
		}
	}

	protected void sendException(final Packet packet, final ComponentException e) {
		try {
			final String t = packet.getElement().getAttributeStaticStr(Packet.TYPE_ATT);

			if ((t != null) && (t == "error")) {
				if (log.isLoggable(Level.FINER)) {
					log.finer(packet.getElemName() + " stanza already with type='error' ignored");
				}

				return;
			}

			Packet result = e.makeElement(packet, true);
			// Why do we need this? Make error should return proper from/to values
//			Element el = result.getElement();
//
//			el.setAttribute("from", BareJID.bareJIDInstance(el.getAttributeStaticStr(Packet.FROM_ATT)).toString());
			if (log.isLoggable(Level.FINEST)) {
				log.log(Level.FINEST, "Sending back: " + result.toString());
			}
			context.getWriter().write(result);
		} catch (Exception e1) {
			if (log.isLoggable(Level.WARNING)) {
				log.log(Level.WARNING, "Problem during generate error response", e1);
			}
		}
	}

	@Override
	public void setProperties(Map<String, Object> props) throws ConfigurationException {
		if (props.size() == 1) {
			// If props.size() == 1, it means this is a single property update
			// and this component does not support single property change for
			// the rest
			// of it's settings
			log.config("props.size() == 1, ignoring setting properties");
			return;
		}

		if (props.containsKey(PING_EVERY_MINUTE_KEY)) {
			this.searchGhostsEveryMinute = (Boolean) props.get(PING_EVERY_MINUTE_KEY);
			log.config("props.containsKey(PING_EVERY_MINUTE_KEY): " + props.containsKey(PING_EVERY_MINUTE_KEY));
		} else {
			log.config(
					"props.containsKey(PING_EVERY_MINUTE_KEY): " + props.containsKey(SEARCH_GHOSTS_EVERY_MINUTE_KEY));
			this.searchGhostsEveryMinute = (Boolean) props.get(SEARCH_GHOSTS_EVERY_MINUTE_KEY);
		}
		log.config("searchGhostsEveryMinute: " + searchGhostsEveryMinute + "; " +
						   props.containsKey(PING_EVERY_MINUTE_KEY));

		if (props.containsKey(MUCComponent.GHOSTBUSTER_ENABLED_KEY)) {
			boolean e = (Boolean) props.get(MUCComponent.GHOSTBUSTER_ENABLED_KEY);
			if (e && ghostbuster == null) {
				log.config("Enabling Ghostbuster");
				ghostbuster = new Ghostbuster2(this);
			} else if (!e && ghostbuster != null) {
				log.config("Disabling Ghostbuster");
				ghostbuster = null;
			}
		}

		if (props.containsKey(MUCComponent.MESSAGE_FILTER_ENABLED_KEY)) {
			this.messageFilterEnabled = (Boolean) props.get(MUCComponent.MESSAGE_FILTER_ENABLED_KEY);
		}
		log.config("messageFilterEnabled: " + messageFilterEnabled + "; props: " +
						   props.containsKey(MUCComponent.MESSAGE_FILTER_ENABLED_KEY));

		if (props.containsKey(MUCComponent.PRESENCE_FILTER_ENABLED_KEY)) {
			this.presenceFilterEnabled = (Boolean) props.get(MUCComponent.PRESENCE_FILTER_ENABLED_KEY);
		}
		log.config("presenceFilterEnabled: " + presenceFilterEnabled + "; props: " +
						   props.containsKey(MUCComponent.PRESENCE_FILTER_ENABLED_KEY));

		if (props.containsKey(MUCComponent.MUC_MULTI_ITEM_ALLOWED_KEY)) {
			this.multiItemMode = (Boolean) props.get(MUCComponent.MUC_MULTI_ITEM_ALLOWED_KEY);
		}
		log.config("multiItemMode: " + multiItemMode + "; props: " +
						   props.containsKey(MUCComponent.MUC_MULTI_ITEM_ALLOWED_KEY));

		if (props.containsKey(MUCComponent.MUC_ALLOW_CHAT_STATES_KEY)) {
			this.chatStateAllowed = (Boolean) props.get(MUCComponent.MUC_ALLOW_CHAT_STATES_KEY);
		}
		log.config("chatStateAllowed: " + chatStateAllowed + "; props: " +
						   props.containsKey(MUCComponent.MUC_ALLOW_CHAT_STATES_KEY));

		if (props.containsKey(MUCComponent.MUC_LOCK_NEW_ROOM_KEY)) {
			this.newRoomLocked = (Boolean) props.get(MUCComponent.MUC_LOCK_NEW_ROOM_KEY);
		}
		log.config("newRoomLocked: " + newRoomLocked + "; props: " +
						   props.containsKey(MUCComponent.MUC_LOCK_NEW_ROOM_KEY));

		if (props.containsKey(LOG_DIR_KEY)) {
			log.config("Setting Chat Logging Directory");
			this.chatLoggingDirectory = (String) props.get(LOG_DIR_KEY);
		}

		if (props.containsKey(MUCComponent.MUC_ADD_ID_TO_MESSAGE_IF_MISSING_KEY)) {
			this.addMessageIdIfMissing = (Boolean) props.get(MUCComponent.MUC_ADD_ID_TO_MESSAGE_IF_MISSING_KEY);
		}
		log.config("addMessageIdIfMissing: " + this.addMessageIdIfMissing);

		if (props.containsKey(MUCComponent.WELCOME_MESSAGE_ENABLED_KEY)) {
			this.welcomeMessagesEnabled = (Boolean) props.get(MUCComponent.WELCOME_MESSAGE_ENABLED_KEY);
		}
		log.config("welcome-messages: " + this.welcomeMessagesEnabled);

		log.config("Initializing History Provider, props.containsKey(HistoryManagerFactory.DB_CLASS_KEY): " +
						   props.containsKey(HistoryManagerFactory.DB_CLASS_KEY));
		HistoryProvider oldHistoryProvider = this.historyProvider;
		this.historyProvider = HistoryManagerFactory.getHistoryManager(props);
		this.historyProvider.init(props);
		if (oldHistoryProvider != null) {
			// if there was other instance of HistoryProvider then destroy it as
			// we have
			// new instance initialized and we should release resources
			oldHistoryProvider.destroy();
		}

		if (mucRepository == null) {
			try {
				final String cls_name = (String) props.get(MUC_REPO_CLASS_PROP_KEY);
				final String res_uri = (String) props.get(MUC_REPO_URL_PROP_KEY);

				log.config("Initializing MUC Repository" + "; cls_name: " + cls_name + "; res_uri: " + res_uri);

				UserRepository userRepository;
				if (cls_name != null && res_uri != null) {
					userRepository = RepositoryFactory.getUserRepository(cls_name, res_uri, null);
				} else {
					userRepository = (UserRepository) props.get(RepositoryFactory.SHARED_USER_REPO_PROP_KEY);
				}
				MucDAO dao = new MucDAO(context, userRepository);
				mucRepository = createMucRepository(context, dao);
				log.config(
						"MUC Repository initialized" + "; userRepository: " + userRepository + "; MucDAO dao: " + dao +
								"; mucRepository: " + mucRepository);

			} catch (Exception e) {
				log.log(Level.WARNING, "Cannot initialize MUC Repository", e);
			}
		}

		log.config("Initializing MUC Logger, props.containsKey(MucLogger.MUC_LOGGER_CLASS_KEY)" +
						   props.containsKey(MucLogger.MUC_LOGGER_CLASS_KEY));
		if (props.containsKey(MucLogger.MUC_LOGGER_CLASS_KEY)) {
			String loggerClassName = (String) props.get(MucLogger.MUC_LOGGER_CLASS_KEY);
			try {
				if (log.isLoggable(Level.CONFIG)) {
					log.config("Using Room Logger: " + loggerClassName);
				}
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

		if (ghostbuster != null && modulesManager.isRegistered(PresenceModule.ID)) {
			PresenceModule module = modulesManager.getModule(PresenceModule.ID);
			ghostbuster.setPresenceModule(module);
		}

		if (props.containsKey(MUCComponent.MUC_CUSTOM_DISCO_FILTER)) {
			try {
				String filterClassName = (String) props.get(MUCComponent.MUC_CUSTOM_DISCO_FILTER);

				Class filterClass = Class.forName(filterClassName);
				DiscoItemsFilter filter = (DiscoItemsFilter) filterClass.newInstance();
				((DiscoveryModule) modulesManager.getModule(DiscoveryModule.ID)).setFilter(filter);
				log.config("Custom disco#items filter class: " + filterClassName);
			} catch (Exception e) {
				log.log(Level.WARNING, "Cannot set custom disco#items filter", e);
			}

		}

	}

	private void updateDefaultRoomConfig(Map<String, Object> props) throws RepositoryException {
		boolean found = false;
		for (Entry<String, Object> x : props.entrySet()) {
			if (x.getKey().startsWith(DEFAULT_ROOM_CONFIG_PREFIX_KEY)) {
				found = true;
				break;
			}
		}

		if (!found) {
			return;
		}

		log.config("Updating Default Room Config");

		final RoomConfig defaultRoomConfig = mucRepository.getDefaultRoomConfig();
		boolean changed = false;
		for (Entry<String, Object> x : props.entrySet()) {
			if (x.getKey().startsWith(DEFAULT_ROOM_CONFIG_PREFIX_KEY)) {
				String var = x.getKey().substring(DEFAULT_ROOM_CONFIG_PREFIX_KEY.length());

				Field field = defaultRoomConfig.getConfigForm().get(var);
				if (field != null) {
					changed = true;
					String[] values = ((String) x.getValue()).split(",");
					field.setValues(values);
				} else if (log.isLoggable(Level.WARNING)) {
					log.warning("Default config room doesn't contains variable '" + var + "'!");
				}
			}
		}
		if (changed) {
			if (log.isLoggable(Level.CONFIG)) {
				log.config("Default room configuration is udpated");
			}
			mucRepository.updateDefaultRoomConfig(defaultRoomConfig);
		}

	}

	private class MucContextImpl
			extends AbstractContext
			implements MucContext {

		private final BareJID serviceName = BareJID.bareJIDInstanceNS("multi-user-chat");

		/**
		 * @param component
		 */
		public MucContextImpl(AbstractComponent<?> component) {
			super(component);
		}

		@Override
		public String getChatLoggingDirectory() {
			return MUCComponent.this.chatLoggingDirectory;
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
		public boolean isAddMessageIdIfMissing() {
			return MUCComponent.this.addMessageIdIfMissing;
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
			return mucLogger != null || (historyProvider != null && historyProvider.isPersistent());
		}

		@Override
		public boolean isWelcomeMessagesEnabled() {
			return MUCComponent.this.welcomeMessagesEnabled;
		}

	}

}
