/*
 * ClusteredRoomStrategyV2.java
 *
 * Tigase Jabber/XMPP Server
 * Copyright (C) 2004-2014 "Tigase, Inc." <office@tigase.com>
 *
 */

package tigase.muc.cluster;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
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
import tigase.muc.exceptions.MUCException;
import tigase.muc.modules.PresenceModule;
import tigase.muc.modules.PresenceModuleImpl;
import tigase.server.Packet;
import tigase.util.TigaseStringprepException;
import tigase.xml.Element;
import tigase.xmpp.BareJID;
import tigase.xmpp.JID;

/**
 *
 * @author andrzej
 */
public class ClusteredRoomStrategyV2 extends AbstractClusteredRoomStrategy {
	
	private static final Logger log = Logger.getLogger(ClusteredRoomStrategyV2.class.getCanonicalName());
	
	private static final String OCCUPANTS_REMOTE_KEY = "occupants-remote-key";
	private static final String OCCUPANT_PRESENCE_CMD = "muc-occupant-presence-cmd";
	private static final String OCCUPANTS_SYNC_REQUEST_CMD = "request-occupants-sync";
	
	private final OccupantChangedPresenceCmd occupantChangedPresenceCmd = new OccupantChangedPresenceCmd();	
	private final OccupantsSyncRequestCmd occupantsSyncRequestCmd = new OccupantsSyncRequestCmd();
	
	private class OccupantChangedPresenceCmd extends CommandListenerAbstract {

		public OccupantChangedPresenceCmd() {
			super(OCCUPANT_PRESENCE_CMD);
		}

		@Override
		public void executeCommand(JID fromNode, Set<JID> visitedNodes, Map<String, String> data,
				Queue<Element> packets) throws ClusterCommandException {
			
			try {
				BareJID roomJid = BareJID.bareJIDInstanceNS(data.get("room"));
				JID occupantJID = JID.jidInstanceNS(data.get("userId"));
				String nickname = data.get("nickname");
				Affiliation occupantAffiliation = Affiliation.valueOf(data.get("affiliation"));
				Role occupantRole = Role.valueOf(data.get("role"));
				boolean newOccupant = data.containsKey("new-occupant");
				// only one packet in packets queue
				Element presenceOrig = packets.poll();
				
				log.log( Level.FINEST, "executig OccupantChangedPresenceCmd command for room = {0}, occupantJID = {1},"
															 + "nickname: {2}, occupantAffiliation = {3}, occupantRole = {4}, newOccupant = {5} ",
								 new Object[] { roomJid.toString(), occupantJID.toString(), nickname,
																occupantAffiliation, occupantRole, newOccupant } );					

				PresenceModule presenceModule = ClusteredRoomStrategyV2.this.muc.getModule(PresenceModule.ID);
				RoomClustered room = (RoomClustered) ClusteredRoomStrategyV2.this.muc.getMucRepository().getRoom(roomJid);
								
				// update map of remote occupants
				if ("unavailable".equals(presenceOrig.getAttribute("type"))) {
					room.removeRemoteOccupant(occupantJID);
				}
				else {
					room.addRemoteOccupant(nickname, occupantJID, occupantRole, occupantAffiliation, presenceOrig);
				}
				
				//if (presenceOrig == null || "unavailable".equals(presenceOrig.getAttributeStaticStr("type"))) {
				// we should always use best presence, no matter what we will receive
				// as it will be included and processed before
				Element tmp = room.getLastPresenceCopy(occupantJID.getBareJID(), nickname);
				if (tmp != null && !"unavailable".equals(tmp.getAttributeStaticStr("type"))) {
					presenceOrig = tmp;
				}
				//}
				
				// broadcast remote occupant presece to all local occupants
				for (JID destinationJID : room.getAllOccupantsJID()) {
					// we need to clone original packet as PresenceWrapper will modify original element!
					Element presence = presenceOrig.clone();
					PresenceModule.PresenceWrapper presenceWrapper = PresenceModule.PresenceWrapper.preparePresenceW(room, destinationJID, presence, occupantJID.getBareJID(),
							Collections.singleton(occupantJID), nickname, occupantAffiliation, occupantRole);
					if (!"unavailable".equals(presence.getAttributeStaticStr("type"))) {
						PresenceModuleImpl.addCodes(presenceWrapper, false, nickname);
					}
					ClusteredRoomStrategyV2.this.muc.addOutPacket(presenceWrapper.getPacket());
				}
				// should be handled on original node
//				if (newOccupant) {
//					presenceModule.sendPresencesToNewOccupant(room, occupantJID);
//				}
			} catch (RepositoryException ex) {
				Logger.getLogger(AbstractClusteredRoomStrategy.class.getName()).log(Level.SEVERE, null, ex);
			} catch (MUCException ex) {
				Logger.getLogger(AbstractClusteredRoomStrategy.class.getName()).log(Level.SEVERE, null, ex);
			} catch (TigaseStringprepException ex) {
				Logger.getLogger(AbstractClusteredRoomStrategy.class.getName()).log(Level.SEVERE, null, ex);
			}
		}
	}
	
