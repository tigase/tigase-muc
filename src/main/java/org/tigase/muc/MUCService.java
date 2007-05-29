/*
 * tigase-muc Copyright (C) 2007 by Bartosz M. Małkowski
 * 
 * This program is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the License,
 * or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with this program; if not,
 * write to the Free Software Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * 
 * $Id$
 */
package org.tigase.muc;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import tigase.conf.Configurable;
import tigase.db.RepositoryFactory;
import tigase.db.TigaseDBException;
import tigase.db.UserRepository;
import tigase.disco.ServiceEntity;
import tigase.disco.ServiceIdentity;
import tigase.disco.XMPPService;
import tigase.server.AbstractMessageReceiver;
import tigase.server.Packet;
import tigase.server.xmppsession.SessionManagerConfig;
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
 * @version $Rev$
 */
public class MUCService extends AbstractMessageReceiver implements XMPPService, Configurable {

    private final static String LETTERS_TO_UNIQUE_NAME = "abcdefghijklmnopqrstuvwxyz0123456789";

    private static final String MUC_REPO_CLASS_PROP_KEY = "muc-repo-class";

    private static final String MUC_REPO_CLASS_PROP_VAL = "tigase.db.xml.XMLRepository";

    private static final String MUC_REPO_URL_PROP_KEY = "muc-repo-url";

    private static final String MUC_REPO_URL_PROP_VAL = "muc-repository.xml";

    private static Random random = new SecureRandom();

    static Packet error400(String roomID, Packet packet) {
        return new Packet(errorPresence(roomID, packet.getElemFrom(), "modify", "400", "jid-malformed"));
    }

    static Element errorPresence(String from, String to, String type, String code, String errorElement) {
        Element p = new Element("presence");
        p.setAttribute("from", from);
        p.setAttribute("to", to);
        p.setAttribute("type", "error");

        Element error = new Element("error");
        error.setAttribute("code", code);
        error.setAttribute("type", type);
        error.addChild(new Element(errorElement, new String[] { "xmlns" },
                new String[] { "urn:ietf:params:xml:ns:xmpp-stanzas" }));
        p.addChild(error);

        return p;
    }

    private static String generateUniqueName() {
        String result = "";
        for (int i = 0; i < 32; i++) {
            result += LETTERS_TO_UNIQUE_NAME.charAt(random.nextInt(LETTERS_TO_UNIQUE_NAME.length()));
        }
        return result;
    };

    private Logger log = Logger.getLogger(this.getClass().getName());

    private UserRepository mucRepository;

    private Map<String, Room> rooms = new HashMap<String, Room>();

    private ServiceEntity serviceEntity = null;

    /**
     * Construct MUC service.
     */
    public MUCService() {

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
            hostnamesPropVal[i] = "muc." + hostnamesPropVal[i];
        }

        props.put(MUC_REPO_CLASS_PROP_KEY, MUC_REPO_CLASS_PROP_VAL);
        props.put(MUC_REPO_URL_PROP_KEY, MUC_REPO_URL_PROP_VAL);

        props.put(SessionManagerConfig.HOSTNAMES_PROP_KEY, hostnamesPropVal);
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

    /** {@inheritDoc} */
    @Override
    public void processPacket(Packet packet) {
        String roomName = JIDUtils.getNodeNick(packet.getElemTo());
        String roomHost = JIDUtils.getNodeHost(packet.getElemTo());

        String roomID = JIDUtils.getNodeID(packet.getElemTo());
        String username = JIDUtils.getNodeResource(packet.getElemTo());

        if (roomName == null && "iq".equals(packet.getElemName())
                && packet.getElement().getChild("unique", "http://jabber.org/protocol/muc#unique") != null) {
            Element iq = new Element("iq");
            iq.setAttribute("to", packet.getElemFrom());
            iq.setAttribute("from", packet.getElemTo());
            iq.setAttribute("id", packet.getElemId());
            iq.setAttribute("type", "result");

            String id;
            do {
                id = generateUniqueName();
            } while (this.rooms.containsKey(id + "@" + roomHost));

            Element unique = new Element("unique", id, new String[] { "xmlns" },
                    new String[] { "http://jabber.org/protocol/muc#unique" });
            iq.addChild(unique);
            addOutPacket(new Packet(iq));
            return;
        }

        Room room = this.rooms.get(roomID);
        List<Element> stanzasToSend = new LinkedList<Element>();
        if (room == null) {
            boolean newRoom = !this.allRooms.contains(roomID);
            room = new Room(mucRepository, roomID, packet.getElemFrom(), newRoom);
            this.rooms.put(roomID, room);
            if (newRoom) {
                stanzasToSend = room.processInitialStanza(packet.getElement());
            }
        }
        stanzasToSend.addAll(room.processStanza(packet.getElement()));

        if (stanzasToSend != null) {
            for (Element element : stanzasToSend) {
                addOutPacket(new Packet(element));
            }
        }
    }

    private Set<String> allRooms = new HashSet<String>();;

    private void readAllRomms() {
        try {
            List<String> roomsJid = this.mucRepository.getUsers();
            allRooms.clear();
            if (roomsJid != null)
                allRooms.addAll(roomsJid);
        } catch (TigaseDBException e) {
            e.printStackTrace();
        }
    }

    /** {@inheritDoc} */
    @Override
    public void setProperties(Map<String, Object> props) {
        super.setProperties(props);

        serviceEntity = new ServiceEntity(getName(), null, "Multi User Chat");
        serviceEntity.addIdentities(new ServiceIdentity("component", "generic", "Multi User Chat"));
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
        String[] hostnames = (String[]) props.get(SessionManagerConfig.HOSTNAMES_PROP_KEY);
        clearRoutings();
        for (String host : hostnames) {
            addRouting(host);
        }
        readAllRomms();
    }

}
