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

import java.util.logging.Logger;

import tigase.kernel.beans.Bean;
import tigase.kernel.beans.config.ConfigField;
import tigase.xmpp.BareJID;

/**
 * @author bmalkow
 *
 */
@Bean(name = "muc-config")
public class MUCConfig {

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
	@ConfigField(desc = "Allowing Chat-States", alias = MUC_ALLOW_CHAT_STATES_KEY)
	protected Boolean chatStateAllowed = false;
	@ConfigField(desc = "Logs Directory", alias = LOG_DIR_KEY)
	private String chatLoggingDirectory = "./logs/";
	@ConfigField(desc = "Chat history database class", alias = DB_CLASS_KEY)
	private String databaseClassName;
	@ConfigField(desc = "Chat history database URI", alias = DB_URI_KEY)
	private String databaseUri;
	@ConfigField(desc = "GhostBuster enabled", alias = GHOSTBUSTER_ENABLED_KEY)
	private boolean ghostbusterEnabled = true;
	@ConfigField(desc = "Passing only body element", alias = MESSAGE_FILTER_ENABLED_KEY)
	private boolean messageFilterEnabled = true;
	@ConfigField(desc = "Multi resources login allowed", alias = MUC_MULTI_ITEM_ALLOWED_KEY)
	private boolean multiItemMode = true;
	@ConfigField(desc = "Lock newly created room", alias = MUC_LOCK_NEW_ROOM_KEY)
	private boolean newRoomLocked = true;
	@ConfigField(desc = "Passing only bare presence", alias = PRESENCE_FILTER_ENABLED_KEY)
	private boolean presenceFilterEnabled = false;
	@ConfigField(desc = "Repository class name", alias = MUC_REPO_CLASS_PROP_KEY)
	private String repositoryClassName;
	@ConfigField(desc = "Repository URI", alias = MUC_REPO_URL_PROP_KEY)
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

	public String getDatabaseUri() {
		return databaseUri;
	}

	public String getRepositoryClassName() {
		return repositoryClassName;
	}

	public String getRepositoryUri() {
		return repositoryUri;
	}

	/**
	 * @return
	 */
	public BareJID getServiceName() {
		return serviceName;
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
