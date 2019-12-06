/*
 * Tigase ACS - MUC Component - Tigase Advanced Clustering Strategy - MUC Component
 * Copyright (C) 2013 Tigase, Inc. (office@tigase.com) - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package tigase.muc.cluster;

import tigase.cluster.api.ClusterCommandException;
import tigase.cluster.api.CommandListenerAbstract;
import tigase.component.exceptions.RepositoryException;
import tigase.eventbus.EventBus;
import tigase.kernel.beans.Bean;
import tigase.kernel.beans.Inject;
import tigase.muc.*;
import tigase.muc.modules.GroupchatMessageModule;
import tigase.muc.modules.PresenceModule.PresenceWrapper;
import tigase.server.Packet;
import tigase.server.Priority;
import tigase.util.stringprep.TigaseStringprepException;
import tigase.xml.Element;
import tigase.xmpp.jid.BareJID;
import tigase.xmpp.jid.JID;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * AbstractClusteredRoomStrategy implements strategy which allows to create rooms with large number of occupants as each
 * cluster node will process packets related only to occupants connected to this node. (Base class for strategies
 * implementations using clustered room strategy)
 * <p>
 * Limitations: - every room needs to be persistent - possible issues with changing affiliations or room configuration
 *
 * @author andrzej
 */
public abstract class AbstractClusteredRoomStrategy
		extends AbstractStrategy
		implements StrategyIfc, InMemoryMucRepositoryClustered.RoomListener, Room.RoomListener {

	private static final Logger log = Logger.getLogger(AbstractClusteredRoomStrategy.class.getCanonicalName());
	private static final String RESPONSE_SYNC_CMD = "muc-sync-response";
	private static final String ROOM_CHANGED_CMD = "muc-room-changed-cmd";
	private static final String ROOM_CREATED_CMD = "muc-room-created-cmd";
	private static final String ROOM_DESTROYED_CMD = "muc-room-destroyed-cmd";
	private static final String ROOM_MESSAGE_CMD = "muc-room-message-cmd";
	private static final String ROOM_AFFILIATION_CMD = "muc-room-affiliation-cmd";
	private static final int SYNC_MAX_BATCH_SIZE = 1000;
	// Map<node_bare_jid, Map<occupant_jid, Map<room_jid,nickname> > >
	protected final ConcurrentHashMap<BareJID, ConcurrentMap<JID, ConcurrentMap<BareJID, String>>> occupantsPerNode = new ConcurrentHashMap<>();
	@Inject
	private GroupchatMessageModule groupchatModule;

	@Override
	public void nodeDisconnected(JID nodeJid) {
		ConcurrentMap<JID, ConcurrentMap<BareJID, String>> nodeOccupants = occupantsPerNode.remove(
				nodeJid.getBareJID());
		if (nodeOccupants == null) {
			return;
		}

		List<JID> allNodes = getNodesConnectedWithLocal();
		int localNodeIdx = allNodes.indexOf(localNodeJid);
		int nodesCount = allNodes.size();
		for (Map.Entry<JID, ConcurrentMap<BareJID, String>> e : nodeOccupants.entrySet()) {
			JID occupant = e.getKey();
			// send removal if occupant is not local and if it's hashcode
			// matches this node
			boolean sendRemovalToOccupant = !mucComponentClustered.isLocalDomain(occupant.getDomain()) &&
					(occupant.hashCode() % nodesCount) == localNodeIdx;

			Map<BareJID, String> rooms = e.getValue();
			if (rooms == null) {
				continue;
			}
			for (BareJID roomJid : rooms.keySet()) {
				try {
					Room room = mucRepository.getRoom(roomJid);
					if (room != null) {
						sendRemoteOccupantRemovalOnDisconnect(room, occupant, rooms.get(roomJid),
															  sendRemovalToOccupant);
					} else {
						log.log(Level.FINER,
								"no room {0} in repository, while instance available in map of active rooms, " +
										"propably room removed on other node but not yet synchronized?", roomJid);
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
				log.log(Level.FINER, "forwarding packet to node = {1}", new Object[]{nodeJid});
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
			// this clustering strategy requires that all rooms are persistent
			// as
			// none of nodes knows about every occupants
			RoomConfig roomConfig = mucRepository.getDefaultRoomConfig();
			roomConfig.setValue(RoomConfig.MUC_ROOMCONFIG_PERSISTENTROOM_KEY, true);
		} catch (RepositoryException ex) {

		}
	}

	@Override
	public void start() {
	}

	@Override
	public void stop() {
	}

	@Override
	public void onRoomChanged(RoomConfig roomConfig, Set<String> modifiedVars) {
		if (modifiedVars.isEmpty()) {
			return;
		}

		List<JID> toNodes = getNodesConnected();

		Map<String, String> data = new HashMap<String, String>();
		data.put("room", roomConfig.getRoomJID().toString());

		for (String key : modifiedVars) {
			String[] val = roomConfig.getConfigForm().getAsStrings(key);
			if (val != null) {
				if (val.length == 1) {
					data.put(key, val[0]);
				} else if (val.length > 1) {
					data.put(key, Arrays.stream(val).collect(Collectors.joining("|")));
				} else {
					data.put(key, null);
				}
			} else {
				data.put(key, null);
			}
		}

		cl_controller.sendToNodes(ROOM_CHANGED_CMD, data, localNodeJid, toNodes.toArray(new JID[toNodes.size()]));
	}

	@Override
	public void onRoomCreated(Room room) {
		List<JID> toNodes = getNodesConnected();

		Map<String, String> data = new HashMap<String, String>();
		data.put("room", room.getRoomJID().toString());
		data.put("creator", room.getCreatorJid().toString());
		// added to improve distribution of processing commands over threads
		data.put("userId", room.getRoomJID().toString());

		if (log.isLoggable(Level.FINEST)) {
			StringBuilder buf = new StringBuilder(100);
			for (JID node : toNodes) {
				if (buf.length() > 0) {
					buf.append(",");
				}
				buf.append(node.toString());
			}
			log.log(Level.FINEST, "room = {0}, notifing nodes [{1}] that room is created",
					new Object[]{room.getRoomJID(), buf});
		}

		cl_controller.sendToNodes(ROOM_CREATED_CMD, data, localNodeJid, toNodes.toArray(new JID[toNodes.size()]));
	}

	@Override
	public void onRoomDestroyed(Room room, Element destroyElement) {
		List<JID> toNodes = getNodesConnected();

		Map<String, String> data = new HashMap<String, String>();
		data.put("room", room.getRoomJID().toString());
		// added to improve distribution of processing commands over threads
		data.put("userId", room.getRoomJID().toString());

		if (log.isLoggable(Level.FINEST)) {
			StringBuilder buf = new StringBuilder(100);
			for (JID node : toNodes) {
				if (buf.length() > 0) {
					buf.append(",");
				}
				buf.append(node.toString());
			}
			log.log(Level.FINEST, "room = {0}, notifing nodes [{1}] that room is destroyed",
					new Object[]{room.getRoomJID(), buf});
		}

		cl_controller.sendToNodes(ROOM_DESTROYED_CMD, data, destroyElement, localNodeJid, null,
								  toNodes.toArray(new JID[toNodes.size()]));
	}

	@Override
	public void onLeaveRoom(Room room) {
		// Nothing to do - other nodes will be notified that occupant changed presence (left room) and will take care of this
		// if needed, however as Room instance is used for discovery and in this strategy every room is persistent
		// then there is no valid point to notify repository and other nodes that room is left (that it is empty now).
	}

	@Override
	public void onChangeSubject(Room room, String nick, String newSubject, Date changeDate) {
		// throw new UnsupportedOperationException("Not supported yet."); //To
		// change body of generated methods, choose Tools | Templates.
	}

	@Override
	public void onSetAffiliation(Room room, BareJID jid, RoomAffiliation oldAffiliation, RoomAffiliation newAffiliation) {
		List<JID> toNodes = getNodesConnected();

		Map<String, String> data = new HashMap<String, String>();
		data.put("room", room.getRoomJID().toString());
		data.put("userId", jid.toString());
		data.put("newAffiliation", newAffiliation.getAffiliation().name());
		data.put("newPersistent", String.valueOf(newAffiliation.isPersistentOccupant()));
		if (newAffiliation.getRegisteredNickname() != null) {
			data.put("newNickname", newAffiliation.getRegisteredNickname());
		}

		if (log.isLoggable(Level.FINEST)) {
			StringBuilder buf = new StringBuilder(100);
			for (JID node : toNodes) {
				if (buf.length() > 0) {
					buf.append(",");
				}
				buf.append(node.toString());
			}
			log.log(Level.FINEST, "room = {0}, notifing nodes [{1}] about new affiliation",
					new Object[]{room.getRoomJID(), buf});
		}
		cl_controller.sendToNodes(ROOM_AFFILIATION_CMD, data, localNodeJid, toNodes.toArray(new JID[toNodes.size()]));
	}

	@Override
	public void onMessageToOccupants(Room room, JID from, Packet packet) {
		List<JID> toNodes = getNodesConnected();

		Map<String, String> data = new HashMap<String, String>();
		data.put("room", room.getRoomJID().toString());
		data.put("userId", from.toString());

		Element message = packet.getElement().clone();
		// this should not be needed but I'm adding it just in case
		message.removeAttribute("from");
		message.removeAttribute("to");

		if (log.isLoggable(Level.FINEST)) {
			StringBuilder buf = new StringBuilder(100);
			for (JID node : toNodes) {
				if (buf.length() > 0) {
					buf.append(",");
				}
				buf.append(node.toString());
			}
			log.log(Level.FINEST, "room = {0}, notifing nodes [{1}] about new message",
					new Object[]{room.getRoomJID(), buf});
		}

		cl_controller.sendToNodes(ROOM_MESSAGE_CMD, data, message, localNodeJid, null,
								  toNodes.toArray(new JID[toNodes.size()]));
	}

	public BareJID getNodeForJID(JID jid) {
		// for local domain we should process packets on same node
		if (mucComponentClustered.isLocalDomain(jid.getDomain())) {
			return null;
		}

		// if not local packet then we need to always select one node
		for (BareJID node : occupantsPerNode.keySet()) {
			// if we have assigned node with this jid then reuse it
			Map<JID, ConcurrentMap<BareJID, String>> nodeOccupants = occupantsPerNode.get(node);
			if (nodeOccupants.containsKey(jid)) {
				if (node.equals(localNodeJid.getBareJID())) {
					return null;
				}
				return node;
			}
		}

		// if no node was assigned then use local node
		return null;
	}

	@Override
	protected boolean addOccupant(BareJID node, BareJID roomJid, JID occupantJid, String nickname) {
		ConcurrentMap<JID, ConcurrentMap<BareJID, String>> nodeOccupants = occupantsPerNode.get(node);
		if (nodeOccupants == null) {
			ConcurrentHashMap<JID, ConcurrentMap<BareJID, String>> tmp = new ConcurrentHashMap<>();
			nodeOccupants = occupantsPerNode.putIfAbsent(node, tmp);
			if (nodeOccupants == null) {
				nodeOccupants = tmp;
			}
		}
		ConcurrentMap<BareJID, String> jidRooms = nodeOccupants.get(occupantJid);
		if (jidRooms == null) {
			ConcurrentHashMap<BareJID, String> tmp = new ConcurrentHashMap<>();
			jidRooms = nodeOccupants.putIfAbsent(occupantJid, tmp);
			if (jidRooms == null) {
				jidRooms = tmp;
			}
		}
		// is synchronization needed here? - each thread should be running on
		// per occupant jid basis
		String oldNickname = jidRooms.put(roomJid, nickname);
		return oldNickname == null;
	}

	@Override
	protected boolean removeOccupant(BareJID node, BareJID roomJid, JID occupantJid) {
		ConcurrentMap<JID, ConcurrentMap<BareJID, String>> nodeOccupants = occupantsPerNode.get(node);
		if (nodeOccupants == null) {
			ConcurrentHashMap<JID, ConcurrentMap<BareJID, String>> tmp = new ConcurrentHashMap<>();
			nodeOccupants = occupantsPerNode.putIfAbsent(node, tmp);
			if (nodeOccupants == null) {
				nodeOccupants = tmp;
			}
		}
		Map<BareJID, String> jidRooms = nodeOccupants.get(occupantJid);
		if (jidRooms == null) {
			ConcurrentHashMap<BareJID, String> tmp = new ConcurrentHashMap<>();
			jidRooms = nodeOccupants.putIfAbsent(occupantJid, tmp);
			if (jidRooms == null) {
				jidRooms = tmp;
			}
		}
		// is synchronization needed here? - each thread should be running on
		// per occupant jid basis
		String removed = jidRooms.remove(roomJid);
		if (jidRooms.isEmpty()) {
			nodeOccupants.remove(occupantJid);
		}

		return removed != null;
	}
	
	public boolean shouldSendOfflineMessageToJidFromLocalNode(BareJID jid) {
		return localNodeJid.equals(
				getNodesConnectedWithLocal().get(jid.hashCode() % getNodesConnectedWithLocal().size()));
	}

	@Bean(name = REQUEST_SYNC_CMD, parent = AbstractClusteredRoomStrategy.class, active = true)
	public static class RequestSyncCmd
			extends CommandListenerAbstract {

		@Inject
		private AbstractClusteredRoomStrategy strategy;

		public RequestSyncCmd() {
			super(REQUEST_SYNC_CMD, Priority.HIGH);
		}

		@Override
		public void executeCommand(JID fromNode, Set<JID> visitedNodes, Map<String, String> data,
								   Queue<Element> packets) throws ClusterCommandException {
			// each node should send only info about is's occupants
			ConcurrentMap<JID, ConcurrentMap<BareJID, String>> nodeOccupants = strategy.occupantsPerNode.get(
					strategy.localNodeJid.getBareJID());
			LinkedList<Element> localOccupants = new LinkedList<Element>();
			if (nodeOccupants != null) {
				if (log.isLoggable(Level.FINEST)) {
					log.log(Level.FINEST, "executig RequestSyncCmd command fromNode = {0}, nodeOccupants = {1}",
							new Object[]{fromNode, nodeOccupants});
				}
				for (Map.Entry<JID, ConcurrentMap<BareJID, String>> occupantsEntry : nodeOccupants.entrySet()) {
					// for each occupant we send
					Element occupant = new Element("occupant", new String[]{"jid"},
												   new String[]{occupantsEntry.getKey().toString()});
					// every room to which he is joined
					Map<BareJID, String> jidRooms = occupantsEntry.getValue();
					for (Map.Entry<BareJID, String> roomsEntry : jidRooms.entrySet()) {
						occupant.addChild(new Element("room", new String[]{"jid", "nickname"},
													  new String[]{roomsEntry.getKey().toString(),
																   roomsEntry.getValue()}));
					}
					localOccupants.add(occupant);

					if (localOccupants.size() > SYNC_MAX_BATCH_SIZE) {
						strategy.cl_controller.sendToNodes(RESPONSE_SYNC_CMD, localOccupants, strategy.localNodeJid,
														   null, fromNode);
						localOccupants = new LinkedList<Element>();
					}
				}
			}

			if (!localOccupants.isEmpty()) {
				strategy.cl_controller.sendToNodes(RESPONSE_SYNC_CMD, localOccupants, strategy.localNodeJid, null,
												   fromNode);
			}
		}
	}

	@Bean(name = RESPONSE_SYNC_CMD, parent = AbstractClusteredRoomStrategy.class, active = true)
	public static class ResponseSyncCmd
			extends CommandListenerAbstract {

		@Inject
		private AbstractClusteredRoomStrategy strategy;

		public ResponseSyncCmd() {
			super(RESPONSE_SYNC_CMD, Priority.HIGH);
		}

		@Override
		public void executeCommand(JID fromNode, Set<JID> visitedNodes, Map<String, String> data,
								   Queue<Element> packets) throws ClusterCommandException {
			if (packets != null && !packets.isEmpty()) {

				if (log.isLoggable(Level.FINEST)) {
					log.log(Level.FINEST, "executig ResponseSyncCmd command fromNode = {0}, packets: {1}",
							new Object[]{fromNode, packets});
				}

				for (Element occupantEl : packets) {
					if (occupantEl.getName() == "occupant") {
						JID occupantJid = JID.jidInstanceNS(occupantEl.getAttributeStaticStr("jid"));

						List<Element> roomsElList = occupantEl.getChildren();
						if (roomsElList != null && !roomsElList.isEmpty()) {
							for (Element roomEl : roomsElList) {
								BareJID roomJid = BareJID.bareJIDInstanceNS(roomEl.getAttributeStaticStr("jid"));
								String nickname = roomEl.getAttributeStaticStr("nickname");
								strategy.addOccupant(fromNode.getBareJID(), roomJid, occupantJid, nickname);
							}
						}
					}
				}
			}
		}
	}

	@Bean(name = ROOM_AFFILIATION_CMD, parent = AbstractClusteredRoomStrategy.class, active = true)
	public static class RoomAffiliationCmd
			extends CommandListenerAbstract {

		@Inject
		private InMemoryMucRepositoryClustered mucRepository;
		@Inject
		private EventBus eventBus;

		public RoomAffiliationCmd() {
			super(ROOM_AFFILIATION_CMD, Priority.HIGH);
		}

		@Override
		public void executeCommand(JID fromNode, Set<JID> visitedNodes, Map<String, String> data,
								   Queue<Element> packets) throws ClusterCommandException {

			BareJID roomJid = BareJID.bareJIDInstanceNS(data.get("room"));
			BareJID from = BareJID.bareJIDInstanceNS(data.get("userId"));
			Affiliation affiliation = Affiliation.valueOf(data.get("newAffiliation"));
			boolean persistent = Boolean.valueOf(data.get("newPersistent"));
			String nickname = data.get("newNickname");

			RoomAffiliation newAffiliation = RoomAffiliation.from(affiliation, persistent, nickname);
			if (log.isLoggable(Level.FINEST)) {
				log.log(Level.FINEST,
						"executig RoomAffiliationCmd command for room = {0}, from = {1}, newAffiliation: {2}",
						new Object[]{roomJid, from, newAffiliation});
			}

			try {
				Room room = mucRepository.getRoom(roomJid);

				// In some cases room may be already destroyed or not yet created on this node
				// In both cases there is no point in sending this event if the room does not exist
				if (room != null) {
					RoomAffiliation oldAffiliation = room.getAffiliation(from);
					room.setNewAffiliation(from, newAffiliation);
					eventBus.fire(new AffiliationChangedEvent(room, from, oldAffiliation, newAffiliation));
				}
			} catch (RepositoryException ex) {
				Logger.getLogger(AbstractClusteredRoomStrategy.class.getName()).log(Level.SEVERE, null, ex);
			}

		}
	}

	@Bean(name = ROOM_CHANGED_CMD, parent = AbstractClusteredRoomStrategy.class, active = true)
	public static class RoomChangedCmd
			extends CommandListenerAbstract {

		@Inject
		private InMemoryMucRepositoryClustered mucRepository;

		public RoomChangedCmd() {
			super(ROOM_CHANGED_CMD, Priority.HIGH);
		}

		@Override
		public void executeCommand(JID fromNode, Set<JID> visitedNodes, Map<String, String> data,
								   Queue<Element> packets) throws ClusterCommandException {
			BareJID roomJid = BareJID.bareJIDInstanceNS(data.remove("room"));
			mucRepository.roomConfigChanged(roomJid, data);
			if (log.isLoggable(Level.FINEST)) {
				log.log(Level.FINEST, "room = {0}, received notification that room {1} was modified at node {2}",
						new Object[]{roomJid, roomJid, fromNode});
			}
		}
	}

	@Bean(name = ROOM_CREATED_CMD, parent = AbstractClusteredRoomStrategy.class, active = true)
	public static class RoomCreatedCmd
			extends CommandListenerAbstract {

		@Inject
		private InMemoryMucRepositoryClustered mucRepository;

		public RoomCreatedCmd() {
			super(ROOM_CREATED_CMD, Priority.HIGH);
		}

		@Override
		public void executeCommand(JID fromNode, Set<JID> visitedNodes, Map<String, String> data,
								   Queue<Element> packets) throws ClusterCommandException {
			BareJID roomJid = BareJID.bareJIDInstanceNS(data.get("room"));
			JID creatorJid = JID.jidInstanceNS(data.get("creator"));
			try {
				if (log.isLoggable(Level.FINEST)) {
					log.log(Level.FINEST, "executig RoomCreatedCmd command for room = {0}, creatorJid = {1}",
							new Object[]{roomJid, creatorJid});
				}
				mucRepository.createNewRoomWithoutListener(roomJid, creatorJid);
			} catch (RepositoryException ex) {
				Logger.getLogger(AbstractClusteredRoomStrategy.class.getName()).log(Level.SEVERE, null, ex);
			}
		}
	}

	@Bean(name = ROOM_DESTROYED_CMD, parent = AbstractClusteredRoomStrategy.class, active = true)
	public static class RoomDestroyedCmd
			extends CommandListenerAbstract {

		@Inject
		private InMemoryMucRepositoryClustered mucRepository;

		public RoomDestroyedCmd() {
			super(ROOM_DESTROYED_CMD, Priority.HIGH);
		}

		@Override
		public void executeCommand(JID fromNode, Set<JID> visitedNodes, Map<String, String> data,
								   Queue<Element> packets) throws ClusterCommandException {
			BareJID roomJid = BareJID.bareJIDInstanceNS(data.get("room"));

			if (log.isLoggable(Level.FINEST)) {
				log.log(Level.FINEST, "executig RoomDestroyedCmd command for room = {0}, packets: {1}",
						new Object[]{roomJid, packets});
			}

			try {
				Room room = mucRepository.getRoom(roomJid);
				Element destroyElement = packets.poll();
				for (JID occupantJid : room.getAllOccupantsJID()) {
					String occupantNickname = room.getOccupantsNickname(occupantJid);
					final Element p = new Element("presence");

					p.addAttribute("type", "unavailable");
					PresenceWrapper presence = PresenceWrapper.preparePresenceW(room, occupantJid, p,
																				occupantJid.getBareJID(),
																				Collections.singleton(occupantJid),
																				occupantNickname, Affiliation.none,
																				Role.none);

					presence.getX().addChild(destroyElement);
				}
				mucRepository.destroyRoomWithoutListener(room, destroyElement);
			} catch (RepositoryException ex) {
				Logger.getLogger(AbstractClusteredRoomStrategy.class.getName()).log(Level.SEVERE, null, ex);
			} catch (TigaseStringprepException ex) {
				Logger.getLogger(AbstractClusteredRoomStrategy.class.getName()).log(Level.SEVERE, null, ex);
			}
		}
	}

	@Bean(name = ROOM_MESSAGE_CMD, parent = AbstractClusteredRoomStrategy.class, active = true)
	public static class RoomMessageCmd
			extends CommandListenerAbstract {

		@Inject
		private GroupchatMessageModule groupchatModule;
		@Inject
		private InMemoryMucRepositoryClustered mucRepository;

		public RoomMessageCmd() {
			super(ROOM_MESSAGE_CMD, Priority.HIGH);
		}

		@Override
		public void executeCommand(JID fromNode, Set<JID> visitedNodes, Map<String, String> data,
								   Queue<Element> packets) throws ClusterCommandException {
			BareJID roomJid = BareJID.bareJIDInstanceNS(data.get("room"));
			JID from = JID.jidInstanceNS(data.get("userId"));
			if (log.isLoggable(Level.FINEST)) {
				log.log(Level.FINEST, "executig RoomMessageCmd command for room = {0}, from = {1}, packets: {2}",
						new Object[]{roomJid, from, packets});
			}
			try {
				Room room = mucRepository.getRoom(roomJid);
				Element message = packets.poll();
				Packet packet = Packet.packetInstance(message);
				groupchatModule.sendMessagesToAllOccupantsJids(room, from, packet);
			} catch (RepositoryException ex) {
				Logger.getLogger(AbstractClusteredRoomStrategy.class.getName()).log(Level.SEVERE, null, ex);
			} catch (TigaseStringprepException ex) {
				Logger.getLogger(AbstractClusteredRoomStrategy.class.getName()).log(Level.SEVERE, null, ex);
			}

		}
	}

}
