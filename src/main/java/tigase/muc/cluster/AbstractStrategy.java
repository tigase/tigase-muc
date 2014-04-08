/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package tigase.muc.cluster;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import tigase.cluster.api.ClusterCommandException;
import tigase.cluster.api.ClusterControllerIfc;
import tigase.cluster.api.CommandListenerAbstract;
import tigase.muc.Room;
import tigase.server.Packet;
import tigase.server.Presence;
import tigase.util.TigaseStringprepException;
import tigase.xml.Element;
import tigase.xmpp.BareJID;
import tigase.xmpp.JID;

/**
 *
 * @author andrzej
 */
public abstract class AbstractStrategy implements StrategyIfc, Room.RoomOccupantListener {
	
	private static final Logger log = Logger.getLogger(AbstractStrategy.class.getCanonicalName());
	
	private static final String PACKET_FORWARD_CMD = "muc-packet-forward-cmd";	
	private static final String OCCUPANT_ADDED_CMD = "muc-occupant-added-cmd";
	private static final String OCCUPANT_REMOVED_CMD = "muc-occupant-removed-cmd";	
	protected static final String REQUEST_SYNC_CMD = "muc-sync-request";

	protected final CopyOnWriteArrayList<JID> connectedNodes = new CopyOnWriteArrayList<JID>();
	protected ClusterControllerIfc cl_controller;
	protected JID localNodeJid;
	protected MUCComponentClustered muc;
	protected InMemoryMucRepositoryClustered mucRepository = null;	

	private final OccupantAddedCmd occupantAddedCmd = new OccupantAddedCmd();
	private final OccupantRemovedCmd occupantRemovedCmd = new OccupantRemovedCmd();
	private final PacketForwardCmd packetForwardCmd = new PacketForwardCmd();
	
	@Override
	public List<JID> getAllNodes() {
		return new ArrayList<>(connectedNodes);
	}
	
	/**
	 * Method description
	 *
	 *
	 * @param nodeJid is a <code>JID</code>
	 */
	@Override
	public boolean nodeConnected(JID nodeJid) {
		boolean added = false;
		synchronized (connectedNodes) {
			if (connectedNodes.addIfAbsent(nodeJid)) {
				added = true;
				sort(connectedNodes);
			}
			if (log.isLoggable(Level.FINEST)) {
				log.log(Level.FINEST, "node {0} added to list of connected nodes", nodeJid);
			}
		}
		if (added && !localNodeJid.equals(nodeJid)) {
			requestSync(nodeJid);
		}		
		return added;
	}
	
	@Override
	public void nodeDisconnected(JID nodeJid) {
		synchronized (connectedNodes) {
			connectedNodes.remove(nodeJid);
			if (log.isLoggable(Level.FINEST)) {
				log.log(Level.FINEST, "node {0} removed from list of connected nodes", nodeJid);
			}			
		}
	}
	
	@Override
	public void onOccupantAdded(Room room, JID occupantJid) {
		String nickname = room.getOccupantsNickname(occupantJid);
		if (addOccupant(localNodeJid.getBareJID(), room.getRoomJID(), occupantJid, nickname)) {
			// we should notify other nodes about that
			List<JID> toNodes = getAllNodes();
			toNodes.remove(localNodeJid);

			Map<String, String> data = new HashMap<String, String>();
			data.put("room", room.getRoomJID().toString());
			data.put("occupant-jid", occupantJid.toString());
			data.put("occupant-nickname", nickname);
			
			if (log.isLoggable(Level.FINEST)) {
				StringBuilder buf = new StringBuilder(100);
				for (JID node : toNodes) {
					if (buf.length() > 0) {
						buf.append(",");
					}
					buf.append(node.toString());
				}
				log.log(Level.FINEST, "room = {0}, notifing nodes [{1}] that occupant {2} joined room {3}",
						new Object[]{room.getRoomJID(), buf, occupantJid, room.getRoomJID()});
			}
		
			cl_controller.sendToNodes(OCCUPANT_ADDED_CMD, data, localNodeJid,
					toNodes.toArray(new JID[toNodes.size()]));
		}
	}

	@Override
	public void onOccupantRemoved(Room room, JID occupantJid) {
		if (removeOccupant(localNodeJid.getBareJID(), room.getRoomJID(), occupantJid)) {
			// we should notify other nodes about that
			List<JID> toNodes = getAllNodes();
			toNodes.remove(localNodeJid);

			Map<String, String> data = new HashMap<String, String>();
			data.put("room", room.getRoomJID().toString());
			data.put("occupant-jid", occupantJid.toString());
			
			if (log.isLoggable(Level.FINEST)) {
				StringBuilder buf = new StringBuilder(100);
				for (JID node : toNodes) {
					if (buf.length() > 0) {
						buf.append(",");
					}
					buf.append(node.toString());
				}
				log.log(Level.FINEST, "room = {0}, notifing nodes [{1}] that occupant {2} left room {3}",
						new Object[]{room.getRoomJID(), buf, occupantJid, room.getRoomJID()});
			}
			
			cl_controller.sendToNodes(OCCUPANT_REMOVED_CMD, data, localNodeJid,
					toNodes.toArray(new JID[toNodes.size()]));
		}
	}	
	
