/*
 * DefaultStrategy.java
 *
 * Tigase Jabber/XMPP Server
 * Copyright (C) 2004-2013 "Tigase, Inc." <office@tigase.com>
 *
 */
package tigase.muc.cluster;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
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
import tigase.muc.MUCComponent;
import tigase.muc.Room;
import tigase.server.Packet;
import tigase.server.Presence;
import tigase.util.TigaseStringprepException;
import tigase.xml.Element;
import tigase.xmpp.BareJID;
import tigase.xmpp.JID;
import tigase.xmpp.XMPPImplIfc;

/**
 *
 * @author andrzej
 */
public class DefaultStrategy implements StrategyIfc, Room.RoomOccupantListener,
		InMemoryMucRepositoryClustered.RoomListener {

	private static final Logger log = Logger.getLogger(DefaultStrategy.class.getCanonicalName());
	private static final String NODE_SHUTDOWN_CMD = "muc-node-shutdown-cmd";
	private static final String OCCUPANT_ADDED_CMD = "muc-occupant-added-cmd";
	private static final String OCCUPANT_REMOVED_CMD = "muc-occupant-removed-cmd";
	private static final String PACKET_FORWARD_CMD = "muc-packet-forward-cmd";
	private static final String REQUEST_SYNC_CMD = "muc-sync-request";
	private static final String RESPONSE_SYNC_CMD = "muc-sync-response";
	private static final String ROOM_CREATED_CMD = "muc-room-created-cmd";
	private static final String ROOM_DESTROYED_CMD = "muc-room-destroyed-cmd";
	private static final int SYNC_MAX_BATCH_SIZE = 1000;
	private CopyOnWriteArrayList<JID> connectedNodes = new CopyOnWriteArrayList<JID>();
	private ClusterControllerIfc cl_controller;
	private JID localNodeJid;
	private MUCComponentClustered muc;
	private OccupantAddedCmd occupantAddedCmd = new OccupantAddedCmd();
	private OccupantRemovedCmd occupantRemovedCmd = new OccupantRemovedCmd();
	private NodeShutdownCmd nodeShutdownCmd = new NodeShutdownCmd();
	private PacketForwardCmd packetForwardCmd = new PacketForwardCmd();
	private RequestSyncCmd requestSyncCmd = new RequestSyncCmd();
	private ResponseSyncCmd responseSyncCmd = new ResponseSyncCmd();
	private RoomCreatedCmd roomCreatedCmd = new RoomCreatedCmd();
	private RoomDestroyedCmd roomDestroyedCmd = new RoomDestroyedCmd();
	private ConcurrentMap<BareJID, JID> roomsPerNode =
			new ConcurrentHashMap<BareJID, JID>();
	private ConcurrentMap<BareJID, Set<JID>> occupantsPerRoom =
			new ConcurrentHashMap<BareJID, Set<JID>>();
	private InMemoryMucRepositoryClustered mucRepository = null;

	private boolean addOccupant(BareJID roomJid, JID occupantJid) {
		Set<JID> occupants = this.occupantsPerRoom.get(roomJid);
		if (occupants == null) {
			occupants = new HashSet<JID>();
			Set<JID> oldList = this.occupantsPerRoom.putIfAbsent(roomJid, occupants);
			if (oldList != null) {
				occupants = oldList;
			}
		}
		synchronized (occupants) {
			return occupants.add(occupantJid);
		}
	}

	private boolean removeOccupant(BareJID roomJid, JID occupantJid) {
		Set<JID> occupants = this.occupantsPerRoom.get(roomJid);
		if (occupants == null) {
			return false;
		}

		synchronized (occupants) {
			return occupants.remove(occupantJid);
		}
	}

	public List<JID> getAllNodes() {
		return connectedNodes;
	}

	/**
	 * Method description
	 *
	 *
	 * @param nodeJid is a <code>JID</code>
	 */
	@Override
	public void nodeConnected(JID nodeJid) {
		boolean added = false;
		synchronized (connectedNodes) {
			if (connectedNodes.addIfAbsent(nodeJid)) {
				added = true;
				sort(connectedNodes);
			}
		}
		if (added && !localNodeJid.equals(nodeJid)) {
			requestSync(nodeJid);
		}
	}

	/**
	 * Method description
	 *
	 *
	 * @param nodeJid is a <code>JID</code>
	 */
	@Override
	public void nodeDisconnected(JID nodeJid) {
		synchronized (connectedNodes) {
			connectedNodes.remove(nodeJid);
		}
		
		int localNodeIdx = connectedNodes.indexOf(localNodeJid);
		// we need to properly handle disconnect!
		Iterator<Map.Entry<BareJID,JID>> iter = roomsPerNode.entrySet().iterator();
		while (iter.hasNext()) {
			Map.Entry<BareJID,JID> entry = iter.next();
			if (!nodeJid.equals(entry.getValue()))
				continue;
				
			iter.remove();
			BareJID roomJid = entry.getKey();			
			Set<JID> occupants = occupantsPerRoom.remove(roomJid);
			
			// spliting between nodes - we send kicks for room if only if we should
			if ((roomJid.hashCode() % connectedNodes.size()) != localNodeIdx)
				continue;			
			
			if (occupants != null) {
				synchronized(occupants) {
					for (JID occupant : occupants) {
						sendKickOnNodeDisconnect(roomJid, occupant);
					}
				}
			}
		}
	}

	@Override
	public void setClusterController(ClusterControllerIfc cl_controller) {
		if (this.cl_controller != null) {
			this.cl_controller.removeCommandListener(roomCreatedCmd);
			this.cl_controller.removeCommandListener(roomDestroyedCmd);
			this.cl_controller.removeCommandListener(occupantAddedCmd);
			this.cl_controller.removeCommandListener(occupantRemovedCmd);
			this.cl_controller.removeCommandListener(packetForwardCmd);
			this.cl_controller.removeCommandListener(requestSyncCmd);
			this.cl_controller.removeCommandListener(responseSyncCmd);
			this.cl_controller.removeCommandListener(nodeShutdownCmd);
		}
		this.cl_controller = cl_controller;
		this.cl_controller.setCommandListener(roomCreatedCmd);
		this.cl_controller.setCommandListener(roomDestroyedCmd);
		this.cl_controller.setCommandListener(occupantAddedCmd);
		this.cl_controller.setCommandListener(occupantRemovedCmd);
		this.cl_controller.setCommandListener(packetForwardCmd);
		this.cl_controller.setCommandListener(requestSyncCmd);
		this.cl_controller.setCommandListener(responseSyncCmd);
		this.cl_controller.setCommandListener(nodeShutdownCmd);
	}

	@Override
	public void setMucComponentClustered(MUCComponentClustered mucComponent) {
		setLocalNodeJid(JID.jidInstance(mucComponent.getDefHostName()));
		this.muc = mucComponent;
	}

	@Override
	public boolean processPacket(Packet packet) {
		BareJID roomJid = packet.getStanzaTo().getBareJID();
		JID nodeJid = getNodeForRoom(roomJid);

		if (localNodeJid.equals(nodeJid)) {
			return false;
		}

		cl_controller.sendToNodes(PACKET_FORWARD_CMD, packet.getElement(), 
				localNodeJid, null, nodeJid);
		return true;
	}

	@Override
	public JID getNodeForRoom(BareJID roomJid) {
		JID nodeJid = roomsPerNode.get(roomJid);
		if (nodeJid == null) {
			int hash = roomJid.hashCode();
			nodeJid = connectedNodes.get(Math.abs(hash) % connectedNodes.size());
		}
		return nodeJid;
	}

	@Override
	public void setMucRepository(InMemoryMucRepositoryClustered mucRepository) {
		this.mucRepository = mucRepository;
	}

	@Override
	public void start() {
		
	}
	
	@Override
	public void stop() {
		// if we are stopping this node, we need to notify other nodes that we 
		// will send informations about destroying/going offline of rooms 
		// hosted by this node
		List<JID> toNodes = getAllNodes();
		toNodes.remove(localNodeJid);		
		cl_controller.sendToNodes(NODE_SHUTDOWN_CMD, localNodeJid, toNodes.toArray(new JID[toNodes.size()]));
	}
	
	protected void setLocalNodeJid(JID jid) {
		this.localNodeJid = jid;
		nodeConnected(localNodeJid);
	}

	private void sendKickOnNodeDisconnect(BareJID roomJid, JID occupant) {
		try {
			Element presenceEl = new Element("presence", new String[]{"xmlns", "from", "to", "type"},
					new String[]{Presence.CLIENT_XMLNS, roomJid.toString(), occupant.toString(), "unavailable"});
			Element x = new Element("x", new String[]{"xmlns"}, new String[]{"http://jabber.org/protocol/muc#user"});
			presenceEl.addChild(x);
			Element item = new Element("item", new String[]{"role"}, new String[]{"none"});
			x.addChild(item);
			item.addChild(new Element("reason", "MUC component is disconnected."));
			x.addChild(new Element("status", new String[]{"code"}, new String[]{"307"}));
			muc.addOutPacket(Packet.packetInstance(presenceEl));
		} catch (TigaseStringprepException ex) {
			log.log(Level.FINE, "Problem on throwing out occupant {0} on node disconnection", new Object[]{occupant});
		}	
	}
	
	private void sort(List<JID> list) {
		JID[] array = list.toArray(new JID[list.size()]);

		Arrays.sort(array);
		list.clear();
		list.addAll(Arrays.asList(array));
	}
	
	@Override
	public void onRoomCreated(Room room) {
		roomsPerNode.put(room.getRoomJID(), localNodeJid);
		// notify other nodes about newly created room and it's location on 
		// node in cluster
		List<JID> toNodes = getAllNodes();
		toNodes.remove(localNodeJid);

		Map<String, String> data = new HashMap<String, String>();
		data.put("room", room.getRoomJID().toString());
		cl_controller.sendToNodes(ROOM_CREATED_CMD, data, localNodeJid,
				toNodes.toArray(new JID[toNodes.size()]));
	}

	@Override
	public void onRoomDestroyed(Room room) {
		roomsPerNode.remove(room.getRoomJID());
		// now we should notify other nodes about that

		Set<JID> removedOccupants = occupantsPerRoom.remove(room.getRoomJID());
		// now we should notify other nodes that following occupants where removed
		// hmm, maybe info sent before about room removal is enought?

		List<JID> toNodes = getAllNodes();
		toNodes.remove(localNodeJid);

		Map<String, String> data = new HashMap<String, String>();
		data.put("room", room.getRoomJID().toString());
		cl_controller.sendToNodes(ROOM_DESTROYED_CMD, data, localNodeJid,
				toNodes.toArray(new JID[toNodes.size()]));
	}

	@Override
	public void onOccupantAdded(Room room, JID occupantJid) {
		if (addOccupant(room.getRoomJID(), occupantJid)) {
			// we should notify other nodes about that
			List<JID> toNodes = getAllNodes();
			toNodes.remove(localNodeJid);

			Map<String, String> data = new HashMap<String, String>();
			data.put("room", room.getRoomJID().toString());
			data.put("occupant-jid", occupantJid.toString());
			cl_controller.sendToNodes(OCCUPANT_ADDED_CMD, data, localNodeJid,
					toNodes.toArray(new JID[toNodes.size()]));
		}
	}

	@Override
	public void onOccupantRemoved(Room room, JID occupantJid) {
		if (removeOccupant(room.getRoomJID(), occupantJid)) {
			// we should notify other nodes about that
			List<JID> toNodes = getAllNodes();
			toNodes.remove(localNodeJid);

			Map<String, String> data = new HashMap<String, String>();
			data.put("room", room.getRoomJID().toString());
			data.put("occupant-jid", occupantJid.toString());
			cl_controller.sendToNodes(OCCUPANT_REMOVED_CMD, data, localNodeJid,
					toNodes.toArray(new JID[toNodes.size()]));
		}
	}
	
	private void requestSync(JID nodeJid) {
		cl_controller.sendToNodes(REQUEST_SYNC_CMD, localNodeJid, nodeJid);
	}

	private class RoomCreatedCmd extends CommandListenerAbstract {

		public RoomCreatedCmd() {
			super(ROOM_CREATED_CMD);
		}

		@Override
		public void executeCommand(JID fromNode, Set<JID> visitedNodes,
				Map<String, String> data, Queue<Element> packets) throws ClusterCommandException {
			BareJID roomJid = BareJID.bareJIDInstanceNS(data.get("room"));
			roomsPerNode.put(roomJid, fromNode);
		}
	}

	private class RoomDestroyedCmd extends CommandListenerAbstract {

		public RoomDestroyedCmd() {
			super(ROOM_DESTROYED_CMD);
		}

		@Override
		public void executeCommand(JID fromNode, Set<JID> visitedNodes,
				Map<String, String> data, Queue<Element> packets) throws ClusterCommandException {
			BareJID roomJid = BareJID.bareJIDInstanceNS(data.get("room"));
			roomsPerNode.remove(roomJid, fromNode);
			occupantsPerRoom.remove(roomJid);
		}
	}
	
	private class NodeShutdownCmd extends CommandListenerAbstract {

		public NodeShutdownCmd() {
			super(NODE_SHUTDOWN_CMD);
		}
		
		@Override
		public void executeCommand(JID fromNode, Set<JID> visitedNodes, 
				Map<String, String> data, Queue<Element> packets) throws ClusterCommandException {
			Iterator<Map.Entry<BareJID,JID>> iter = roomsPerNode.entrySet().iterator();
			while (iter.hasNext()) {
				Map.Entry<BareJID,JID> entry = iter.next();
				if (!fromNode.equals(entry.getValue()))
					continue;
				
				iter.remove();
				occupantsPerRoom.remove(entry.getKey());
			}
		}
		
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
			addOccupant(roomJid, occupantJid);
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
			removeOccupant(roomJid, occupantJid);
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
					} catch (TigaseStringprepException ex) {
						log.warning("Addressing problem, stringprep failed for packet: " + elem);
					}
				}
			}
		}
	}
	
	private class RequestSyncCmd extends CommandListenerAbstract {
		
		public RequestSyncCmd() {
			super(REQUEST_SYNC_CMD);
		}

		@Override
		public void executeCommand(JID fromNode, Set<JID> visitedNodes,
				Map<String, String> data, Queue<Element> packets) throws ClusterCommandException {
			LinkedList<Element> localRooms = new LinkedList<Element>();
			
			for (Map.Entry<BareJID,JID> entry : roomsPerNode.entrySet()) {
				if (!localNodeJid.equals(entry.getValue()))
					continue;
		
				Element roomEl = new Element("room", new String[] { "jid" }, 
						new String[] { entry.getKey().toString() });
				
				Set<JID> occupants = occupantsPerRoom.get(entry.getKey());
				if (occupants != null && !occupants.isEmpty()) {
					synchronized (occupants) {
						for (JID occupant : occupants) {
							roomEl.addChild(new Element("occupant", occupant.toString()));
						}
					}
				}
				
				localRooms.add(roomEl);
				if (localRooms.size() > SYNC_MAX_BATCH_SIZE) {
					cl_controller.sendToNodes(RESPONSE_SYNC_CMD, packets, 
							localNodeJid, null, fromNode);
					localRooms = new LinkedList<Element>();
				}
			}
			
			if (!localRooms.isEmpty()) {
				cl_controller.sendToNodes(RESPONSE_SYNC_CMD, packets, 
						localNodeJid, null, fromNode);
			}
		}
	}
	
	private class ResponseSyncCmd extends CommandListenerAbstract {
		
		public ResponseSyncCmd() {
			super(RESPONSE_SYNC_CMD);
		}

		@Override
		public void executeCommand(JID fromNode, Set<JID> visitedNodes,
				Map<String, String> data, Queue<Element> packets) throws ClusterCommandException {
			if (packets != null && !packets.isEmpty()) {
				for (Element roomEl : packets) {
					if (roomEl.getName() == "room") {
						BareJID roomJid = BareJID.bareJIDInstanceNS(
								roomEl.getAttributeStaticStr("jid"));						
						JID oldValue = roomsPerNode.put(roomJid, fromNode);
						
						if (oldValue != null && !fromNode.equals(oldValue)) {
							log.log(Level.SEVERE, "received info about a room {0} on "
									+ "{1} but we had info about this room on node {2}", 
									new Object[] { roomJid, fromNode, oldValue });
						}
						
						List<Element> occupantsElList = roomEl.getChildren();
						if (occupantsElList != null && !occupantsElList.isEmpty()) {
							for (Element occupantEl : occupantsElList) {
								JID occupantJid = JID.jidInstanceNS(occupantEl.getCData());
								addOccupant(roomJid, occupantJid);
							}
						}
					}
				}
			}
		}
		
	}
}
