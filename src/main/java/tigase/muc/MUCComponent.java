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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import tigase.conf.Configurable;
import tigase.criteria.Criteria;
import tigase.db.RepositoryFactory;
import tigase.db.UserRepository;
import tigase.disco.ServiceEntity;
import tigase.disco.ServiceIdentity;
import tigase.disco.XMPPService;
import tigase.muc.exceptions.MUCException;
import tigase.muc.modules.DiscoInfoModule;
import tigase.muc.modules.DiscoItemsModule;
import tigase.muc.modules.GroupchatMessageModule;
import tigase.muc.modules.IqStanzaForwarderModule;
import tigase.muc.modules.MediatedInvitationModule;
import tigase.muc.modules.ModeratorModule;
import tigase.muc.modules.PresenceModule;
import tigase.muc.modules.PrivateMessageModule;
import tigase.muc.modules.RoomConfigurationModule;
import tigase.muc.modules.SoftwareVersionModule;
import tigase.muc.modules.UniqueRoomNameModule;
import tigase.muc.modules.XmppPingModule;
import tigase.muc.modules.PresenceModule.DelayDeliveryThread.DelDeliverySend;
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
import tigase.xmpp.JID;
import tigase.xmpp.PacketErrorTypeException;
import tigase.xmpp.StanzaType;

@SuppressWarnings("deprecation")
public class MUCComponent extends AbstractMessageReceiver implements DelDeliverySend, XMPPService, Configurable, DisableDisco {

	public static final String ADMINS_KEY = "admins";

	private static final String LOG_DIR_KEY = "room-log-directory";

	protected static final String MUC_REPO_CLASS_PROP_KEY = "muc-repo-class";

	protected static final String MUC_REPO_URL_PROP_KEY = "muc-repo-url";

	private MucConfig config = new MucConfig();

	private MucDAO dao;

	public String[] HOSTNAMES_PROP_VAL = { "localhost", "hostname" };

	protected Logger log = Logger.getLogger(this.getClass().getName());

	private GroupchatMessageModule messageModule;

	private final ArrayList<Module> modules = new ArrayList<Module>();

	private IMucRepository mucRepository;

	private PresenceModule presenceModule;

	private IChatRoomLogger roomLogger;

	private ServiceEntity serviceEntity;

	private UserRepository userRepository;

	@Override
	public Map<String, Object> getDefaults(Map<String, Object> params) {
		Map<String, Object> props = super.getDefaults(params);

		if (params.get(GEN_VIRT_HOSTS) != null) {
			HOSTNAMES_PROP_VAL = ((String) params.get(GEN_VIRT_HOSTS)).split(",");
		} else {
			HOSTNAMES_PROP_VAL = DNSResolver.getDefHostNames();
		}
		String[] hostnames = new String[HOSTNAMES_PROP_VAL.length];
		int i = 0;
		for (String host : HOSTNAMES_PROP_VAL) {
			hostnames[i++] = getName() + "." + host;
		}
		props.put(HOSTNAMES_PROP_KEY, hostnames);

		// By default use the same repository as all other components:
		String repo_class = params.get(GEN_USER_DB) != null ? (String) params.get(GEN_USER_DB) : DERBY_REPO_CLASS_PROP_VAL;
		String repo_uri = params.get(GEN_USER_DB_URI) != null ? (String) params.get(GEN_USER_DB_URI) : DERBY_REPO_URL_PROP_VAL;
		props.put(MUC_REPO_CLASS_PROP_KEY, repo_class);
		props.put(MUC_REPO_URL_PROP_KEY, repo_uri);

		String[] admins;
		if (params.get(GEN_ADMINS) != null) {
			admins = ((String) params.get(GEN_ADMINS)).split(",");
		} else {
			admins = new String[] { "admin@" + getDefHostName() };
		}
		props.put(ADMINS_KEY, admins);

		props.put(LOG_DIR_KEY, new String("./logs/"));

		props.put("muc-allow-chat-states", Boolean.FALSE);
		props.put("muc-lock-new-room", Boolean.TRUE);

		return props;
	}

	@Override
	public List<Element> getDiscoFeatures() {
		return null;
	}

	@Override
	public Element getDiscoInfo(String node, JID jid) {
		return null;
	}

	@Override
	public List<Element> getDiscoItems(String node, JID jid) {
		if (node == null) {
			Element result = serviceEntity.getDiscoItem(null, getName() + "." + jid.toString());
			return Arrays.asList(result);
		} else {
			return null;
		}
	}

	public Set<String> getFeaturesFromModule() {
		HashSet<String> result = new HashSet<String>();
		for (Module module : this.modules) {
			if (module.getFeatures() != null) {
				for (String feature : module.getFeatures()) {
					if (feature != null)
						result.add(feature);
				}
			}

		}
		return result;
	}

