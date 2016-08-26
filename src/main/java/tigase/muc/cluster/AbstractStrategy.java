/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package tigase.muc.cluster;

import tigase.cluster.api.ClusterCommandException;
import tigase.cluster.api.ClusterControllerIfc;
import tigase.cluster.api.CommandListenerAbstract;
import tigase.kernel.beans.Bean;
import tigase.kernel.beans.Inject;
import tigase.muc.Room;
import tigase.server.Packet;
import tigase.server.Presence;
import tigase.server.Priority;
import tigase.util.TigaseStringprepException;
import tigase.xml.Element;
import tigase.xmpp.BareJID;
import tigase.xmpp.JID;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author andrzej
 */
public abstract class AbstractStrategy implements StrategyIfc, Room.RoomOccupantListener {

	protected static final String REQUEST_SYNC_CMD = "muc-sync-request";
	private static final Logger log = Logger.getLogger(AbstractStrategy.class.getCanonicalName());
	private static final String PACKET_FORWARD_CMD = "muc-packet-forward-cmd";
	private static final String OCCUPANT_ADDED_CMD = "muc-occupant-added-cmd";
	private static final String OCCUPANT_REMOVED_CMD = "muc-occupant-removed-cmd";
	protected ClusterControllerIfc cl_controller;
	protected JID localNodeJid;
	@Inject
	protected MUCComponentClustered mucComponentClustered;
	@Inject
	protected InMemoryMucRepositoryClustered mucRepository;

	@Override
	public List<JID> getNodesConnected() {
		return mucComponentClustered.getNodesConnected();
	}

	@Override
	public List<JID> getNodesConnectedWithLocal() {
		return mucComponentClustered.getNodesConnectedWithLocal();
	}

	/**
	 * Method description
	 *
	 *
	 * @param nodeJid
	 *            is a <code>JID</code>
	 */
	@Override
	public void nodeConnected(JID nodeJid) {
		if (!localNodeJid.equals(nodeJid)) {
			requestSync(nodeJid);
		}
	}

	@Override
	public void onOccupantAdded(Room room, JID occupantJid) {
		String nickname = room.getOccupantsNickname(occupantJid);
		if (addOccupant(localNodeJid.getBareJID(), room.getRoomJID(), occupantJid, nickname)) {
			// we should notify other nodes about that
			List<JID> toNodes = getNodesConnected();

			Map<String, String> data = new HashMap<String, String>();
			data.put("room", room.getRoomJID().toString());
			data.put("userId", occupantJid.toString());
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
						new Object[] { room.getRoomJID(), buf, occupantJid, room.getRoomJID() });
			}

