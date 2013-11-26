/*
 * StrategyIfc.java
 *
 * Tigase Jabber/XMPP Server
 * Copyright (C) 2004-2013 "Tigase, Inc." <office@tigase.com>
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
