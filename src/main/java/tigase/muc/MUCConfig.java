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

import java.util.Map;
import java.util.logging.Logger;

import tigase.component.PropertiesBeanConfigurator;
import tigase.kernel.beans.Bean;
import tigase.kernel.beans.Initializable;
import tigase.kernel.beans.Inject;
import tigase.kernel.beans.config.ConfigField;
import tigase.xmpp.BareJID;

/**
 * @author bmalkow
 *
 */
@Bean(name = "muc-config")
public class MUCConfig implements Initializable {

	public static final String DB_CLASS_KEY = "history-db";

	public static final String DB_URI_KEY = "history-db-uri";
	public static final String LOG_DIR_KEY = "room-log-directory";
	public static final String MESSAGE_FILTER_ENABLED_KEY = "message-filter-enabled";
	public static final String MUC_ALLOW_CHAT_STATES_KEY = "muc-allow-chat-states";
	public static final String MUC_LOCK_NEW_ROOM_KEY = "muc-lock-new-room";
	public static final String MUC_MULTI_ITEM_ALLOWED_KEY = "muc-multi-item-allowed";
	public static final String PRESENCE_FILTER_ENABLED_KEY = "presence-filter-enabled";
	protected static final String MUC_REPO_CLASS_PROP_KEY = "muc-repo-class";

	protected static final String MUC_REPO_URL_PROP_KEY = "muc-repo-url";
	private static final String GHOSTBUSTER_ENABLED_KEY = "ghostbuster-enabled";
	protected final Logger log = Logger.getLogger(this.getClass().getName());
	private final BareJID serviceName = BareJID.bareJIDInstanceNS("multi-user-chat");
	@ConfigField(desc = "Allowing Chat-States")
	protected Boolean chatStateAllowed = false;
	@ConfigField(desc = "Logs Directory")
	private String chatLoggingDirectory = "./logs/";
	@Inject
	private PropertiesBeanConfigurator configurator;
	@ConfigField(desc = "Chat history database class")
	private String databaseClassName;
	@ConfigField(desc = "Chat history database URI")
	private String databaseUri;
	@ConfigField(desc = "GhostBuster enabled")
	private boolean ghostbusterEnabled = true;
	@ConfigField(desc = "Passing only body element")
	private boolean messageFilterEnabled = true;
	@ConfigField(desc = "Multi resources login allowed")
	private boolean multiItemMode = true;
	@ConfigField(desc = "Lock newly created room")
	private boolean newRoomLocked = true;
	@ConfigField(desc = "Passing only bare presence")
	private boolean presenceFilterEnabled = false;
	private String repositoryClassName;
	private String repositoryUri;

	/**
	 * @return
	 */
	public String getChatLoggingDirectory() {
		return chatLoggingDirectory;
	}

	public String getDatabaseClassName() {
		return databaseClassName;
	}

	public void setDatabaseClassName(String databaseClassName) {
		this.databaseClassName = databaseClassName;
	}

	public String getDatabaseUri() {
		return databaseUri;
	}

	public void setDatabaseUri(String databaseUri) {
		this.databaseUri = databaseUri;
	}

	public String getRepositoryClassName() {
		return repositoryClassName;
	}

	public void setRepositoryClassName(String repositoryClassName) {
		this.repositoryClassName = repositoryClassName;
	}

	public String getRepositoryUri() {
		return repositoryUri;
	}

	public void setRepositoryUri(String repositoryUri) {
		this.repositoryUri = repositoryUri;
	}