			cl_controller.sendToNodes(OCCUPANT_ADDED_CMD, data, localNodeJid, toNodes.toArray(new JID[toNodes.size()]));
		}
	}

	@Override
	public void onOccupantRemoved(Room room, JID occupantJid) {
		if (removeOccupant(localNodeJid.getBareJID(), room.getRoomJID(), occupantJid)) {
			// we should notify other nodes about that
			List<JID> toNodes = getNodesConnected();

			Map<String, String> data = new HashMap<String, String>();
			data.put("room", room.getRoomJID().toString());
			data.put("userId", occupantJid.toString());

			if (log.isLoggable(Level.FINEST)) {
				StringBuilder buf = new StringBuilder(100);
				for (JID node : toNodes) {
					if (buf.length() > 0) {
						buf.append(",");
					}
					buf.append(node.toString());
				}
				log.log(Level.FINEST, "room = {0}, notifing nodes [{1}] that occupant {2} left room {3}",
						new Object[] { room.getRoomJID(), buf, occupantJid, room.getRoomJID() });
			}

			cl_controller.sendToNodes(OCCUPANT_REMOVED_CMD, data, localNodeJid, toNodes.toArray(new JID[toNodes.size()]));
		}
	}

	protected void sendRemoteOccupantRemovalOnDisconnect(Room room, JID occupant, String occupantNick,
			boolean sendRemovalToOccupant) {
		// notify occupants of this room on this node that occupant was removed
		for (String nickname : room.getOccupantsNicknames()) {
			Collection<JID> jids = room.getOccupantsJidsByNickname(nickname);
			for (JID jid : jids) {
				sendRemovalFromRoomOnNodeDisconnect(JID.jidInstanceNS(room.getRoomJID(), occupantNick), jid);
			}
		}
		if (sendRemovalToOccupant) {
			sendRemovalFromRoomOnNodeDisconnect(room.getRoomJID(), occupant);
		}
	}

	protected void sendRemovalFromRoomOnNodeDisconnect(BareJID roomJid, JID occupant) {
		sendRemovalFromRoomOnNodeDisconnect(roomJid.toString(), occupant, false);
	}

	private void sendRemovalFromRoomOnNodeDisconnect(JID roomJid, JID occupant) {
		sendRemovalFromRoomOnNodeDisconnect(roomJid.toString(), occupant, true);
	}

	private void sendRemovalFromRoomOnNodeDisconnect(String roomJid, JID occupant, boolean toDisconnectedOccupant) {
		try {
			Element presenceEl = new Element("presence", new String[] { "xmlns", "from", "to", "type" },
					new String[] { Presence.CLIENT_XMLNS, roomJid.toString(), occupant.toString(), "unavailable" });
			Element x = new Element("x", new String[] { "xmlns" }, new String[] { "http://jabber.org/protocol/muc#user" });
			presenceEl.addChild(x);
			Element item = new Element("item", new String[] { "role" }, new String[] { "none" });
			x.addChild(item);
			if (toDisconnectedOccupant) {
				item.addChild(new Element("reason", "MUC component is disconnected."));
				x.addChild(new Element("status", new String[] { "code" }, new String[] { "332" }));
			}
			mucComponentClustered.addOutPacket(Packet.packetInstance(presenceEl));
		} catch (TigaseStringprepException ex) {
			log.log(Level.FINE, "Problem on throwing out occupant {0} on node disconnection", new Object[] { occupant });
		}
	}

	@Override
	public void setClusterController(ClusterControllerIfc cl_controller) {
		this.cl_controller = cl_controller;
	}

	@Override
	public void setMucComponentClustered(MUCComponentClustered mucComponent) {
		setLocalNodeJid(JID.jidInstanceNS(mucComponent.getName(), mucComponent.getDefHostName().getDomain(), null));
		this.mucComponentClustered = mucComponent;
	}

	@Override
	public void setMucRepository(InMemoryMucRepositoryClustered mucRepository) {
		this.mucRepository = mucRepository;
	}

	protected void forwardPacketToNode(JID nodeJid, Packet packet) {
		cl_controller.sendToNodes(PACKET_FORWARD_CMD, packet.getElement(), localNodeJid, null, nodeJid);
	}

	protected void setLocalNodeJid(JID jid) {
		this.localNodeJid = jid;
	}

	abstract protected boolean addOccupant(BareJID node, BareJID roomJid, JID occupantJid, String nickname);

	abstract protected boolean removeOccupant(BareJID node, BareJID roomJid, JID occupantJid);

	protected void requestSync(JID nodeJid) {
		cl_controller.sendToNodes(REQUEST_SYNC_CMD, localNodeJid, nodeJid);
	}

	@Bean(name = OCCUPANT_ADDED_CMD, parent = AbstractStrategy.class)
	public static class OccupantAddedCmd extends CommandListenerAbstract {

		@Inject
		private AbstractStrategy strategy;

		public OccupantAddedCmd() {
			super(OCCUPANT_ADDED_CMD, Priority.HIGH);
		}

		@Override
		public void executeCommand(JID fromNode, Set<JID> visitedNodes, Map<String, String> data, Queue<Element> packets)
				throws ClusterCommandException {
			BareJID roomJid = BareJID.bareJIDInstanceNS(data.get("room"));
			JID occupantJid = JID.jidInstanceNS(data.get("userId"));
			String nickname = data.get("occupant-nickname");
			strategy.addOccupant(fromNode.getBareJID(), roomJid, occupantJid, nickname);
			if (log.isLoggable(Level.FINEST)) {
				log.log(Level.FINEST, "room = {0}, received notification that occupant {1} joined room {2} at node {3}",
						new Object[] { roomJid, occupantJid, roomJid, fromNode });
			}
		}
	}

	@Bean(name = OCCUPANT_REMOVED_CMD, parent = AbstractStrategy.class)
	public static class OccupantRemovedCmd extends CommandListenerAbstract {

		@Inject
		private AbstractStrategy strategy;

		public OccupantRemovedCmd() {
			super(OCCUPANT_REMOVED_CMD, Priority.HIGH);
		}

		@Override
		public void executeCommand(JID fromNode, Set<JID> visitedNodes, Map<String, String> data, Queue<Element> packets)
				throws ClusterCommandException {
			BareJID roomJid = BareJID.bareJIDInstanceNS(data.get("room"));
			JID occupantJid = JID.jidInstanceNS(data.get("userId"));
			strategy.removeOccupant(fromNode.getBareJID(), roomJid, occupantJid);
			if (log.isLoggable(Level.FINEST)) {
				log.log(Level.FINEST, "room = {0}, received notification that occupant {1} left room {2} at node {3}",
						new Object[] { roomJid, occupantJid, roomJid, fromNode });
			}
		}
	}

	@Bean(name = PACKET_FORWARD_CMD, parent = AbstractStrategy.class)
	public static class PacketForwardCmd extends CommandListenerAbstract {

		@Inject
		private MUCComponentClustered mucComponentClustered;

		public PacketForwardCmd() {
			super(PACKET_FORWARD_CMD, Priority.HIGH);
		}

		@Override
		public void executeCommand(JID fromNode, Set<JID> visitedNodes, Map<String, String> data, Queue<Element> packets)
				throws ClusterCommandException {
			if (packets != null && !packets.isEmpty()) {
				for (Element elem : packets) {
					try {
						Packet packet = Packet.packetInstance(elem);
						if (log.isLoggable(Level.FINEST)) {
							log.log(Level.FINEST, "received packet {0} forwarded from node {1}",
									new Object[] { packet, fromNode });
						}
						if (mucComponentClustered.addPacketNB(packet)) {
							if (log.isLoggable(Level.FINEST)) {
								log.log(Level.FINEST, "forwarded packet added to processing queue " + "of component = {0}",
										packet.toString());
							}
						} else {
							log.log(Level.FINE, "forwarded packet dropped due to component queue " + "overflow = {0}",
									packet.toString());
						}
					} catch (TigaseStringprepException ex) {
						log.log(Level.FINEST, "Addressing problem, stringprep failed for packet: {0}", elem);
					}
				}
			}
		}
	}

}
