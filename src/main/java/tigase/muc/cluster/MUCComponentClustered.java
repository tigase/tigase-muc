/*
 * MUCComponentClustered.java
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

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import tigase.cluster.api.ClusterControllerIfc;
import tigase.cluster.api.ClusteredComponentIfc;
import tigase.component.exceptions.RepositoryException;
import tigase.muc.MUCComponent;
import tigase.muc.MucConfig;
import tigase.muc.repository.IMucRepository;
import tigase.muc.repository.MucDAO;
import tigase.server.Packet;
import tigase.xmpp.JID;

/**
 * MUCComponent is class implementing clusering support for MUCComponent.
 *
 * @author andrzej
 */
public class MUCComponentClustered extends MUCComponent
		implements ClusteredComponentIfc {

	private static final Logger log = Logger.getLogger(MUCComponentClustered.class.getCanonicalName());
	
	private static final String DEF_STRATEGY_CLASS_VAL = DefaultStrategy.class.getCanonicalName();
	private static final String STRATEGY_CLASS_KEY = "muc-strategy-class";
	private StrategyIfc strategy;

	@Override
	protected IMucRepository createMucRepository(MucConfig componentConfig, MucDAO dao) throws RepositoryException {
		InMemoryMucRepositoryClustered repo = new InMemoryMucRepositoryClustered(componentConfig, dao);
		strategy.setMucRepository(repo);
		return repo;
	}

	@Override
	public void nodeConnected(String node) {
		strategy.nodeConnected(JID.jidInstanceNS(node));
	}

	@Override
	public void nodeDisconnected(String node) {
		strategy.nodeDisconnected(JID.jidInstanceNS(node));
	}

	@Override
	public void processPacket(Packet packet) {
		boolean handled = strategy.processPacket(packet);

		if (!handled) {
			super.processPacket(packet);
		}
	}

	@Override
	public void setClusterController(ClusterControllerIfc cl_controller) {
		strategy.setClusterController(cl_controller);
	}

	@Override
	public Map<String, Object> getDefaults(Map<String, Object> params) {
		Map<String,Object> defaults = super.getDefaults(params);
		defaults.put(STRATEGY_CLASS_KEY, DEF_STRATEGY_CLASS_VAL);
		return defaults;
	}
	
	@Override
	public void setProperties(Map<String, Object> props) {
		if (props.size() > 1 && props.containsKey(STRATEGY_CLASS_KEY)) {
			String strategy_class = (String) props.get(STRATEGY_CLASS_KEY);
			try {
				strategy = (StrategyIfc) Class.forName(strategy_class).newInstance();
				strategy.setMucComponentClustered(this);
			} catch (Exception ex) {
				log.log(Level.SEVERE, "Cannot instance clustering strategy class: "
						+ strategy_class, ex);
			}
		}
		super.setProperties(props);
	}
}
