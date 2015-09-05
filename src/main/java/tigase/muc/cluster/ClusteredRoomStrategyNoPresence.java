/*
 * ConnectionRecordExt.java
 *
 * Tigase Jabber/XMPP Server
 * Copyright (C) 2004-2013 "Tigase, Inc." <office@tigase.com>
 *
 */

package tigase.muc.cluster;

import tigase.cluster.api.ClusterCommandException;
import tigase.cluster.api.ClusterControllerIfc;
import tigase.cluster.api.CommandListenerAbstract;

import tigase.server.Packet;

import tigase.xmpp.BareJID;
import tigase.xmpp.JID;

import tigase.muc.Affiliation;
import tigase.muc.Role;
import tigase.muc.Room;
import tigase.xml.Element;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Stripped down strategy that suppress sending MUC presence between rooms.
 *
 * @author Wojciech Kapcia
 */
public class ClusteredRoomStrategyNoPresence extends AbstractClusteredRoomStrategy {

	private static final Logger log = Logger.getLogger( ClusteredRoomStrategyNoPresence.class.getCanonicalName() );

	private static final String OCCUPANTS_REMOTE_KEY = "occupants-remote-key";
	private static final String OCCUPANT_PRESENCE_CMD = "muc-occupant-presence-cmd";
	private static final String OCCUPANTS_SYNC_REQUEST_CMD = "request-occupants-sync";

	private final OccupantChangedPresenceCmd occupantChangedPresenceCmd = new OccupantChangedPresenceCmd();
	private final OccupantsSyncRequestCmd occupantsSyncRequestCmd = new OccupantsSyncRequestCmd();

	private class OccupantChangedPresenceCmd extends CommandListenerAbstract {

		public OccupantChangedPresenceCmd() {
			super( OCCUPANT_PRESENCE_CMD );
		}

		@Override
		public void executeCommand( JID fromNode, Set<JID> visitedNodes, Map<String, String> data,
																Queue<Element> packets ) throws ClusterCommandException {

			BareJID roomJid = BareJID.bareJIDInstanceNS( data.get( "room" ) );
			JID occupantJID = JID.jidInstanceNS( data.get( "userId" ) );
			String nickname = data.get( "nickname" );
			Affiliation occupantAffiliation = Affiliation.valueOf( data.get( "affiliation" ) );
			Role occupantRole = Role.valueOf( data.get( "role" ) );
			boolean newOccupant = data.containsKey( "new-occupant" );

			log.log( Level.FINEST, "executig OccupantChangedPresenceCmd command for room = {0}, occupantJID = {1},"
														 + "nickname: {2}, occupantAffiliation = {3}, occupantRole = {4}, newOccupant = {5} ",
							 new Object[] { roomJid.toString(), occupantJID.toString(), nickname,
															occupantAffiliation, occupantRole, newOccupant } );
		}
	}

	private class OccupantsSyncRequestCmd extends CommandListenerAbstract {

		public OccupantsSyncRequestCmd() {
			super( OCCUPANTS_SYNC_REQUEST_CMD );
		}

		@Override
		public void executeCommand( JID fromNode, Set<JID> visitedNodes, Map<String, String> data, Queue<Element> packets ) throws ClusterCommandException {
			List<Room> rooms = new ArrayList( muc.getMucRepository().getActiveRooms().values() );
			log.log( Level.FINEST, "executig OccupantsSyncRequestCmd command fromNode = {0}, rooms = {1}",
							 new Object[] { fromNode.toString(), rooms.toString() } );
		}

	}

	@Override
	public void setClusterController( ClusterControllerIfc cl_controller ) {
		if ( this.cl_controller != null ){
			this.cl_controller.removeCommandListener( occupantChangedPresenceCmd );
			this.cl_controller.removeCommandListener( occupantsSyncRequestCmd );
		}
		super.setClusterController( cl_controller );
		if ( cl_controller != null ){
			cl_controller.setCommandListener( occupantChangedPresenceCmd );
			cl_controller.setCommandListener( occupantsSyncRequestCmd );
		}
	}

