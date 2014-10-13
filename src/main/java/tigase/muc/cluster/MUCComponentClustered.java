/*
 * MUCComponentClustered.java
 *
 * Tigase Jabber/XMPP Server
 * Copyright (C) 2004-2013 "Tigase, Inc." <office@tigase.com>
 *
 */
package tigase.muc.cluster;

import java.util.Map;
import java.util.logging.*;
import tigase.cluster.api.*;
import tigase.component.exceptions.RepositoryException;
import tigase.component.modules.Module;
import tigase.conf.ConfigurationException;
import tigase.licence.*;
import tigase.muc.*;
import tigase.muc.repository.*;
import tigase.osgi.ModulesManagerImpl;
import tigase.server.*;
import tigase.xmpp.JID;

/**
 * MUCComponent is class implementing clustering support for MUCComponent.
 *
 * @author andrzej
 */
public class MUCComponentClustered extends MUCComponent
		implements ClusteredComponentIfc {

	private static final Logger log = Logger.getLogger(MUCComponentClustered.class.getCanonicalName());
	
	private static final String DEF_STRATEGY_CLASS_VAL = ShardingStrategy.class.getCanonicalName();
	private static final String STRATEGY_CLASS_KEY = "muc-strategy-class";
	private StrategyIfc strategy;

	protected LicenceChecker licenceChecker;

	private ComponentInfo         cmpInfo           = null;
	private ClusterControllerIfc cl_controller;

	public MUCComponentClustered() {
		licenceChecker = LicenceChecker.getLicenceChecker( "acs" );
		RoomClustered.initialize();
	}

	@Override
	public boolean addOutPacket(Packet packet) {
		return super.addOutPacket(packet);	
	}
	
	@Override
	protected IMucRepository createMucRepository(MucContext componentConfig, MucDAO dao) throws RepositoryException {
		InMemoryMucRepositoryClustered repo = new InMemoryMucRepositoryClustered(componentConfig, dao);
		strategy.setMucRepository(repo);
		return repo;
	}

	@Override
	public void nodeConnected(String node) {
		JID jid = JID.jidInstanceNS(getName(), node, null);
		strategy.nodeConnected(jid);
	}

	@Override
	public void nodeDisconnected(String node) {
		JID jid = JID.jidInstanceNS(getName(), node, null);
		strategy.nodeDisconnected(jid);
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
		this.cl_controller = cl_controller;
		if (strategy != null) {
			strategy.setClusterController(cl_controller);
		}
	}

	@Override
	public Map<String, Object> getDefaults(Map<String, Object> params) {
		Map<String,Object> defaults = super.getDefaults(params);
		defaults.put(STRATEGY_CLASS_KEY, DEF_STRATEGY_CLASS_VAL);
		return defaults;
	}
	
	@Override
	public void setProperties(Map<String, Object> props) throws ConfigurationException {
		if (props.size() > 1 && props.containsKey(STRATEGY_CLASS_KEY)) {
			String strategy_class = (String) props.get(STRATEGY_CLASS_KEY);
			try {
				strategy = (StrategyIfc) ModulesManagerImpl.getInstance().forName(strategy_class).newInstance();
				strategy.setMucComponentClustered(this);
				if (cl_controller != null) {
					strategy.setClusterController(cl_controller);
				}
			} catch (Exception ex) {
				if (!XMPPServer.isOSGi()) {
					log.log(Level.SEVERE, "Cannot instance clustering strategy class: "
							+ strategy_class, ex);
				}
				throw new ConfigurationException("Cannot instance clustering strategy class: "
						+ strategy_class);
			}
		}
		super.setProperties(props);
	}
	
	@Override
	public void start() {
		super.start();
		//strategy.start();
	}
	
	@Override
	public void stop() {
		strategy.stop();
		super.stop();
	}

	/**
	 * Allows to obtain various informations about components
	 *
	 * @return information about particular component
	 */
	@Override
	public ComponentInfo getComponentInfo() {
		cmpInfo = super.getComponentInfo();
		cmpInfo.getComponentData().put("MUCClusteringStrategy", (strategy != null)
				? strategy.getClass()
				: null);

		return cmpInfo;
	}

	protected <T extends Module> T getModule(String id) {
		return (T) this.modulesManager.getModule(id);
	}
	
}