	/**
	 * @return
	 */
	public BareJID getServiceName() {
		return serviceName;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see tigase.kernel.beans.Initializable#initialize()
	 */
	@Override
	public void initialize() {
		final Map<String, Object> props = configurator.getProperties();

		if (props.containsKey(MESSAGE_FILTER_ENABLED_KEY)) {
			this.messageFilterEnabled = (Boolean) props.get(MESSAGE_FILTER_ENABLED_KEY);
		}
		log.config(
				"messageFilterEnabled: " + messageFilterEnabled + "; props: " + props.containsKey(MESSAGE_FILTER_ENABLED_KEY));

		if (props.containsKey(PRESENCE_FILTER_ENABLED_KEY)) {
			this.presenceFilterEnabled = (Boolean) props.get(PRESENCE_FILTER_ENABLED_KEY);
		}
		log.config("presenceFilterEnabled: " + presenceFilterEnabled + "; props: "
				+ props.containsKey(PRESENCE_FILTER_ENABLED_KEY));

		if (props.containsKey(MUC_MULTI_ITEM_ALLOWED_KEY)) {
			this.multiItemMode = (Boolean) props.get(MUC_MULTI_ITEM_ALLOWED_KEY);
		}
		log.config("multiItemMode: " + multiItemMode + "; props: " + props.containsKey(MUC_MULTI_ITEM_ALLOWED_KEY));

		if (props.containsKey(MUC_ALLOW_CHAT_STATES_KEY)) {
			this.chatStateAllowed = (Boolean) props.get(MUC_ALLOW_CHAT_STATES_KEY);
		}
		log.config("chatStateAllowed: " + chatStateAllowed + "; props: " + props.containsKey(MUC_ALLOW_CHAT_STATES_KEY));

		if (props.containsKey(MUC_LOCK_NEW_ROOM_KEY)) {
			this.newRoomLocked = (Boolean) props.get(MUC_LOCK_NEW_ROOM_KEY);
		}
		log.config("newRoomLocked: " + newRoomLocked + "; props: " + props.containsKey(MUC_LOCK_NEW_ROOM_KEY));

		if (props.containsKey(LOG_DIR_KEY)) {
			log.config("Setting Chat Logging Directory");
			this.chatLoggingDirectory = (String) props.get(LOG_DIR_KEY);
		}

		if (props.containsKey(GHOSTBUSTER_ENABLED_KEY)) {
			this.ghostbusterEnabled = (Boolean) props.get(GHOSTBUSTER_ENABLED_KEY);
		}
		log.config("ghostbuster: " + ghostbusterEnabled + "; props: " + props.containsKey(GHOSTBUSTER_ENABLED_KEY));

		if (props.containsKey(DB_CLASS_KEY)) {
			log.config("Setting Database Class Name");
			this.databaseClassName = (String) props.get(DB_CLASS_KEY);
		}

		if (props.containsKey(DB_URI_KEY)) {
			log.config("Setting Database URI");
			this.databaseUri = (String) props.get(DB_URI_KEY);
		}

		if (props.containsKey(MUC_REPO_CLASS_PROP_KEY)) {
			log.config("Setting Repository class name");
			this.repositoryClassName = (String) props.get(MUC_REPO_CLASS_PROP_KEY);
		}

		if (props.containsKey(MUC_REPO_URL_PROP_KEY)) {
			log.config("Setting Repository URI");
			this.repositoryUri = (String) props.get(MUC_REPO_URL_PROP_KEY);
		}

	}

	/**
	 * @return
	 */
	public boolean isChatStateAllowed() {
		return chatStateAllowed;
	}

	public boolean isGhostbusterEnabled() {
		return ghostbusterEnabled;
	}

	public void setGhostbusterEnabled(boolean ghostbusterEnabled) {
		this.ghostbusterEnabled = ghostbusterEnabled;
	}

	/**
	 * @return
	 */
	public boolean isMessageFilterEnabled() {
		return messageFilterEnabled;
	}

	/**
	 * @return
	 */
	public boolean isMultiItemMode() {
		return multiItemMode;
	}

	/**
	 * @return
	 */
	public boolean isNewRoomLocked() {
		return newRoomLocked;
	}

	/**
	 * @return
	 */
	public boolean isPresenceFilterEnabled() {
		return presenceFilterEnabled;
	}

}
