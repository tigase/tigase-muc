/**
 * Tigase ACS - MUC Component - Tigase Advanced Clustering Strategy - MUC Component
 * Copyright (C) 2013 Tigase, Inc. (office@tigase.com) - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package tigase.muc.cluster;

import tigase.cluster.api.ClusterControllerIfc;
import tigase.kernel.beans.Initializable;
import tigase.kernel.beans.UnregisterAware;
import tigase.server.Packet;
import tigase.xmpp.jid.BareJID;
import tigase.xmpp.jid.JID;

import java.util.List;

/**
 * @author andrzej
 */
public interface StrategyIfc
		extends Initializable, UnregisterAware {

	/**
	 * Method called when new node connected
	 */
	void nodeConnected(JID nodeJid);

	/**
	 * Method called when node was disconnected
	 */
	void nodeDisconnected(JID nodeJid);

	/**
	 * Setter used to set instance of cluster controller
	 */
	void setClusterController(ClusterControllerIfc cl_controller);

	/**
	 * Setter used to pass instance of MUCComponentClustered
	 */
	void setMucComponentClustered(MUCComponentClustered mucComponent);

	/**
	 * Method called when packet is received by component to preprocess packet before/instead of passing it back to non
	 * clustered component
	 *
	 * @return true if packet was fully processed
	 */
	boolean processPacket(Packet packet);

	/**
	 * The method returns cluster nodes currently connected to the cluster node.
	 *
	 * @return List of cluster nodes currently connected to the cluster node.
	 */
	List<JID> getNodesConnected();

	/**
	 * The method returns cluster nodes currently connected to the cluster including jid of this cluster node.
	 *
	 * @return List of cluster nodes currently connected to the cluster.
	 */
	List<JID> getNodesConnectedWithLocal();

	/**
	 * Setter to pass instance of InMemoryMucRepositoryClustered
	 *
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

	default void beforeUnregister() {
		stop();
	}

	default void initialize() {
		start();
	}

	default boolean shouldSendMessageOfflineToJidFromLocalNode(BareJID jid) {
		return true;
	}
}
