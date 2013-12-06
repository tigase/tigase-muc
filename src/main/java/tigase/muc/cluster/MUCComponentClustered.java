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
import tigase.licence.*;
import tigase.muc.*;
import tigase.muc.repository.*;
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
	
	private static final String DEF_STRATEGY_CLASS_VAL = DefaultStrategy.class.getCanonicalName();
	private static final String STRATEGY_CLASS_KEY = "muc-strategy-class";
	private StrategyIfc strategy;

	protected LicenceChecker licenceChecker;

	private ComponentInfo         cmpInfo           = null;

	public MUCComponentClustered() {
		licenceChecker = LicenceChecker.getLicenceChecker( "acs-muc" );
	}

	@Override
	public boolean addOutPacket(Packet packet) {
		return super.addOutPacket(packet);
	}
	
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
	
}
