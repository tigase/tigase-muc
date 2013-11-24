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
	
	void nodeConnected(JID nodeJid);
	
	void nodeDisconnected(JID nodeJid);
	
	void setClusterController(ClusterControllerIfc cl_controller);
	
	void setMucComponentClustered(MUCComponentClustered mucComponent);
	
	boolean processPacket(Packet packet);
	
	JID getNodeForRoom(BareJID roomJid);
	
	void setMucRepository(InMemoryMucRepositoryClustered mucRepository);
}
