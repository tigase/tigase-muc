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
import tigase.muc.RoomConfig;
import tigase.muc.exceptions.MUCException;
import tigase.muc.modules.GroupchatMessageModule;
import tigase.muc.modules.PresenceModule;
import tigase.muc.modules.PresenceModule.PresenceWrapper;
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
 * - may not work if occupants are joining from S2S connections
 * - possible issues with changing affiliations or room configuration
 * 
 * @author andrzej
 */
public class ClusteredRoomStrategy extends AbstractStrategy implements StrategyIfc,
		InMemoryMucRepositoryClustered.RoomListener, Room.RoomListener {

	private static final Logger log = Logger.getLogger(ClusteredRoomStrategy.class.getCanonicalName());
	
//	private static final String OCCUPANT_ADDED_CMD = "muc-occupant-added-cmd";
//	private static final String OCCUPANT_REMOVED_CMD = "muc-occupant-removed-cmd";
	private static final String OCCUPANT_PRESENCE_CMD = "muc-occupant-presence-cmd";
	private static final String ROOM_CREATED_CMD = "muc-room-created-cmd";
	private static final String ROOM_DESTROYED_CMD = "muc-room-destroyed-cmd";
	private static final String ROOM_MESSAGE_CMD = "muc-room-message-cmd";
	
	private final OccupantChangedPresenceCmd occupantChangedPresenceCmd = new OccupantChangedPresenceCmd();
	private final RoomCreatedCmd roomCreatedCmd = new RoomCreatedCmd();
	private final RoomDestroyedCmd roomDestroyedCmd = new RoomDestroyedCmd();
	private final RoomMessageCmd roomMessageCmd = new RoomMessageCmd();
	
	@Override
	public boolean processPacket(Packet packet) {
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
			this.cl_controller.removeCommandListener(occupantChangedPresenceCmd);
			this.cl_controller.removeCommandListener(roomCreatedCmd);
			this.cl_controller.removeCommandListener(roomDestroyedCmd);
			this.cl_controller.removeCommandListener(roomMessageCmd);
		}
		super.setClusterController(cl_controller);
		if (cl_controller != null) {
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
				
				PresenceModule presenceModule = ClusteredRoomStrategy.this.muc.getModule(PresenceModule.class);
				Room room = ClusteredRoomStrategy.this.muc.getMucRepository().getRoom(roomJid);
				for (Element presence : packets) {
					for (JID destinationJID : room.getAllOccupantsJID()) {
						PresenceWrapper presenceWrapper = presenceModule.preparePresenceW(room, destinationJID, presence, occupantJID.getBareJID(),
								Collections.singleton(occupantJID), nickname, occupantAffiliation, occupantRole);
						if (!"unavailable".equals(presence.getAttributeStaticStr("type"))) {
							presenceModule.addCodes(presenceWrapper, false, nickname);
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
					PresenceWrapper presence = PresenceModule.preparePresenceW(room, occupantJid, p, occupantJid.getBareJID(), Collections.singleton(occupantJid),
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
				GroupchatMessageModule groupchatModule = ClusteredRoomStrategy.this.muc.getModule(GroupchatMessageModule.class);
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
}
