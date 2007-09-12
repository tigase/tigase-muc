/*
 * Tigase Jabber/XMPP Multi User Chatroom Component
 * Copyright (C) 2007 "Bartosz M. Małkowski" <bartosz.malkowski@tigase.org>
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
 * Last modified by $Author: $
 * $Date: $
 */
package tigase.muc;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import tigase.conf.Configurable;
import tigase.db.RepositoryFactory;
import tigase.db.TigaseDBException;
import tigase.db.UserExistsException;
import tigase.db.UserNotFoundException;
import tigase.db.UserRepository;
import tigase.disco.ServiceEntity;
import tigase.disco.ServiceIdentity;
import tigase.disco.XMPPService;
import tigase.muc.modules.BroadcastMessageModule;
import tigase.muc.modules.ChangeSubjectModule;
import tigase.muc.modules.InvitationModule;
import tigase.muc.modules.RoomModule;
import tigase.muc.modules.PresenceModule;
import tigase.muc.modules.PrivateMessageModule;
import tigase.muc.modules.admin.AdminGetModule;
import tigase.muc.modules.admin.AdminSetModule;
import tigase.muc.modules.owner.OwnerGetModule;
import tigase.muc.modules.owner.OwnerSetModule;
import tigase.muc.xmpp.JID;
import tigase.muc.xmpp.stanzas.IQ;
import tigase.muc.xmpp.stanzas.IQType;
import tigase.server.AbstractMessageReceiver;
import tigase.server.Packet;
import tigase.util.DNSResolver;
import tigase.util.JIDUtils;
import tigase.xml.Element;

/**
 * Implements MUC service for tigase server.
 * <p>
 * Created: 2007-01-24 13:02:15
 * </p>
 * 
 * @author bmalkow
 * @version $Rev:43 $
 */
public class MUCService extends AbstractMessageReceiver implements XMPPService, Configurable, RoomListener {

	private static final String MUC_REPO_CLASS_PROP_KEY = "muc-repo-class";

	private static final String MUC_REPO_CLASS_PROP_VAL = "tigase.db.xml.XMLRepository";

	private static final String MUC_REPO_URL_PROP_KEY = "muc-repo-url";

	private static final String MUC_REPO_URL_PROP_VAL = "muc-repository.xml";

	public static Element errorPresence(JID from, JID to, String type, String code, String errorElement) {
		Element p = new Element("presence");
		p.setAttribute("from", from.toString());
		p.setAttribute("to", to.toString());
		p.setAttribute("type", "error");

		Element error = new Element("error");
		error.setAttribute("code", code);
		error.setAttribute("type", type);
		error.addChild(new Element(errorElement, new String[] { "xmlns" }, new String[] { "urn:ietf:params:xml:ns:xmpp-stanzas" }));
		p.addChild(error);

		return p;
	}

	private Logger log = Logger.getLogger(this.getClass().getName());

	private UserRepository mucRepository;

	private ModulesProcessor processor;

	private RoomsContainer rooms;

	private ServiceEntity serviceEntity = null;

	/**
	 * Construct MUC service.
	 */
	public MUCService() {
		log.info("Creating tigase-muc ver." + MucVersion.getVersion() + " Service.");

		this.processor = new ModulesProcessor(this);
	}

	/** {@inheritDoc} */
	@Override
	public Map<String, Object> getDefaults(Map<String, Object> params) {
		Map<String, Object> props = super.getDefaults(params);
		String[] hostnamesPropVal = null;
		if (params.get("--virt-hosts") != null) {
			hostnamesPropVal = ((String) params.get("--virt-hosts")).split(",");
		} else {
			hostnamesPropVal = DNSResolver.getDefHostNames();
		}
		for (int i = 0; i < hostnamesPropVal.length; i++) {
			if (((String) params.get("config-type")).equals(GEN_CONFIG_COMP)) {
				// This is specialized configuration for a single
				// external component and on specialized component like MUC
				hostnamesPropVal[i] = hostnamesPropVal[i];
			} else {
				hostnamesPropVal[i] = "muc." + hostnamesPropVal[i];
			}
		}

		// By default use the same repository as all other components:
		String repo_class = XML_REPO_CLASS_PROP_VAL;
		String repo_uri = XML_REPO_URL_PROP_VAL;
		String conf_db = null;
		if (params.get(GEN_USER_DB) != null) {
			conf_db = (String) params.get(GEN_USER_DB);
		} // end of if (params.get(GEN_USER_DB) != null)
		if (conf_db != null) {
			if (conf_db.equals("mysql")) {
				repo_class = MYSQL_REPO_CLASS_PROP_VAL;
				repo_uri = MYSQL_REPO_URL_PROP_VAL;
			}
			if (conf_db.equals("pgsql")) {
				repo_class = PGSQL_REPO_CLASS_PROP_VAL;
				repo_uri = PGSQL_REPO_URL_PROP_VAL;
			}
		} // end of if (conf_db != null)
		if (params.get(GEN_USER_DB_URI) != null) {
			repo_uri = (String) params.get(GEN_USER_DB_URI);
		} // end of if (params.get(GEN_USER_DB_URI) != null)
		props.put(MUC_REPO_CLASS_PROP_KEY, repo_class);
		props.put(MUC_REPO_URL_PROP_KEY, repo_uri);

		props.put(HOSTNAMES_PROP_KEY, hostnamesPropVal);
		return props;
	}

	public List<Element> getDiscoFeatures() {
		return null;
	}

