/*
 * ClusteredRoomStrategy.java
 *
 * Tigase Jabber/XMPP Server
 * Copyright (C) 2004-2014 "Tigase, Inc." <office@tigase.com>
 *
 */

package tigase.muc.cluster;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
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

import java.util.Arrays;
import tigase.server.Priority;

/**
 *
 * @author andrzej
 */
public class ClusteredRoomStrategy extends AbstractClusteredRoomStrategy {

	private static final Logger log = Logger.getLogger(ClusteredRoomStrategy.class.getCanonicalName());
	
	private static final String OCCUPANT_PRESENCE_CMD = "muc-occupant-presence-cmd";
	
	private final OccupantChangedPresenceCmd occupantChangedPresenceCmd = new OccupantChangedPresenceCmd();	
	
	private class OccupantChangedPresenceCmd extends CommandListenerAbstract {

		public OccupantChangedPresenceCmd() {
			super(OCCUPANT_PRESENCE_CMD, Priority.HIGH);
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
				
				if ( log.isLoggable( Level.FINEST ) ){
					log.log( Level.FINEST, "executig OccupantChangedPresenceCmd command for room = {0}, occupantJID = {1},"
																 + "nickname: {2}, occupantAffiliation = {3}, occupantRole = {4}, newOccupant = {5} ",
									 new Object[] { roomJid, occupantJID, nickname,
																	occupantAffiliation, occupantRole, newOccupant } );
				}
				
				PresenceModule presenceModule = ClusteredRoomStrategy.this.muc.getModule(PresenceModule.ID);
				Room room = ClusteredRoomStrategy.this.muc.getMucRepository().getRoom(roomJid);
				for (Element presenceOrig : packets) {
					for (JID destinationJID : room.getAllOccupantsJID()) {
						// we need to clone original packet as PresenceWrapper will modify original element!
						Element presence = presenceOrig.clone();
						PresenceModule.PresenceWrapper presenceWrapper = PresenceModule.PresenceWrapper.preparePresenceW(room, destinationJID, presence, occupantJID.getBareJID(),
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
				Logger.getLogger(AbstractClusteredRoomStrategy.class.getName()).log(Level.SEVERE, null, ex);
			} catch (MUCException ex) {
				Logger.getLogger(AbstractClusteredRoomStrategy.class.getName()).log(Level.SEVERE, null, ex);
			} catch (TigaseStringprepException ex) {
				Logger.getLogger(AbstractClusteredRoomStrategy.class.getName()).log(Level.SEVERE, null, ex);
			}
		}
	}
	
	@Override
	public void setClusterController(ClusterControllerIfc cl_controller) {
		if (this.cl_controller != null) {
			this.cl_controller.removeCommandListener(occupantChangedPresenceCmd);
		}
		super.setClusterController(cl_controller);
		if (cl_controller != null) {
			cl_controller.setCommandListener(occupantChangedPresenceCmd);
		}
	}

	@Override
	public void onOccupantChangedPresence(Room room, JID occupantJid, String nickname, Element presence, boolean newOccupant) {
		List<JID> toNodes = getNodesConnected();
		if (occupantJid != null && presence == null) {
			presence = new Element("presence", new String[] { "type", "xmlns" }, new String[] { "unavailable", Packet.CLIENT_XMLNS });
		}
		if (occupantJid == null) occupantJid = JID.jidInstanceNS(presence.getAttributeStaticStr("from"));
		Affiliation affiliation = room.getAffiliation(occupantJid.getBareJID());
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
					new Object[]{room, buf, occupantJid, room, presence});
		}
		
//		cl_controller.sendToNodes(OCCUPANT_PRESENCE_CMD, data, presence, localNodeJid, null, toNodes.toArray(new JID[toNodes.size()]));
	}
	
}
