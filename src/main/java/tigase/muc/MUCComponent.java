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
import java.util.Map.Entry;
import java.util.logging.Level;

import javax.script.Bindings;

import tigase.component.AbstractKernelBasedComponent;
import tigase.component.exceptions.RepositoryException;
import tigase.component.modules.impl.AdHocCommandModule;
import tigase.component.modules.impl.JabberVersionModule;
import tigase.component.modules.impl.XmppPingModule;
import tigase.conf.ConfigurationException;
import tigase.db.UserRepository;
import tigase.form.Field;
import tigase.kernel.core.Kernel;
import tigase.muc.history.HistoryProvider;
import tigase.muc.history.HistoryProviderFactory;
import tigase.muc.logger.RoomChatLogger;
import tigase.muc.modules.DiscoveryModule;
import tigase.muc.modules.GroupchatMessageModule;
import tigase.muc.modules.IqStanzaForwarderModule;
import tigase.muc.modules.MediatedInvitationModule;
import tigase.muc.modules.ModeratorModule;
import tigase.muc.modules.PresenceModuleImpl;
import tigase.muc.modules.PrivateMessageModule;
import tigase.muc.modules.RoomConfigurationModule;
import tigase.muc.modules.UniqueRoomNameModule;
import tigase.muc.repository.IMucRepository;
import tigase.muc.repository.MucDAO;
import tigase.muc.repository.MucRepositoryFactory;
import tigase.muc.repository.UserRepositoryFactory;
import tigase.server.Packet;

public class MUCComponent extends AbstractKernelBasedComponent {

	public static final String DEFAULT_ROOM_CONFIG_KEY = "default_room_config";

	public static final String DEFAULT_ROOM_CONFIG_PREFIX_KEY = DEFAULT_ROOM_CONFIG_KEY + "/";

	protected static void addIfExists(Bindings binds, String name, Object value) {
		if (value != null) {
			binds.put(name, value);
		}
	}

	private Ghostbuster2 ghostbuster;

	public MUCComponent() {
	}

	@Override
	public String getComponentVersion() {
		String version = this.getClass().getPackage().getImplementationVersion();
		return version == null ? "0.0.0" : version;
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
	public void initBindings(Bindings binds) {
		super.initBindings(binds);

		// TODO addIfExists(binds, PRESENCE_MODULE_VAR,
		// kernel.getInstance(PresenceModule.ID));
		// addIfExists(binds, OWNER_MODULE_VAR,
		// kernel.getInstance(RoomConfigurationModule.ID));
		// addIfExists(binds, MUC_REPOSITORY_VAR,
		// kernel.getInstance(IMucRepository.class));
	}

	@Override
	public boolean isDiscoNonAdmin() {
		return true;
	}

	@Override
	public boolean isSubdomain() {
		return true;
	}

	@Override
	public int processingInThreads() {
		return Runtime.getRuntime().availableProcessors() * 4;
	}

	@Override
	public int processingOutThreads() {
		return Runtime.getRuntime().availableProcessors() * 4;
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
	protected void registerModules(Kernel kernel) {
		kernel.registerBean(XmppPingModule.class).exec();
		kernel.registerBean(JabberVersionModule.class).exec();
		kernel.registerBean(DiscoveryModule.class).exec();
		kernel.registerBean(GroupchatMessageModule.class).exec();
		kernel.registerBean(IqStanzaForwarderModule.class).exec();
		kernel.registerBean(MediatedInvitationModule.class).exec();
		kernel.registerBean(ModeratorModule.class).exec();
		kernel.registerBean(PresenceModuleImpl.class).exec();
		kernel.registerBean(PrivateMessageModule.class).exec();
		kernel.registerBean(RoomConfigurationModule.class).exec();
		kernel.registerBean(UniqueRoomNameModule.class).exec();
		kernel.registerBean(AdHocCommandModule.class).exec();

		kernel.registerBean(MUCConfig.class).exec();

		kernel.registerBean("user-repository").asClass(UserRepository.class).withFactory(UserRepositoryFactory.class).exec();
		kernel.registerBean(IMucRepository.ID).asClass(IMucRepository.class).withFactory(MucRepositoryFactory.class).exec();
		kernel.registerBean(HistoryProvider.class).withFactory(HistoryProviderFactory.class).exec();
		kernel.registerBean(MucDAO.class).exec();
		kernel.registerBean(RoomChatLogger.class).exec();
		kernel.registerBean(Ghostbuster2.class).exec();
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

		super.setProperties(props);

		this.ghostbuster = kernel.getInstance(Ghostbuster2.class);

		try {
			updateDefaultRoomConfig(props);
		} catch (Exception e) {
			log.log(Level.WARNING, "Cannot update Default Room Config", e);
		}
	}

	private void updateDefaultRoomConfig(Map<String, Object> props) throws RepositoryException {
		final IMucRepository mucRepository = kernel.getInstance(IMucRepository.class);

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
					String[] values = ((String) x.getValue()).split(",");
					field.setValues(values);
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
