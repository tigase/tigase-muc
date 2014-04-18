/*
 * ClusteredRoomStrategy.java
 *
 * Tigase Jabber/XMPP Server
 * Copyright (C) 2004-2014 "Tigase, Inc." <office@tigase.com>
 *
 */

package tigase.muc.cluster;

import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import tigase.cluster.api.ClusterCommandException;
import tigase.cluster.api.ClusterControllerIfc;
import tigase.cluster.api.CommandListenerAbstract;
import tigase.component.exceptions.RepositoryException;
import tigase.muc.Affiliation;
import tigase.muc.Role;
import tigase.muc.Room;
import tigase.muc.RoomConfig;
import tigase.muc.exceptions.MUCException;
import tigase.muc.modules.GroupchatMessageModule;
import tigase.muc.modules.PresenceModule;
import tigase.muc.modules.PresenceModule.PresenceWrapper;
import tigase.muc.modules.PresenceModuleImpl;
import tigase.server.Packet;
import tigase.util.TigaseStringprepException;
import tigase.xml.Element;
import tigase.xmpp.BareJID;
import tigase.xmpp.JID;

/**
 * ClusteredRoomStrategy implements strategy which allows to create rooms with
 * large number of occupants as each cluster node will process packets related
 * only to occupants connected to this node.
 * 
 * Limitations:
 * - every room needs to be persistent
 * - possible issues with changing affiliations or room configuration
 * 
 * @author andrzej
 */