	public Element getDiscoInfo(String node, String jid) {
		if (jid != null && JIDUtils.getNodeHost(jid).startsWith(getName() + ".")) {
			return serviceEntity.getDiscoInfo(node);
		}
		return null;
	}

	public List<Element> getDiscoItems(String node, String jid) {
		if (JIDUtils.getNodeHost(jid).startsWith(getName() + ".")) {
			return serviceEntity.getDiscoItems(node, null);
		} else {
			return Arrays.asList(serviceEntity.getDiscoItem(null, getName() + "." + jid));
		}
	}

	public String myDomain() {
		return getName() + "." + getDefHostName();
	};

	/*
	 * (non-Javadoc)
	 * 
	 * @see tigase.muc.RoomListener#onConfigurationChange(tigase.muc.Room)
	 */
	@Override
	public void onConfigurationChange(RoomContext room) {
		ServiceEntity ent = this.serviceEntity.findNode(room.getRoomID());
		if (ent != null) {
			this.serviceEntity.removeItems(ent);
		}
		this.rooms.configRoomDiscovery(room);
	}

	@Override
	public void onDestroy(RoomContext room) {
		ServiceEntity ent = this.serviceEntity.findNode(room.getRoomID());
		if (ent != null) {
			this.serviceEntity.removeItems(ent);
		}
		this.rooms.destroyRoom(JID.fromString(room.getRoomID()));
		try {
			this.mucRepository.removeSubnode(myDomain(), room.getRoomID());
		} catch (UserNotFoundException e) {
			e.printStackTrace();
		} catch (TigaseDBException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void onOccupantLeave(RoomContext room) {
		int c = room.getOccupantsByJID().size();
		if (c == 0) {
			this.rooms.removeRoom(JID.fromString(room.getRoomID()));
		}
	}

	private Element processDisco(IQ iq) {
		Element queryInfo = iq.getChild("query", "http://jabber.org/protocol/disco#info");
		Element queryItems = iq.getChild("query", "http://jabber.org/protocol/disco#items");

		String jid = iq.getTo().getBareJID().toString();

		if (queryInfo != null) {
			String node = queryInfo.getAttribute("node");
			Element result = getDiscoInfo(node, jid);
			return result;
		} else if (queryItems != null) {
			String node = queryItems.getAttribute("node");
			Element result = serviceEntity.getDiscoItem(node, jid);
			return result;
		}

		return null;
	}

	/** {@inheritDoc} */
	@Override
	public void processPacket(Packet packet) {
		try {
			String roomID = JIDUtils.getNodeID(packet.getElemTo());
			// String username = JIDUtils.getNodeResource(packet.getElemTo());

			if ("iq".equals(packet.getElemName()) && (packet.getElement().getChild("query", "http://jabber.org/protocol/disco#info") != null)
					|| packet.getElement().getChild("query", "http://jabber.org/protocol/disco#items") != null) {

				Packet result = packet.okResult(processDisco(new IQ(packet.getElement())), 0);
				addOutPacket(result);
				return;
			}

			List<Element> stanzasToSend = new LinkedList<Element>();

			RoomContext room = null;
			if (roomID != null) {
				room = this.rooms.getRoomContext(roomID);
				if (room == null && "presence".equals(packet.getElemName())) {
					boolean newRoom = !this.rooms.isRoomExists(JID.fromString(roomID));
					room = new RoomContext(myDomain(), roomID, mucRepository, JID.fromString(packet.getElemFrom()), newRoom);
					this.rooms.addRoom(room);
				} else if (room == null && !"presence".equals(packet.getElemName())) {
					addOutPacket(packet.errorResult("cancel", "item-not-found", null, true));
					return;
				}
			}

			List<Element> result = this.processor.processStanza(room, this.rooms, packet.getElement());
			stanzasToSend.addAll(result);

			if (stanzasToSend != null && stanzasToSend.size() > 0) {
				for (Element element : stanzasToSend) {
					addOutPacket(new Packet(element));
				}
			} else {
				addOutPacket(packet.errorResult("cancel", "feature-not-implemented", "Stanza is not processed", true));
			}
		} catch (Exception e) {
			e.printStackTrace();
			log.throwing("Muc Service", "processPacket", e);
		}
	}

	/** {@inheritDoc} */
	@Override
	public void setProperties(Map<String, Object> props) {
		super.setProperties(props);

		serviceEntity = new ServiceEntity(getName(), null, "Multi User Chat");
		serviceEntity.addIdentities(new ServiceIdentity("conference", "text", "Multi User Chat"));
		serviceEntity.addFeatures("http://jabber.org/protocol/muc", "muc_rooms");

		log.config("Register Service discovery " + serviceEntity.getJID());

		try {
			String cls_name = (String) props.get(MUC_REPO_CLASS_PROP_KEY);
			String res_uri = (String) props.get(MUC_REPO_URL_PROP_KEY);

			mucRepository = RepositoryFactory.getUserRepository("muc", cls_name, res_uri);
			mucRepository.initRepository(res_uri);

			log.config("Initialized " + cls_name + " as user repository: " + res_uri);
		} catch (Exception e) {
			log.severe("Can't initialize user repository: " + e);
			e.printStackTrace();
			System.exit(1);
		}
		String[] hostnames = (String[]) props.get(HOSTNAMES_PROP_KEY);
		clearRoutings();
		for (String host : hostnames) {
			addRouting(host);
		}
		this.rooms = new RoomsContainer(myDomain(), this.mucRepository, this.serviceEntity);
		this.rooms.readAllRomms();

		log.info("MUC Service started.");
		System.out.println(".");
	}
}