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
package tigase.muc;

import tigase.component.AbstractKernelBasedComponent;
import tigase.component.exceptions.RepositoryException;
import tigase.component.modules.impl.AdHocCommandModule;
import tigase.component.modules.impl.JabberVersionModule;
import tigase.component.modules.impl.XmppPingModule;
import tigase.form.Field;
import tigase.kernel.beans.Bean;
import tigase.kernel.beans.Inject;
import tigase.kernel.beans.config.ConfigField;
import tigase.kernel.beans.selector.ClusterModeRequired;
import tigase.kernel.beans.selector.ConfigType;
import tigase.kernel.beans.selector.ConfigTypeEnum;
import tigase.kernel.core.Kernel;
import tigase.muc.modules.*;
import tigase.muc.repository.IMucRepository;
import tigase.server.Packet;
import tigase.xmpp.mam.MAMItemHandler;
import tigase.xmpp.mam.MAMQueryParser;
import tigase.xmpp.mam.modules.GetFormModule;

import javax.script.Bindings;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.logging.Level;

@Bean(name = "muc", parent = Kernel.class, active = true)
@ConfigType(ConfigTypeEnum.DefaultMode)
@ClusterModeRequired(active = false)
public class MUCComponent
		extends AbstractKernelBasedComponent {

	public static final String DEFAULT_ROOM_CONFIG_KEY = "default_room_config";
	public static final String DEFAULT_ROOM_CONFIG_PREFIX_KEY = DEFAULT_ROOM_CONFIG_KEY + "/";
	@ConfigField(alias = DEFAULT_ROOM_CONFIG_KEY, desc = "Default room configuration", allowAliasFromParent = false)
	private HashMap<String, String> defaultRoomConfig = new HashMap<>();
	@Inject
	private Ghostbuster2 ghostbuster;

	protected static void addIfExists(Bindings binds, String name, Object value) {
		if (value != null) {
			binds.put(name, value);
		}
	}

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
	public boolean isDiscoNonAdmin() {
		return true;
	}

	@Override
	public boolean isSubdomain() {
		return true;
	}

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
	public void initialize() {
		try {
			updateDefaultRoomConfig();
		} catch (Exception ex) {
			log.log(Level.FINEST, "Exception during modification of default room config", ex);
		}

		super.initialize();
	}

	@Override
	protected void registerModules(Kernel kernel) {
		kernel.registerBean(XmppPingModule.class).exec();
		kernel.registerBean(JabberVersionModule.class).exec();
		//kernel.registerBean(DiscoveryModule.class).exec();
		kernel.registerBean(GroupchatMessageModule.class).exec();
		kernel.registerBean(IqStanzaForwarderModule.class).exec();
		kernel.registerBean(MediatedInvitationModule.class).exec();
		kernel.registerBean(ModeratorModule.class).exec();
		//kernel.registerBean(PresenceModuleImpl.class).exec();
		kernel.registerBean(PrivateMessageModule.class).exec();
		kernel.registerBean(RoomConfigurationModule.class).exec();
		kernel.registerBean(UniqueRoomNameModule.class).exec();
		kernel.registerBean(AdHocCommandModule.class).exec();

		kernel.registerBean(MAMItemHandler.class).exec();
		kernel.registerBean(MAMQueryParser.class).exec();
		kernel.registerBean(MAMQueryModule.class).exec();
		kernel.registerBean(GetFormModule.class).exec();
		//kernel.registerBean(MUCConfig.class).exec();

//		kernel.registerBean(IMucRepository.ID).asClass(InMemoryMucRepository.class).exec();
		//kernel.registerBean(RoomChatLogger.class).exec();
		//kernel.registerBean(Ghostbuster2.class).exec();
	}

	private void updateDefaultRoomConfig() throws RepositoryException {
		final IMucRepository mucRepository = kernel.getInstance(IMucRepository.class);

		if (defaultRoomConfig.isEmpty()) {
			return;
		}

		log.config("Updating Default Room Config");

		final RoomConfig defaultRoomConfig = mucRepository.getDefaultRoomConfig();
		boolean changed = false;
		for (Entry<String, String> x : this.defaultRoomConfig.entrySet()) {
			String var = x.getKey();
			Field field = defaultRoomConfig.getConfigForm().get(var);
			if (field != null) {
				changed = true;
				String[] values = x.getValue().split(",");
				field.setValues(values);
			} else if (log.isLoggable(Level.WARNING)) {
				log.warning("Default config room doesn't contains variable '" + var + "'!");
			}
		}
		if (changed) {
			if (log.isLoggable(Level.CONFIG)) {
				log.config("Default room configuration is udpated");
			}
			mucRepository.updateDefaultRoomConfig(defaultRoomConfig);
		}

	}

}