	protected void sendRemovalFromRoomOnNodeDisconnect(BareJID roomJid, JID occupant) {
		sendRemovalFromRoomOnNodeDisconnect(roomJid.toString(), occupant);
	}
	protected void sendRemovalFromRoomOnNodeDisconnect(JID roomJid, JID occupant) {
		sendRemovalFromRoomOnNodeDisconnect(roomJid.toString(), occupant);
	}
	protected void sendRemovalFromRoomOnNodeDisconnect(String roomJid, JID occupant) {
		try {
			Element presenceEl = new Element("presence", new String[]{"xmlns", "from", "to", "type"},
					new String[]{Presence.CLIENT_XMLNS, roomJid.toString(), occupant.toString(), "unavailable"});
			Element x = new Element("x", new String[]{"xmlns"}, new String[]{"http://jabber.org/protocol/muc#user"});
			presenceEl.addChild(x);
			Element item = new Element("item", new String[]{"role"}, new String[]{"none"});
			x.addChild(item);
			item.addChild(new Element("reason", "MUC component is disconnected."));
			x.addChild(new Element("status", new String[]{"code"}, new String[]{"332"}));
			muc.addOutPacket(Packet.packetInstance(presenceEl));
		} catch (TigaseStringprepException ex) {
			log.log(Level.FINE, "Problem on throwing out occupant {0} on node disconnection", new Object[]{occupant});
		}	
	}
	
	@Override
	public void setClusterController(ClusterControllerIfc cl_controller) {
		if (this.cl_controller != null) {
			this.cl_controller.removeCommandListener(occupantAddedCmd);
			this.cl_controller.removeCommandListener(occupantRemovedCmd);			
			this.cl_controller.removeCommandListener(packetForwardCmd);
		}
		this.cl_controller = cl_controller;
		if (cl_controller != null) {
			this.cl_controller.setCommandListener(occupantAddedCmd);
			this.cl_controller.setCommandListener(occupantRemovedCmd);
			this.cl_controller.setCommandListener(packetForwardCmd);
		}
	}

	@Override
	public void setMucComponentClustered(MUCComponentClustered mucComponent) {
		setLocalNodeJid(JID.jidInstance(mucComponent.getDefHostName()));
		this.muc = mucComponent;
	}

	@Override
	public void setMucRepository(InMemoryMucRepositoryClustered mucRepository) {
		this.mucRepository = mucRepository;
	}	
	
	protected void forwardPacketToNode(JID nodeJid, Packet packet) {
		cl_controller.sendToNodes(PACKET_FORWARD_CMD, packet.getElement(), 
				localNodeJid, null, nodeJid);		
	}
	
	protected void setLocalNodeJid(JID jid) {
		this.localNodeJid = jid;
		nodeConnected(localNodeJid);
	}
	
	private void sort(List<JID> list) {
		JID[] array = list.toArray(new JID[list.size()]);

		Arrays.sort(array);
		list.clear();
		list.addAll(Arrays.asList(array));
	}	
	
	abstract protected boolean addOccupant(BareJID node, BareJID roomJid, JID occupantJid, String nickname);

	abstract protected boolean removeOccupant(BareJID node, BareJID roomJid, JID occupantJid);
	
	protected void requestSync(JID nodeJid) {
		cl_controller.sendToNodes(REQUEST_SYNC_CMD, localNodeJid, nodeJid);
	}
	
	private class OccupantAddedCmd extends CommandListenerAbstract {

		public OccupantAddedCmd() {
			super(OCCUPANT_ADDED_CMD);
		}

		@Override
		public void executeCommand(JID fromNode, Set<JID> visitedNodes,
				Map<String, String> data, Queue<Element> packets) throws ClusterCommandException {
			BareJID roomJid = BareJID.bareJIDInstanceNS(data.get("room"));
			JID occupantJid = JID.jidInstanceNS(data.get("occupant-jid"));
			String nickname = data.get("occupant-nickname");
			addOccupant(fromNode.getBareJID(), roomJid, occupantJid, nickname);
			if (log.isLoggable(Level.FINEST)) {
				log.log(Level.FINEST, "room = {0}, received notification that occupant {1} joined room {2} at node {3}", 
						new Object[]{ roomJid, occupantJid, roomJid, fromNode});
			}
		}
	}

	private class OccupantRemovedCmd extends CommandListenerAbstract {

		public OccupantRemovedCmd() {
			super(OCCUPANT_REMOVED_CMD);
		}

		@Override
		public void executeCommand(JID fromNode, Set<JID> visitedNodes,
				Map<String, String> data, Queue<Element> packets) throws ClusterCommandException {
			BareJID roomJid = BareJID.bareJIDInstanceNS(data.get("room"));
			JID occupantJid = JID.jidInstanceNS(data.get("occupant-jid"));
			removeOccupant(fromNode.getBareJID(), roomJid, occupantJid);
			if (log.isLoggable(Level.FINEST)) {
				log.log(Level.FINEST, "room = {0}, received notification that occupant {1} left room {2} at node {3}", 
						new Object[]{ roomJid, occupantJid, roomJid, fromNode});
			}
		}
	}
	
	private class PacketForwardCmd extends CommandListenerAbstract {

		public PacketForwardCmd() {
			super(PACKET_FORWARD_CMD);
		}

		@Override
		public void executeCommand(JID fromNode, Set<JID> visitedNodes,
				Map<String, String> data, Queue<Element> packets) throws ClusterCommandException {
			if (packets != null && !packets.isEmpty()) {
				for (Element elem : packets) {
					try {
						Packet packet = Packet.packetInstance(elem);
						muc.processPacket(packet);
						if (log.isLoggable(Level.FINEST)) {
							log.log(Level.FINEST, "received packet {0} forwarded from node {1}",
									new Object[]{ packet, fromNode });
						}
					} catch (TigaseStringprepException ex) {
						log.warning("Addressing problem, stringprep failed for packet: " + elem);
					}
				}
			}
		}
	}
		
}
