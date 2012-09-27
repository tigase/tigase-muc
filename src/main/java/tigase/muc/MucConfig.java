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

import java.util.Map;

import tigase.component.AbstractComponent;
import tigase.component.ComponentConfig;
import tigase.xmpp.BareJID;

/**
 * @author bmalkow
 * 
 */
public class MucConfig extends ComponentConfig {

	private boolean messageFilterEnabled;

	private boolean presenceFilterEnabled;

	private Map<String, Object> props;

	private boolean publicLoggingEnabled;

	private BareJID serviceName;

	protected MucConfig(AbstractComponent<?> component) {
		super(component);
	}

	@Override
	public Map<String, Object> getDefaults(Map<String, Object> params) {
		return null;
	}

	public Map<String, Object> getProps() {
		return props;
	}

	@Override
	public BareJID getServiceName() {
		return serviceName;
	}

	public boolean isMessageFilterEnabled() {
		return messageFilterEnabled;
	}

	public boolean isPresenceFilterEnabled() {
		return presenceFilterEnabled;
	}

	/**
	 * @return
	 */
	public boolean isPublicLoggingEnabled() {
		return publicLoggingEnabled;
	}

	@Override
	public void setProperties(Map<String, Object> props) {
		this.props = props;
		this.serviceName = BareJID.bareJIDInstanceNS("multi-user-chat");
		this.messageFilterEnabled = (Boolean) props.get(MUCComponent.MESSAGE_FILTER_ENABLED_KEY);
		this.presenceFilterEnabled = (Boolean) props.get(MUCComponent.PRESENCE_FILTER_ENABLED_KEY);
	}

	/**
	 * @param b
	 */
	void setPublicLoggingEnabled(boolean b) {
		this.publicLoggingEnabled = b;
	}

}
