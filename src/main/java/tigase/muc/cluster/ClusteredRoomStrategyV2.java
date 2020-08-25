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
import tigase.kernel.beans.Bean;
import tigase.kernel.beans.Inject;
import tigase.muc.Affiliation;
import tigase.muc.Role;
import tigase.muc.Room;
import tigase.muc.modules.PresenceModule;
import tigase.muc.modules.PresenceModuleImpl;
import tigase.server.Packet;
import tigase.server.Priority;
import tigase.util.stringprep.TigaseStringprepException;
import tigase.xml.Element;
import tigase.xmpp.jid.BareJID;
import tigase.xmpp.jid.JID;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author andrzej
 */
public class ClusteredRoomStrategyV2
		extends AbstractClusteredRoomStrategy {

	private static final Logger log = Logger.getLogger(ClusteredRoomStrategyV2.class.getCanonicalName());

	private static final String OCCUPANTS_REMOTE_KEY = "occupants-remote-key";
	private static final String OCCUPANT_PRESENCE_CMD = "muc-occupant-presence-cmd";
	private static final String OCCUPANTS_SYNC_REQUEST_CMD = "request-occupants-sync";

	@Override
	public void onOccupantChangedPresence(Room room, JID occupantJid, String nickname, Element presence,
										  boolean newOccupant) {
		List<JID> toNodes = getNodesConnected();
		if (occupantJid != null && presence == null) {
			presence = new Element("presence", new String[]{"type", "xmlns"},
								   new String[]{"unavailable", Packet.CLIENT_XMLNS});
		}
		if (occupantJid == null) {
			occupantJid = JID.jidInstanceNS(presence.getAttributeStaticStr("from"));
		}
		Affiliation affiliation = room.getAffiliation(nickname).getAffiliation();
		Role role = room.getRole(nickname);

		Map<String, String> data = new HashMap<String, String>();
		data.put("room", room.getRoomJID().toString());
		data.put("userId", occupantJid.toString());
		data.put("nickname", nickname);
		data.put("affiliation", affiliation.name());
		data.put("role", role.name());
		if (newOccupant) {
			data.put("new-occupant", String.valueOf(newOccupant));
		}

		if (log.isLoggable(Level.FINEST)) {
			StringBuilder buf = new StringBuilder(100);
			for (JID node : toNodes) {
				if (buf.length() > 0) {
					buf.append(",");
				}
				buf.append(node.toString());
			}
			log.log(Level.FINEST,
					"room = {0}, notifing nodes [{1}] that occupant {2} in room {3} changed presence = {4}",
					new Object[]{room.getRoomJID(), buf, occupantJid, room.getRoomJID(), presence});
		}

		cl_controller.sendToNodes(OCCUPANT_PRESENCE_CMD, data, presence, localNodeJid, null,
								  toNodes.toArray(new JID[toNodes.size()]));

		if (newOccupant) {
			// send presences of all remote occupants to new local occupant
			sendRemoteOccupantPresencesToLocalOccupant(room, occupantJid);
		}
	}

	protected void requestSync(JID nodeJid) {
		// cl_controller.sendToNodes(REQUEST_SYNC_CMD, localNodeJid, nodeJid);
		super.requestSync(nodeJid);
		cl_controller.sendToNodes(OCCUPANTS_SYNC_REQUEST_CMD, localNodeJid, nodeJid);
	}

	@Override
	protected void sendRemoteOccupantRemovalOnDisconnect(Room room, JID occupant, String occupantNick,
														 boolean sendRemovalToOccupant) {
		super.sendRemoteOccupantRemovalOnDisconnect(room, occupant, occupantNick, sendRemovalToOccupant);
		RoomClustered croom = (RoomClustered) room;
		croom.removeRemoteOccupant(occupant);
	}

	private void sendRemoteOccupantPresencesToLocalOccupant(Room room, JID occupantJid) {
		List<Occupant> occupants = new ArrayList<Occupant>(((RoomClustered) room).getRemoteOccupants());
		for (Occupant occupant : occupants) {
			try {
				sendRemoteOccupantPresenceToLocalOccupant(room, occupantJid, occupant);
			} catch (TigaseStringprepException ex) {
				log.log(Level.SEVERE, null, ex);
			}
		}
	}

	private void sendRemoteOccupantPresenceToLocalOccupant(Room room, JID occupantJid, Occupant occupant)
			throws TigaseStringprepException {
		Element occupantPresence = occupant.getBestPresence();
		if (occupantPresence == null) {
			// it may be null if remote occupant left but room had not removed it from the remote occupants list
			return;
		}

		PresenceModule.PresenceWrapper presenceWrapper = PresenceModule.PresenceWrapper.preparePresenceW(room,
																										 occupantJid,
																										 occupantPresence.clone(),
																										 occupant.getOccupantJID(),
																										 occupant.getOccupants(),
																										 occupant.getNickname(),
																										 occupant.getAffiliation(),
																										 occupant.getRole());

		PresenceModuleImpl.addCodes(presenceWrapper, false, occupant.getNickname());
		mucComponentClustered.addOutPacket(presenceWrapper.getPacket());
	}

	@Bean(name = OCCUPANT_PRESENCE_CMD, parent = ClusteredRoomStrategyV2.class, active = true)
	public static class OccupantChangedPresenceCmd
			extends CommandListenerAbstract {

		@Inject
		private MUCComponentClustered mucComponentClustered;
		@Inject
		private InMemoryMucRepositoryClustered mucRepository;

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
				// only one packet in packets queue
				Element presenceOrig = packets.poll();

				log.log(Level.FINEST, "executig OccupantChangedPresenceCmd command for room = {0}, occupantJID = {1}," +
								"nickname: {2}, occupantAffiliation = {3}, occupantRole = {4}, newOccupant = {5} ",
						new Object[]{roomJid.toString(), occupantJID.toString(), nickname, occupantAffiliation,
									 occupantRole, newOccupant});

				RoomClustered room = (RoomClustered) mucRepository.getRoom(roomJid);

				// update map of remote occupants
				if ("unavailable".equals(presenceOrig.getAttributeStaticStr("type"))) {
					room.removeRemoteOccupant(occupantJID);
				} else {
					room.addRemoteOccupant(nickname, occupantJID, occupantRole, occupantAffiliation, presenceOrig);
				}

				// if (presenceOrig == null ||
				// "unavailable".equals(presenceOrig.getAttributeStaticStr("type")))
				// {
				// we should always use best presence, no matter what we will
				// receive
				// as it will be included and processed before
				Element tmp = room.getLastPresenceCopy(occupantJID.getBareJID(), nickname);
				if (tmp != null && !"unavailable".equals(tmp.getAttributeStaticStr("type"))) {
					presenceOrig = tmp;
				}
				// }

				// broadcast remote occupant presece to all local occupants
				for (JID destinationJID : room.getAllOccupantsJID()) {
					// we need to clone original packet as PresenceWrapper will
					// modify original element!
					Element presence = presenceOrig.clone();
					PresenceModule.PresenceWrapper presenceWrapper = PresenceModule.PresenceWrapper.preparePresenceW(
							room, destinationJID, presence, occupantJID.getBareJID(),
							Collections.singleton(occupantJID), nickname, occupantAffiliation, occupantRole);
					if (!"unavailable".equals(presence.getAttributeStaticStr("type"))) {
						PresenceModuleImpl.addCodes(presenceWrapper, false, nickname);
					}
					mucComponentClustered.addOutPacket(presenceWrapper.getPacket());
				}

				// should be handled on original node
				// if (newOccupant) {
				// presenceModule.sendPresencesToNewOccupant(room, occupantJID);
				// }
			} catch (RepositoryException ex) {
				Logger.getLogger(AbstractClusteredRoomStrategy.class.getName()).log(Level.SEVERE, null, ex);
			} catch (TigaseStringprepException ex) {
				Logger.getLogger(AbstractClusteredRoomStrategy.class.getName()).log(Level.SEVERE, null, ex);
			}
		}
	}

	@Bean(name = OCCUPANTS_SYNC_REQUEST_CMD, parent = ClusteredRoomStrategyV2.class, active = true)
	public static class OccupantsSyncRequestCmd
			extends CommandListenerAbstract {

		@Inject
		private InMemoryMucRepositoryClustered mucRepository;

		@Inject
		private ClusteredRoomStrategyV2 strategy;

		public OccupantsSyncRequestCmd() {
			super(OCCUPANTS_SYNC_REQUEST_CMD, Priority.HIGH);
		}

		@Override
		public void executeCommand(JID fromNode, Set<JID> visitedNodes, Map<String, String> data,
								   Queue<Element> packets) throws ClusterCommandException {
			List<Room> rooms = new ArrayList(mucRepository.getActiveRooms().values());
			log.log(Level.FINEST, "executig OccupantsSyncRequestCmd command fromNode = {0}, rooms = {1}",
					new Object[]{fromNode.toString(), rooms.toString()});
			for (Room room : rooms) {
				for (String nickname : room.getOccupantsNicknames(false)) {
					Collection<JID> jids = room.getOccupantsJidsByNickname(nickname);
					if (jids.isEmpty()) {
						continue;
					}
					JID jid = jids.iterator().next();
					Element presence = room.getLastPresenceCopyByJid(jid.getBareJID());
					strategy.onOccupantChangedPresence(room, jid, nickname, presence, false);
				}
			}
		}

	}

}