	protected void init() {

		registerModule(new PrivateMessageModule(this.config, this.mucRepository));

		messageModule = registerModule(new GroupchatMessageModule(this.config, this.mucRepository, this.roomLogger));

		presenceModule = registerModule(new PresenceModule(this.config, this.mucRepository, this.roomLogger, this));

		registerModule(new RoomConfigurationModule(this.config, this.mucRepository, messageModule));
		registerModule(new ModeratorModule(this.config, this.mucRepository));

		registerModule(new SoftwareVersionModule());
		registerModule(new XmppPingModule());

		registerModule(new DiscoItemsModule(this.config, this.mucRepository));
		registerModule(new DiscoInfoModule(this.config, this.mucRepository, this));

		registerModule(new MediatedInvitationModule(this.config, this.mucRepository));

		registerModule(new UniqueRoomNameModule(this.config, this.mucRepository));

		registerModule(new IqStanzaForwarderModule(this.config, this.mucRepository));

	}

	public Collection<Element> process(final Element element) throws PacketErrorTypeException {
		List<Element> result = new ArrayList<Element>();
		try {
			boolean handled = runModules(element, result);

			if (!handled) {
				final String t = element.getAttribute("type");
				final StanzaType type = t == null ? null : StanzaType.valueof(t);
				if (type != StanzaType.error) {
					throw new MUCException(Authorization.FEATURE_NOT_IMPLEMENTED);
				} else {
					log.finer(element.getName() + " stanza with type='error' ignored");
				}
			}
		} catch (MUCException e) {
			Element r = e.makeElement(element, true);
			result.add(r);
		}
		return result;
	}

	@Override
	public void processPacket(Packet packet) {
		try {
			Collection<Element> result = process(packet.getElement());
			for (Element element : result) {
				try {
					addOutPacket(Packet.packetInstance(element));
				} catch (TigaseStringprepException ex) {
					log.info("Packet addressing problem, stringprep failed: "
							+ element);
				}
			}
		} catch (Exception e) {
			log.log(Level.WARNING, "Unexpected exception: internal-server-error", e);
			e.printStackTrace();
			try {
				addOutPacket(Authorization.INTERNAL_SERVER_ERROR.getResponseMessage(packet, e.getMessage(), true));
			} catch (PacketErrorTypeException e1) {
				e1.printStackTrace();
				log.throwing("MUC Component", "processPacket (sending internal-server-error)", e);
			}
		}
	}

	public <T extends Module> T registerModule(final T module) {
		log.config("Register MUC plugin: " + module.getClass().getCanonicalName());
		this.modules.add(module);
		return module;
	}

	protected boolean runModules(final Element element, Collection<Element> sendCollection) throws MUCException {
		boolean handled = false;
		log.finest("Processing packet: " + element.toString());

		for (Module module : this.modules) {
			Criteria criteria = module.getModuleCriteria();
			if (criteria != null && criteria.match(element) && module.isProcessedByModule(element)) {
				handled = true;
				log.finest("Handled by module " + module.getClass());
				List<Element> result = module.process(element);
				if (result != null) {
					sendCollection.addAll(result);
					return true;
				}
			}
		}
		return handled;
	}

	public void sendDelayedPacket(Packet packet) {
		addOutPacket(packet);
	}

	/**
	 * @param config2
	 */
	public void setConfig(MucConfig config2) {
		this.config = config2;
	}

	public void setMucRepository(IMucRepository mucRepository) {
		this.mucRepository = mucRepository;
	}

	@Override
	public void setProperties(Map<String, Object> props) {
		super.setProperties(props);

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

		this.config.setServiceName("multi-user-chat");

		this.config.setLogDirectory((String) props.get(LOG_DIR_KEY));

		if (userRepository == null) {
			userRepository = (UserRepository) props.get(SHARED_USER_REPO_POOL_PROP_KEY);
			if (userRepository == null) {
				// Is there shared user repository instance? If so I want to use
				// it:
				userRepository = (UserRepository) props.get(SHARED_USER_REPO_PROP_KEY);
			} else {
				log.info("Using shared repository pool.");
			}
			try {
				if (userRepository == null) {
					String cls_name = (String) props.get(MUC_REPO_CLASS_PROP_KEY);
					String res_uri = (String) props.get(MUC_REPO_URL_PROP_KEY);

					this.userRepository = RepositoryFactory.getUserRepository(getName(), cls_name, res_uri, null);
					userRepository.initRepository(res_uri, null);
				}

				dao = new MucDAO(this.config, this.userRepository);

				mucRepository = new InMemoryMucRepository(this.config, dao);

			} catch (Exception e) {
				log.severe("Can't initialize MUC repository: " + e);
				e.printStackTrace();
				// System.exit(1);
			}

			this.roomLogger = new RoomChatLogger(this.config);

			init();
		}

		this.messageModule.setChatStateAllowed((Boolean) props.get("muc-allow-chat-states"));
		this.presenceModule.setLockNewRoom((Boolean) props.get("muc-lock-new-room"));

		log.info("Tigase MUC Component ver. " + MucVersion.getVersion() + " started.");
	}
}