	@Override
	public void onOccupantChangedPresence( Room room, JID occupantJid, String nickname, Element presence, boolean newOccupant ) {
		List<JID> toNodes = getNodesConnected();
		toNodes.remove( localNodeJid );
		if ( occupantJid != null && presence == null ){
			presence = new Element( "presence", new String[] { "type", "xmlns" }, new String[] { "unavailable", Packet.CLIENT_XMLNS } );
		}
		if ( occupantJid == null ) {
			occupantJid = JID.jidInstanceNS( presence.getAttributeStaticStr( "from" ) );
		}
		Affiliation affiliation = room.getAffiliation( occupantJid.getBareJID() );
		Role role = room.getRole( nickname );

		Map<String, String> data = new HashMap<String, String>();
		data.put( "room", room.getRoomJID().toString() );
		data.put( "userId", occupantJid.toString() );
		data.put( "nickname", nickname );
		data.put( "affiliation", affiliation.name() );
		data.put( "role", role.name() );
		if ( newOccupant ) {
			data.put( "new-occupant", String.valueOf( newOccupant ) );
		}

		if ( log.isLoggable( Level.FINEST ) ){
			StringBuilder buf = new StringBuilder( 100 );
			for ( JID node : toNodes ) {
				if ( buf.length() > 0 ){
					buf.append( "," );
				}
				buf.append( node.toString() );
			}
			log.log( Level.FINEST, "room = {0}, notifing nodes [{1}] that occupant {2} in room {3} changed presence = {4}",
							 new Object[] { room.getRoomJID(), buf, occupantJid, room.getRoomJID(), presence } );
		}

	}

	@Override
	protected void requestSync( JID nodeJid ) {
	}

	private ConcurrentMap<String, Occupant> getRemoteOccupants( Room room ) {
		ConcurrentMap<String, Occupant> occupants = (ConcurrentMap<String, Occupant>) room.getRoomCustomData( OCCUPANTS_REMOTE_KEY );
		if ( occupants == null ){
			synchronized ( room ) {
				occupants = new ConcurrentHashMap<String, Occupant>();
				ConcurrentMap<String, Occupant> tmp = (ConcurrentMap<String, Occupant>) room.getRoomCustomData( OCCUPANTS_REMOTE_KEY );
				if ( tmp == null ){
					room.setRoomCustomData( OCCUPANTS_REMOTE_KEY, occupants );
				} else {
					occupants = tmp;
				}
			}
		}
		return occupants;
	}

	@Override
	protected void sendRemoteOccupantRemovalOnDisconnect( Room room, JID occupant, String occupantNick, boolean sendRemovalToOccupant ) {
		super.sendRemoteOccupantRemovalOnDisconnect( room, occupant, occupantNick, sendRemovalToOccupant );
		Map<String, Occupant> occupants = getRemoteOccupants( room );
		occupants.remove( occupantNick );
	}

	private class Occupant {

		private JID occupantJID;
		private String nickname;
		private Affiliation affiliation;
		private Role role;
		private Element presence;

		public Occupant() {
		}

		public JID getOccupantJID() {
			return occupantJID;
		}

		public void setOccupantJID( JID occupantJID ) {
			this.occupantJID = occupantJID;
		}

		public String getNickname() {
			return nickname;
		}

		public void setNickname( String nickname ) {
			this.nickname = nickname;
		}

		public Affiliation getAffiliation() {
			return affiliation;
		}

		public void setAffiliation( Affiliation affiliation ) {
			this.affiliation = affiliation;
		}

		public Role getRole() {
			return role;
		}

		public void setRole( Role role ) {
			this.role = role;
		}

		public Element getPresence() {
			return presence;
		}

		public void setPresence( Element presence ) {
			this.presence = presence;
		}

	}
}
