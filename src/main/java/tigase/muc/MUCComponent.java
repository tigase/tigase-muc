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

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
import tigase.muc.modules.DiscoInfoModule;
import tigase.muc.modules.DiscoItemsModule;
import tigase.muc.modules.GroupchatMessageModule;
import tigase.muc.modules.IqStanzaForwarderModule;
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
	private static final String LOG_DIR_KEY = "room-log-directory";
	protected static final String MUC_REPO_CLASS_PROP_KEY = "muc-repo-class";
	protected static final String MUC_REPO_URL_PROP_KEY = "muc-repo-url";

	// ~--- fields
	// ---------------------------------------------------------------

	private MucConfig config = new MucConfig();
	private MucDAO dao;
	/** Field description */
	public String[] HOSTNAMES_PROP_VAL = { "localhost", "hostname" };
	protected Logger log = Logger.getLogger(this.getClass().getName());
	private GroupchatMessageModule messageModule;
	private final ModulesManager modulesManager = new ModulesManager();
	private IMucRepository mucRepository;
	private PresenceModule presenceModule;
	private IChatRoomLogger roomLogger;
	private ServiceEntity serviceEntity;
	private UserRepository userRepository;

	private final tigase.muc.ElementWriter writer;

	// ~--- get methods
	// ----------------------------------------------------------

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

	// ~--- methods
	// --------------------------------------------------------------

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

	protected void init() {

		this.modulesManager.register(new PrivateMessageModule(this.config, writer, this.mucRepository));
		messageModule = this.modulesManager.register(new GroupchatMessageModule(this.config, writer, this.mucRepository,
				this.roomLogger));
		presenceModule = this.modulesManager.register(new PresenceModule(this.config, writer, this.mucRepository,
				this.roomLogger, this));
		this.modulesManager.register(new RoomConfigurationModule(this.config, writer, this.mucRepository, messageModule));
		this.modulesManager.register(new ModeratorModule(this.config, writer, this.mucRepository));
		this.modulesManager.register(new SoftwareVersionModule(writer));
		this.modulesManager.register(new XmppPingModule(writer));
		this.modulesManager.register(new DiscoItemsModule(this.config, writer, this.mucRepository));
		this.modulesManager.register(new DiscoInfoModule(this.config, writer, this.mucRepository, this));
		this.modulesManager.register(new MediatedInvitationModule(this.config, writer, this.mucRepository));
		this.modulesManager.register(new UniqueRoomNameModule(this.config, writer, this.mucRepository));
		this.modulesManager.register(new IqStanzaForwarderModule(this.config, writer, this.mucRepository));
	}

	/**
	 * Method description
	 * 
	 * 
	 * @param packet
	 */
	@Override
	public void processPacket(Packet packet) {
		processStanzaPacket(packet);
	}

	protected void processStanzaPacket(final Packet packet) {
		try {
			boolean handled = this.modulesManager.process(packet, writer);

			if (!handled) {
				final String t = packet.getAttribute("type");
				final StanzaType type = t == null ? null : StanzaType.valueof(t);
				if (type != StanzaType.error) {
					throw new MUCException(Authorization.FEATURE_NOT_IMPLEMENTED);
				} else {
					if (log.isLoggable(Level.FINER)) {
						log.finer(packet.getElemName() + " stanza with type='error' ignored");
					}
				}
			}
		} catch (MUCException e) {
			try {
				if (log.isLoggable(Level.FINER)) {
					log.log(Level.FINER, "Exception thrown for " + packet.toString(), e);
				} else if (log.isLoggable(Level.FINE)) {
					log.log(Level.FINE, "PubSubException on stanza id=" + packet.getAttribute("id") + " " + e.getMessage());
				}
				Packet result = e.makeElement(packet, true);
				if (log.isLoggable(Level.FINEST)) {
					log.log(Level.FINEST, "Sending back: " + result.toString());
				}
				writer.write(result);
			} catch (Exception e1) {
				log.log(Level.WARNING, "Problem during generate error response", e1);
			}
		}
	}

	// ~--- set methods
	// ----------------------------------------------------------

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
		this.config.setServiceName(BareJID.bareJIDInstanceNS("multi-user-chat"));
		this.config.setLogDirectory((String) props.get(LOG_DIR_KEY));

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

			this.roomLogger = new RoomChatLogger(this.config);
			init();
		}

		this.messageModule.setChatStateAllowed((Boolean) props.get("muc-allow-chat-states"));
		this.presenceModule.setLockNewRoom((Boolean) props.get("muc-lock-new-room"));
		log.info("Tigase MUC Component ver. " + MucVersion.getVersion() + " started.");
	}

}

// ~ Formatted in Sun Code Convention

// ~ Formatted by Jindent --- http://www.jindent.com
