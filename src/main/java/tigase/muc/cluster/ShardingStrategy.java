/*
 * DefaultStrategy.java
 *
 * Tigase Jabber/XMPP Server
 * Copyright (C) 2004-2013 "Tigase, Inc." <office@tigase.com>
 *
 */
package tigase.muc.cluster;

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
import tigase.component.exceptions.RepositoryException;
import tigase.muc.Room;
import tigase.server.Packet;
import tigase.server.Presence;
import tigase.util.TigaseStringprepException;
import tigase.xml.Element;
import tigase.xmpp.BareJID;
import tigase.xmpp.JID;

/**
 * ShardingStrategy implements clustering strategy in which each room has assigned
 * cluster node which is responsible for particular room.
 * 
 * Limitations: not known
 * 
 * @author andrzej
 */
public class ShardingStrategy extends AbstractStrategy implements StrategyIfc, Room.RoomOccupantListener,
		InMemoryMucRepositoryClustered.RoomListener {

	private static final Logger log = Logger.getLogger(ShardingStrategy.class.getCanonicalName());
	private static final String NODE_SHUTDOWN_CMD = "muc-node-shutdown-cmd";
	private static final String RESPONSE_SYNC_CMD = "muc-sync-response";
	private static final String ROOM_CREATED_CMD = "muc-room-created-cmd";
	private static final String ROOM_DESTROYED_CMD = "muc-room-destroyed-cmd";
	private static final int SYNC_MAX_BATCH_SIZE = 1000;
	private final NodeShutdownCmd nodeShutdownCmd = new NodeShutdownCmd();
	private final RequestSyncCmd requestSyncCmd = new RequestSyncCmd();
	private final ResponseSyncCmd responseSyncCmd = new ResponseSyncCmd();
	private final RoomCreatedCmd roomCreatedCmd = new RoomCreatedCmd();
	private final RoomDestroyedCmd roomDestroyedCmd = new RoomDestroyedCmd();
	private final ConcurrentMap<BareJID, JID> roomsPerNode =
			new ConcurrentHashMap<BareJID, JID>();
	private final ConcurrentMap<BareJID, Set<JID>> occupantsPerRoom =
			new ConcurrentHashMap<BareJID, Set<JID>>();
	
	/**
	 * Method description
	 *
	 *
	 * @param nodeJid is a <code>JID</code>
	 */
	@Override
	public void nodeDisconnected(JID nodeJid) {
		super.nodeDisconnected(nodeJid);
		
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
						sendRemovalFromRoomOnNodeDisconnect(roomJid, occupant);
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
			this.cl_controller.removeCommandListener(requestSyncCmd);
			this.cl_controller.removeCommandListener(responseSyncCmd);
			this.cl_controller.removeCommandListener(nodeShutdownCmd);
		}
		super.setClusterController(cl_controller);
		this.cl_controller.setCommandListener(roomCreatedCmd);
		this.cl_controller.setCommandListener(roomDestroyedCmd);
		this.cl_controller.setCommandListener(requestSyncCmd);
		this.cl_controller.setCommandListener(responseSyncCmd);
		this.cl_controller.setCommandListener(nodeShutdownCmd);
	}	
	
	@Override
	public boolean processPacket(Packet packet) {
		BareJID roomJid = packet.getStanzaTo().getBareJID();
		JID nodeJid = getNodeForRoom(roomJid);

		if (localNodeJid.equals(nodeJid)) {
			return false;
		}

		if (log.isLoggable(Level.FINER)) {
			log.log(Level.FINER, "room = {0}, forwarding packet to node = {1}", new Object[] {
				roomJid, nodeJid });
		}
		forwardPacketToNode(nodeJid, packet);
		return true;
	}

	protected JID getNodeForRoom(BareJID roomJid) {
		JID nodeJid = roomsPerNode.get(roomJid);
		if (nodeJid == null) {
			if (log.isLoggable(Level.FINEST)) {
				log.log(Level.FINEST, "room = {0}, not created on any node", roomJid);
			}
			int hash = roomJid.hashCode();
			nodeJid = connectedNodes.get(Math.abs(hash) % connectedNodes.size());
			if (log.isLoggable(Level.FINEST)) {
				StringBuilder nodes = new StringBuilder(100);
				for (JID node : connectedNodes) {
					if (nodes.length() > 0) {
						nodes.append(",");
					}
					nodes.append(node.toString());
				}				
				log.log(Level.FINEST, "room = {0}, selected node = {1} to handle this room by hash = {2} from {3}", 
						new Object[] { roomJid, nodeJid, hash, nodes });
			}
		}
		if (log.isLoggable(Level.FINEST)) {
			log.log(Level.FINEST, "room = {0}, selected node = {1} to handle this room", 
					new Object[] { roomJid, nodeJid });
		}
		return nodeJid;
	}
	
	@Override
	public void setMucRepository(InMemoryMucRepositoryClustered mucRepository) {
		super.setMucRepository(mucRepository);
		mucRepository.setRoomListener(this);
		mucRepository.setRoomOccupantListener(this);
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
		
		if (log.isLoggable(Level.FINEST)) {
			StringBuilder buf = new StringBuilder(100);
			for (JID node : toNodes) {
				if (buf.length() > 0) {
					buf.append(",");
				}
				buf.append(node.toString());
			}
			log.log(Level.FINEST, "room = {0}, notifing nodes [{1}] that room is created",
					new Object[] { room.getRoomJID(), buf });
		}
		
		cl_controller.sendToNodes(ROOM_CREATED_CMD, data, localNodeJid,
				toNodes.toArray(new JID[toNodes.size()]));
	}

	@Override
	public void onRoomDestroyed(Room room, Element destroyElement) {
		roomsPerNode.remove(room.getRoomJID());
		// now we should notify other nodes about that

		Set<JID> removedOccupants = occupantsPerRoom.remove(room.getRoomJID());
		// now we should notify other nodes that following occupants where removed
		// hmm, maybe info sent before about room removal is enought?

		List<JID> toNodes = getAllNodes();
		toNodes.remove(localNodeJid);

		Map<String, String> data = new HashMap<String, String>();
		data.put("room", room.getRoomJID().toString());
		
		if (log.isLoggable(Level.FINEST)) {
			StringBuilder buf = new StringBuilder(100);
			for (JID node : toNodes) {
				if (buf.length() > 0) {
					buf.append(",");
				}
				buf.append(node.toString());
			}
			log.log(Level.FINEST, "room = {0}, notifing nodes [{1}] that room is destroyed",
					new Object[] { room.getRoomJID(), buf });
		}		
		
		cl_controller.sendToNodes(ROOM_DESTROYED_CMD, data, localNodeJid,
				toNodes.toArray(new JID[toNodes.size()]));
	}
	
	@Override
	public void onOccupantChangedPresence(Room room, JID occupantJid, String nickname, Element presence, boolean newOccupant) {		
		// nothing to do here
	}	
	
	@Override
	protected boolean addOccupant(BareJID node, BareJID roomJid, JID occupantJid, String nickname) {
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
	
	@Override
	protected boolean removeOccupant(BareJID node, BareJID roomJid, JID occupantJid) {
		Set<JID> occupants = this.occupantsPerRoom.get(roomJid);
		if (occupants == null) {
			return false;
		}

		synchronized (occupants) {
			return occupants.remove(occupantJid);
		}
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
			if (log.isLoggable(Level.FINEST)) {
				log.log(Level.FINEST, "room = {0}, received notification that room {1} was created at node {2}", 
						new Object[]{ roomJid, roomJid, fromNode});
			}
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
			if (log.isLoggable(Level.FINEST)) {
				log.log(Level.FINEST, "room = {0}, received notification that room {1} was destroyed at node {2}", 
						new Object[]{ roomJid, roomJid, fromNode});
			}
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
		
				// for each room hosted locally we send
				Element roomEl = new Element("room", new String[] { "jid" }, 
						new String[] { entry.getKey().toString() });
				
				try {
					// all it's occupants
					Room room = mucRepository.getRoom(entry.getKey());
					Set<JID> occupants = occupantsPerRoom.get(entry.getKey());
					if (occupants != null && !occupants.isEmpty()) {
						synchronized (occupants) {
							for (JID occupant : occupants) {
								String nickname = room.getOccupantsNickname(occupant);
								
								// it is not possible to have occupants without nickname
								// so we should skip synchronization as null value of occupants
								// nickname means that occupant is already gone
								if (nickname == null)
									continue;
								
								Element occupantEl = new Element("occupant", occupant.toString());
								occupantEl.addAttribute("nickname", nickname);
								roomEl.addChild(occupantEl);
							}
						}
					}
				}
				catch (Exception ex) {
					log.log(Level.SEVERE, "exception during cluster nodes synchronization", ex);
				}
				
				localRooms.add(roomEl);
				if (localRooms.size() > SYNC_MAX_BATCH_SIZE) {
					cl_controller.sendToNodes(RESPONSE_SYNC_CMD, localRooms, 
							localNodeJid, null, fromNode);
					localRooms = new LinkedList<Element>();
				}
			}
			
			if (!localRooms.isEmpty()) {
				cl_controller.sendToNodes(RESPONSE_SYNC_CMD, localRooms, 
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
								String nickname = occupantEl.getAttributeStaticStr("nickname");
								addOccupant(fromNode.getBareJID(), roomJid, occupantJid, nickname);
							}
						}
					}
				}
			}
		}
		
	}
}
