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
import tigase.xml.Element;
import tigase.xmpp.BareJID;
import tigase.xmpp.JID;

/**
 *
 * @author andrzej
 */
public abstract class AbstractStrategy implements StrategyIfc, Room.RoomOccupantListener {
	
	private static final Logger log = Logger.getLogger(AbstractStrategy.class.getCanonicalName());
	
	private static final String OCCUPANT_ADDED_CMD = "muc-occupant-added-cmd";
	private static final String OCCUPANT_REMOVED_CMD = "muc-occupant-removed-cmd";	
	
	protected final CopyOnWriteArrayList<JID> connectedNodes = new CopyOnWriteArrayList<JID>();
	protected ClusterControllerIfc cl_controller;
	protected JID localNodeJid;
	protected MUCComponentClustered muc;
	protected InMemoryMucRepositoryClustered mucRepository = null;	
	protected final ConcurrentMap<BareJID, Set<JID>> occupantsPerRoom =
			new ConcurrentHashMap<BareJID, Set<JID>>();

	private final OccupantAddedCmd occupantAddedCmd = new OccupantAddedCmd();
	private final OccupantRemovedCmd occupantRemovedCmd = new OccupantRemovedCmd();
	
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
		if (addOccupant(room.getRoomJID(), occupantJid)) {
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
				log.log(Level.FINEST, "room = {0}, notifing nodes [{1}] that occupant {2} joined room {3}",
						new Object[]{room.getRoomJID(), buf, occupantJid, room.getRoomJID()});
			}
		
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
	
	@Override
	public void setClusterController(ClusterControllerIfc cl_controller) {
		if (this.cl_controller != null) {
			this.cl_controller.removeCommandListener(occupantAddedCmd);
			this.cl_controller.removeCommandListener(occupantRemovedCmd);			
		}
		this.cl_controller = cl_controller;
		if (cl_controller != null) {
			this.cl_controller.setCommandListener(occupantAddedCmd);
			this.cl_controller.setCommandListener(occupantRemovedCmd);
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
	
	protected boolean addOccupant(BareJID roomJid, JID occupantJid) {
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

	protected boolean removeOccupant(BareJID roomJid, JID occupantJid) {
		Set<JID> occupants = this.occupantsPerRoom.get(roomJid);
		if (occupants == null) {
			return false;
		}

		synchronized (occupants) {
			return occupants.remove(occupantJid);
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
			removeOccupant(roomJid, occupantJid);
			if (log.isLoggable(Level.FINEST)) {
				log.log(Level.FINEST, "room = {0}, received notification that occupant {1} left room {2} at node {3}", 
						new Object[]{ roomJid, occupantJid, roomJid, fromNode});
			}
		}
	}
	
}