public class ClusteredRoomStrategy extends AbstractStrategy implements StrategyIfc,
		InMemoryMucRepositoryClustered.RoomListener, Room.RoomListener {

	private static final Logger log = Logger.getLogger(ClusteredRoomStrategy.class.getCanonicalName());
	
	private static final String OCCUPANT_PRESENCE_CMD = "muc-occupant-presence-cmd";
	private static final String RESPONSE_SYNC_CMD = "muc-sync-response";	
	private static final String ROOM_CREATED_CMD = "muc-room-created-cmd";
	private static final String ROOM_DESTROYED_CMD = "muc-room-destroyed-cmd";
	private static final String ROOM_MESSAGE_CMD = "muc-room-message-cmd";
	private static final int SYNC_MAX_BATCH_SIZE = 1000;
	
	private final OccupantChangedPresenceCmd occupantChangedPresenceCmd = new OccupantChangedPresenceCmd();
	private final RoomCreatedCmd roomCreatedCmd = new RoomCreatedCmd();
	private final RoomDestroyedCmd roomDestroyedCmd = new RoomDestroyedCmd();
	private final RoomMessageCmd roomMessageCmd = new RoomMessageCmd();
	private final RequestSyncCmd requestSyncCmd = new RequestSyncCmd();
	private final ResponseSyncCmd responseSyncCmd = new ResponseSyncCmd();
	
	private final ConcurrentHashMap<BareJID,ConcurrentMap<JID,ConcurrentMap<BareJID,String>>> occupantsPerNode = new ConcurrentHashMap<>();
	
	@Override
	public void nodeDisconnected(JID nodeJid) {
		super.nodeDisconnected(nodeJid);
		
		ConcurrentMap<JID,ConcurrentMap<BareJID,String>> nodeOccupants = occupantsPerNode.remove(nodeJid.getBareJID());
		if (nodeOccupants == null)
			return;
		
		int localNodeIdx = connectedNodes.indexOf(localNodeJid);
		int nodesCount = connectedNodes.size();
		for (Map.Entry<JID,ConcurrentMap<BareJID,String>> e : nodeOccupants.entrySet()) {
			JID occupant = e.getKey();
			// send removal if occupant is not local and if it's hashcode matches this node
			boolean sendRemovalToOccupant = !muc.isLocalDomain(occupant.getDomain()) 
					&& (occupant.hashCode() % nodesCount) == localNodeIdx;
			
			Map<BareJID,String> rooms = e.getValue();
			if (rooms == null)
				continue;
			for (BareJID roomJid : rooms.keySet()) {
				try {
					Room room = mucRepository.getRoom(roomJid);
					// notify occupants of this room on this node that occupant was removed
					for (String nickname : room.getOccupantsNicknames()) {
						Collection<JID> jids = room.getOccupantsJidsByNickname(nickname);
						for (JID jid : jids) {
							sendRemovalFromRoomOnNodeDisconnect(JID.jidInstanceNS(roomJid, rooms.get(roomJid)), jid);
						}
					}
					if (sendRemovalToOccupant) {
						sendRemovalFromRoomOnNodeDisconnect(roomJid.toString(), occupant);
					}
				} catch (Exception ex) {
					log.log(Level.SEVERE, "exception retrieving occupants for room " + roomJid, ex);
				}
			}
		}
	}
	
	@Override
	public boolean processPacket(Packet packet) {
		JID from = packet.getStanzaFrom();
		BareJID nodeJid = getNodeForJID(from);
		if (nodeJid != null) {
			// we need to forward this packet to this node
			if (log.isLoggable(Level.FINER)) {
				log.log(Level.FINER, "forwarding packet to node = {1}", new Object[] {
					nodeJid });
			}			
			forwardPacketToNode(JID.jidInstance(nodeJid), packet);
			return true;
		}
		return false;
	}

	@Override
	public void setMucRepository(InMemoryMucRepositoryClustered mucRepository) {
		super.setMucRepository(mucRepository);
		mucRepository.setRoomListener(this);
		mucRepository.setRoomOccupantListener(this);
		try {
			// this clustering strategy requires that all rooms are persistent as
			// none of nodes knows about every occupants
			RoomConfig roomConfig = mucRepository.getDefaultRoomConfig();
			roomConfig.setValue(RoomConfig.MUC_ROOMCONFIG_PERSISTENTROOM_KEY, true);
		}
		catch (RepositoryException ex) {
			
		}
	}

	@Override
	public void start() {
	}

	@Override
	public void stop() {
	}
	
	@Override
	public void setClusterController(ClusterControllerIfc cl_controller) {
		if (this.cl_controller != null) {
			this.cl_controller.removeCommandListener(requestSyncCmd);
			this.cl_controller.removeCommandListener(responseSyncCmd);
			this.cl_controller.removeCommandListener(occupantChangedPresenceCmd);
			this.cl_controller.removeCommandListener(roomCreatedCmd);
			this.cl_controller.removeCommandListener(roomDestroyedCmd);
			this.cl_controller.removeCommandListener(roomMessageCmd);
		}
		super.setClusterController(cl_controller);
		if (cl_controller != null) {
			cl_controller.setCommandListener(requestSyncCmd);
			cl_controller.setCommandListener(responseSyncCmd);
			cl_controller.setCommandListener(occupantChangedPresenceCmd);
			cl_controller.setCommandListener(roomCreatedCmd);
			cl_controller.setCommandListener(roomDestroyedCmd);
			cl_controller.setCommandListener(roomMessageCmd);
		}
	}

	@Override
	public void onOccupantChangedPresence(Room room, JID occupantJid, String nickname, Element presence, boolean newOccupant) {
		List<JID> toNodes = getAllNodes();
		toNodes.remove(localNodeJid);
		if (occupantJid != null && presence == null) {
			presence = new Element("presence", new String[] { "type", "xmlns" }, new String[] { "unavailable", Packet.CLIENT_XMLNS });
		}
		if (occupantJid == null) occupantJid = JID.jidInstanceNS(presence.getAttributeStaticStr("from"));
		Affiliation affiliation = room.getAffiliation(occupantJid.getBareJID());
		Role role = room.getRole(nickname);
		
		Map<String,String> data = new HashMap<String,String>();
		data.put("room", room.getRoomJID().toString());
		data.put("occupantJid", occupantJid.toString());
		data.put("nickname", nickname);
		data.put("affiliation", affiliation.name());
		data.put("role", role.name());
		if (newOccupant)
			data.put("new-occupant", String.valueOf(newOccupant));
		
		if (log.isLoggable(Level.FINEST)) {
			StringBuilder buf = new StringBuilder(100);
			for (JID node : toNodes) {
				if (buf.length() > 0) {
					buf.append(",");
				}
				buf.append(node.toString());
			}
			log.log(Level.FINEST, "room = {0}, notifing nodes [{1}] that occupant {2} in room {3} changed presence = {4}",
					new Object[]{room.getRoomJID(), buf, occupantJid, room.getRoomJID(), presence});
		}
		
		cl_controller.sendToNodes(OCCUPANT_PRESENCE_CMD, data, presence, localNodeJid, null, toNodes.toArray(new JID[toNodes.size()]));
	}
	
	@Override
	public void onRoomCreated(Room room) {
		List<JID> toNodes = getAllNodes();
		toNodes.remove(localNodeJid);
		
		Map<String, String> data = new HashMap<String, String>();
		data.put("room", room.getRoomJID().toString());
		data.put("creator", room.getCreatorJid().toString());
		
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
		
		cl_controller.sendToNodes(ROOM_DESTROYED_CMD, data, destroyElement, localNodeJid, null,
				toNodes.toArray(new JID[toNodes.size()]));	
	}

	@Override
	public void onChangeSubject(Room room, String nick, String newSubject, Date changeDate) {
//		throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
	}

	@Override
	public void onSetAffiliation(Room room, BareJID jid, Affiliation newAffiliation) {
//		throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
	}

	@Override
	public void onMessageToOccupants(Room room, JID from, Element[] contents) {
		List<JID> toNodes = getAllNodes();
		toNodes.remove(localNodeJid);

		Map<String, String> data = new HashMap<String, String>();
		data.put("room", room.getRoomJID().toString());
		data.put("from", from.toString());
		
		Element message = new Element("message");
		for (Element content : contents) {
			message.addChild(content);
		}

		if (log.isLoggable(Level.FINEST)) {
			StringBuilder buf = new StringBuilder(100);
			for (JID node : toNodes) {
				if (buf.length() > 0) {
					buf.append(",");
				}
				buf.append(node.toString());
			}
			log.log(Level.FINEST, "room = {0}, notifing nodes [{1}] about new message",
					new Object[] { room.getRoomJID(), buf });
		}		
		
		cl_controller.sendToNodes(ROOM_MESSAGE_CMD, data, message, localNodeJid, null,
				toNodes.toArray(new JID[toNodes.size()]));
	}

	@Override
	protected boolean addOccupant(BareJID node, BareJID roomJid, JID occupantJid, String nickname) {
		ConcurrentMap<JID,ConcurrentMap<BareJID,String>> nodeOccupants = occupantsPerNode.get(node);
		if (nodeOccupants == null) {
			ConcurrentHashMap<JID,ConcurrentMap<BareJID,String>> tmp = new ConcurrentHashMap<>();
			nodeOccupants = occupantsPerNode.putIfAbsent(node, tmp);
			if (nodeOccupants == null) {
				nodeOccupants = tmp;
			}
		}
		ConcurrentMap<BareJID,String> jidRooms = nodeOccupants.get(occupantJid);
		if (jidRooms == null) {
			ConcurrentHashMap<BareJID,String> tmp = new ConcurrentHashMap<>();
			jidRooms = nodeOccupants.putIfAbsent(occupantJid, tmp);
			if (jidRooms == null) {
				jidRooms = tmp;
			}			
		}
		// is synchronization needed here? - each thread should be running on per occupant jid basis
		String oldNickname = jidRooms.put(roomJid, nickname);
		return oldNickname == null;
	}

	@Override
	protected boolean removeOccupant(BareJID node, BareJID roomJid, JID occupantJid) {
		ConcurrentMap<JID,ConcurrentMap<BareJID,String>> nodeOccupants = occupantsPerNode.get(node);
		if (nodeOccupants == null) {
			ConcurrentHashMap<JID,ConcurrentMap<BareJID,String>> tmp = new ConcurrentHashMap<>();
			nodeOccupants = occupantsPerNode.putIfAbsent(node, tmp);
			if (nodeOccupants == null) {
				nodeOccupants = tmp;
			}
		}
		Map<BareJID,String> jidRooms = nodeOccupants.get(occupantJid);
		if (jidRooms == null) {
			ConcurrentHashMap<BareJID,String> tmp = new ConcurrentHashMap<>();
			jidRooms = nodeOccupants.putIfAbsent(occupantJid, tmp);
			if (jidRooms == null) {
				jidRooms = tmp;
			}			
		}
		// is synchronization needed here? - each thread should be running on per occupant jid basis
		String removed = jidRooms.remove(roomJid);
		if (jidRooms.isEmpty())
			nodeOccupants.remove(occupantJid);

		return removed != null;
	}

	public BareJID getNodeForJID(JID jid) {
		// for local domain we should process packets on same node
		if (muc.isLocalDomain(jid.getDomain()))
			return null;
		
		// if not local packet then we need to always select one node
		for (BareJID node : occupantsPerNode.keySet()) {
			// if we have assigned node with this jid then reuse it
			Map<JID,ConcurrentMap<BareJID,String>> nodeOccupants = occupantsPerNode.get(node);
			if (nodeOccupants.containsKey(jid)) {
				return node;
			}
		}
		
		// if no node was assigned then use local node
		return null;
	}
	
	private class OccupantChangedPresenceCmd extends CommandListenerAbstract {

		public OccupantChangedPresenceCmd() {
			super(OCCUPANT_PRESENCE_CMD);
		}

		@Override
		public void executeCommand(JID fromNode, Set<JID> visitedNodes, Map<String, String> data,
				Queue<Element> packets) throws ClusterCommandException {
			
			try {
				BareJID roomJid = BareJID.bareJIDInstanceNS(data.get("room"));
				JID occupantJID = JID.jidInstanceNS(data.get("occupantJid"));
				String nickname = data.get("nickname");
				Affiliation occupantAffiliation = Affiliation.valueOf(data.get("affiliation"));
				Role occupantRole = Role.valueOf(data.get("role"));
				boolean newOccupant = data.containsKey("new-occupant");
				
				PresenceModule presenceModule = ClusteredRoomStrategy.this.muc.getModule(PresenceModule.ID);
				Room room = ClusteredRoomStrategy.this.muc.getMucRepository().getRoom(roomJid);
				for (Element presence : packets) {
					for (JID destinationJID : room.getAllOccupantsJID()) {
						PresenceWrapper presenceWrapper = PresenceWrapper.preparePresenceW(room, destinationJID, presence, occupantJID.getBareJID(),
								Collections.singleton(occupantJID), nickname, occupantAffiliation, occupantRole);
						if (!"unavailable".equals(presence.getAttributeStaticStr("type"))) {
							PresenceModuleImpl.addCodes(presenceWrapper, false, nickname);
						}
						ClusteredRoomStrategy.this.muc.addOutPacket(presenceWrapper.getPacket());						
					}
				}
				if (newOccupant) {
					presenceModule.sendPresencesToNewOccupant(room, occupantJID);
				}
			} catch (RepositoryException ex) {
				Logger.getLogger(ClusteredRoomStrategy.class.getName()).log(Level.SEVERE, null, ex);
			} catch (MUCException ex) {
				Logger.getLogger(ClusteredRoomStrategy.class.getName()).log(Level.SEVERE, null, ex);
			} catch (TigaseStringprepException ex) {
				Logger.getLogger(ClusteredRoomStrategy.class.getName()).log(Level.SEVERE, null, ex);
			}
		}
	}
	
	private class RoomCreatedCmd extends CommandListenerAbstract {

		public RoomCreatedCmd() {
			super(ROOM_CREATED_CMD);
		}

		@Override
		public void executeCommand(JID fromNode, Set<JID> visitedNodes, Map<String, String> data,
				Queue<Element> packets) throws ClusterCommandException {
			BareJID roomJid = BareJID.bareJIDInstanceNS(data.get("room"));
			JID creatorJid = JID.jidInstanceNS(data.get("creator"));
			try {
				mucRepository.createNewRoomWithoutListener(roomJid, creatorJid);
			} catch (RepositoryException ex) {
				Logger.getLogger(ClusteredRoomStrategy.class.getName()).log(Level.SEVERE, null, ex);
			}
		}		
	}
	
	private class RoomDestroyedCmd extends CommandListenerAbstract {

		public RoomDestroyedCmd() {
			super(ROOM_DESTROYED_CMD);
		}

		@Override
		public void executeCommand(JID fromNode, Set<JID> visitedNodes, Map<String, String> data,
				Queue<Element> packets) throws ClusterCommandException {
			BareJID roomJid = BareJID.bareJIDInstanceNS(data.get("room"));
			try {
				Room room = ClusteredRoomStrategy.this.muc.getMucRepository().getRoom(roomJid);
				Element destroyElement = packets.poll();
				for (JID occupantJid : room.getAllOccupantsJID()) {
					String occupantNickname = room.getOccupantsNickname(occupantJid);
					final Element p = new Element("presence");

					p.addAttribute("type", "unavailable");					
					PresenceWrapper presence = PresenceWrapper.preparePresenceW(room, occupantJid, p, occupantJid.getBareJID(), Collections.singleton(occupantJid),
							occupantNickname, Affiliation.none, Role.none);

					presence.getX().addChild(destroyElement);
				}
				mucRepository.destroyRoomWithoutListener(room, destroyElement);
			} catch (RepositoryException ex) {
				Logger.getLogger(ClusteredRoomStrategy.class.getName()).log(Level.SEVERE, null, ex);
			} catch (MUCException ex) {
				Logger.getLogger(ClusteredRoomStrategy.class.getName()).log(Level.SEVERE, null, ex);
			} catch (TigaseStringprepException ex) {
				Logger.getLogger(ClusteredRoomStrategy.class.getName()).log(Level.SEVERE, null, ex);
			}
		}		
	}	
	
	private class RoomMessageCmd extends CommandListenerAbstract {

		public RoomMessageCmd() {
			super(ROOM_MESSAGE_CMD);
		}

		@Override
		public void executeCommand(JID fromNode, Set<JID> visitedNodes, Map<String, String> data,
				Queue<Element> packets) throws ClusterCommandException {
			BareJID roomJid = BareJID.bareJIDInstanceNS(data.get("room"));
			JID from = JID.jidInstanceNS(data.get("from"));
			try {
				Room room = ClusteredRoomStrategy.this.muc.getMucRepository().getRoom(roomJid);
				GroupchatMessageModule groupchatModule = ClusteredRoomStrategy.this.muc.getModule(GroupchatMessageModule.ID);
				Element message = packets.poll();
				List<Element> children = message.getChildren();
				groupchatModule.sendMessagesToAllOccupantsJids(room, from, children.toArray(new Element[children.size()]));
			} catch (RepositoryException ex) {
				Logger.getLogger(ClusteredRoomStrategy.class.getName()).log(Level.SEVERE, null, ex);
			} catch (MUCException ex) {
				Logger.getLogger(ClusteredRoomStrategy.class.getName()).log(Level.SEVERE, null, ex);
			} catch (TigaseStringprepException ex) {
				Logger.getLogger(ClusteredRoomStrategy.class.getName()).log(Level.SEVERE, null, ex);
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
			// each node should send only info about is's occupants
			ConcurrentMap<JID,ConcurrentMap<BareJID,String>> nodeOccupants = occupantsPerNode.get(localNodeJid.getBareJID());
			LinkedList<Element> localOccupants = new LinkedList<Element>();
			if (nodeOccupants != null) {
				for (Map.Entry<JID,ConcurrentMap<BareJID,String>> occupantsEntry : nodeOccupants.entrySet()) {
					// for each occupant we send
					Element occupant = new Element("occupant", new String[] { "jid" }, 
							new String[] { occupantsEntry.getKey().toString() });
					// every room to which he is joined
					Map<BareJID,String> jidRooms = occupantsEntry.getValue();
					for (Map.Entry<BareJID,String> roomsEntry : jidRooms.entrySet()) {
						occupant.addChild(new Element("room", new String[] { "jid", "nickname" }, 
								new String[] { roomsEntry.getKey().toString(), roomsEntry.getValue() }));
					}
					localOccupants.add(occupant);
					
					if (localOccupants.size() > SYNC_MAX_BATCH_SIZE) {
						cl_controller.sendToNodes(RESPONSE_SYNC_CMD, localOccupants, 
								localNodeJid, null, fromNode);
						localOccupants = new LinkedList<Element>();
					}
				}
			}
			
			if (!localOccupants.isEmpty()) {
				cl_controller.sendToNodes(RESPONSE_SYNC_CMD, localOccupants, 
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
				for (Element occupantEl : packets) {
					if (occupantEl.getName() == "occupant") {
						JID occupantJid = JID.jidInstanceNS(
								occupantEl.getAttributeStaticStr("jid"));						
						
						List<Element> roomsElList = occupantEl.getChildren();
						if (roomsElList != null && !roomsElList.isEmpty()) {
							for (Element roomEl : roomsElList) {
								BareJID roomJid = BareJID.bareJIDInstanceNS(roomEl.getAttributeStaticStr("jid"));
								String nickname = roomEl.getAttributeStaticStr("nickname");
								addOccupant(fromNode.getBareJID(), roomJid, occupantJid, nickname);
							}
						}
					}
				}
			}
		}		
	}
}
