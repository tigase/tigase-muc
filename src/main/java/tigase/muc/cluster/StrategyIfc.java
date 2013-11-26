/*
 * StrategyIfc.java
 *
 * Tigase Jabber/XMPP Server
 * Copyright (C) 2004-2013 "Tigase, Inc." <office@tigase.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. Look for COPYING file in the top folder.
 * If not, see http://www.gnu.org/licenses/.
 *
 */
package tigase.muc.cluster;

import tigase.cluster.api.ClusterControllerIfc;
import tigase.server.Packet;
import tigase.xmpp.BareJID;
import tigase.xmpp.JID;

/**
 *
 * @author andrzej
 */
public interface StrategyIfc {
	
	/**
	 * Method called when new node connected
	 * 
	 * @param nodeJid 
	 */
	void nodeConnected(JID nodeJid);
	
	/**
	 * Method called when node was disconnected
	 * 
	 * @param nodeJid 
	 */
	void nodeDisconnected(JID nodeJid);
	
	/**
	 * Setter used to set instance of cluster controller
	 * 
	 * @param cl_controller 
	 */
	void setClusterController(ClusterControllerIfc cl_controller);
	
	/**
	 * Setter used to pass instance of MUCComponentClustered
	 * 
	 * @param mucComponent 
	 */
	void setMucComponentClustered(MUCComponentClustered mucComponent);
	
	/**
	 * Method called when packet is received by component to preprocess packet
	 * before/instead of passing it back to non clustered component
	 * 
	 * @param packet
	 * @return true if packet was fully processed
	 */
	boolean processPacket(Packet packet);
	
	/**
	 * Retrieve JID of node which is hosting this room
	 * 
	 * @param roomJid
	 * @return 
	 */
	JID getNodeForRoom(BareJID roomJid);
	
	/**
	 * Setter to pass instnace of InMemoryMucRepositoryClustered
	 * 
	 * @param mucRepository 
	 */
	void setMucRepository(InMemoryMucRepositoryClustered mucRepository);
	
	/**
	 * Method called when component is started
	 */
	void start();
	
	/**
	 * Method called when component is stopped
	 */
	void stop();
}