	private class OccupantsSyncRequestCmd extends CommandListenerAbstract {

		public OccupantsSyncRequestCmd() {
			super(OCCUPANTS_SYNC_REQUEST_CMD);
		}

		@Override
		public void executeCommand(JID fromNode, Set<JID> visitedNodes, Map<String, String> data, Queue<Element> packets) throws ClusterCommandException {
			List<Room> rooms = new ArrayList(muc.getMucRepository().getActiveRooms().values());
			log.log( Level.FINEST, "executig OccupantsSyncRequestCmd command fromNode = {0}, rooms = {1}",
								 new Object[] { fromNode.toString(), rooms.toString() } );	
			for (Room room : rooms) {
				for (String nickname : room.getOccupantsNicknames()) {
					Collection<JID> jids = room.getOccupantsJidsByNickname(nickname);
					if (jids.isEmpty()) {
						continue;
					}
					JID jid = jids.iterator().next();
					Element presence = room.getLastPresenceCopyByJid(jid.getBareJID());
					onOccupantChangedPresence(room, jid, nickname, presence, false);
				}
			}
		}
		
	}
	
	@Override
	public void setClusterController(ClusterControllerIfc cl_controller) {
		if (this.cl_controller != null) {
			this.cl_controller.removeCommandListener(occupantChangedPresenceCmd);
			this.cl_controller.removeCommandListener(occupantsSyncRequestCmd);
		}
		super.setClusterController(cl_controller);
		if (cl_controller != null) {
			cl_controller.setCommandListener(occupantChangedPresenceCmd);
			cl_controller.setCommandListener(occupantsSyncRequestCmd);
		}
	}

	@Override
	public void onOccupantChangedPresence(Room room, JID occupantJid, String nickname, Element presence, boolean newOccupant) {
		List<JID> toNodes = getNodesConnected();
		if (occupantJid != null && presence == null) {
			presence = new Element("presence", new String[] { "type", "xmlns" }, new String[] { "unavailable", Packet.CLIENT_XMLNS });
		}
		if (occupantJid == null) occupantJid = JID.jidInstanceNS(presence.getAttributeStaticStr("from"));
		Affiliation affiliation = room.getAffiliation(nickname);
		Role role = room.getRole(nickname);
		
		Map<String,String> data = new HashMap<String,String>();
		data.put("room", room.getRoomJID().toString());
		data.put("userId", occupantJid.toString());
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
	
		if (newOccupant) {
			// send presences of all remote occupants to new local occupant
			sendRemoteOccupantPresencesToLocalOccupant(room, occupantJid);
		}
	}
	
	protected void requestSync(JID nodeJid) {
		//cl_controller.sendToNodes(REQUEST_SYNC_CMD, localNodeJid, nodeJid);
		super.requestSync(nodeJid);
		cl_controller.sendToNodes(OCCUPANTS_SYNC_REQUEST_CMD, localNodeJid, nodeJid);
	}

	@Override
	protected void sendRemoteOccupantRemovalOnDisconnect(Room room, JID occupant, String occupantNick, boolean sendRemovalToOccupant) {
		super.sendRemoteOccupantRemovalOnDisconnect(room, occupant, occupantNick, sendRemovalToOccupant);
		RoomClustered croom = (RoomClustered) room;
		croom.removeRemoteOccupant(occupant);
	}	
	
	private void sendRemoteOccupantPresencesToLocalOccupant(Room room, JID occupantJid) {
		List<Occupant> occupants = new ArrayList<Occupant>(((RoomClustered)room).getRemoteOccupants());
		for (Occupant occupant : occupants) {
			try {
				sendRemoteOccupantPresenceToLocalOccupant(room, occupantJid, occupant);
			} catch (TigaseStringprepException ex) {
				log.log(Level.SEVERE, null, ex);
			}
		}	
	}
	
	private void sendRemoteOccupantPresenceToLocalOccupant(Room room, JID occupantJid, Occupant occupant) throws TigaseStringprepException {
		
		PresenceModule.PresenceWrapper presenceWrapper = PresenceModule.PresenceWrapper.preparePresenceW(room, occupantJid,
				occupant.getBestPresence().clone(), occupant.getOccupantJID(),
				occupant.getOccupants(), occupant.getNickname(), occupant.getAffiliation(), occupant.getRole());

		PresenceModuleImpl.addCodes(presenceWrapper, false, occupant.getNickname());
		ClusteredRoomStrategyV2.this.muc.addOutPacket(presenceWrapper.getPacket());
	}
	
}
