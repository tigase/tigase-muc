/*
 * MUCComponentClustered.java
 *
 * Tigase Jabber/XMPP Server
 * Copyright (C) 2004-2013 "Tigase, Inc." <office@tigase.com>
 *
 */
package tigase.muc.cluster;

import tigase.cluster.api.ClusterControllerIfc;
import tigase.cluster.api.ClusteredComponentIfc;
import tigase.kernel.core.Kernel;
import tigase.licence.LicenceChecker;
import tigase.muc.MUCComponent;
import tigase.server.ComponentInfo;
import tigase.server.Packet;
import tigase.xmpp.JID;

import java.util.logging.Logger;

/**
 * MUCComponent is class implementing clustering support for MUCComponent.
 *
 * @author andrzej
 */
public class MUCComponentClustered extends MUCComponent implements ClusteredComponentIfc {

	private static final Logger log = Logger.getLogger(MUCComponentClustered.class.getCanonicalName());

	private static final String DEF_STRATEGY_CLASS_VAL = ShardingStrategy.class.getCanonicalName();

	private static final String STRATEGY_CLASS_KEY = "muc-strategy-class";

	protected LicenceChecker licenceChecker;

	private ComponentInfo cmpInfo = null;
	private ClusterControllerIfc cl_controller;

	public MUCComponentClustered() {
		licenceChecker = LicenceChecker.getLicenceChecker("acs");
		RoomClustered.initialize();

		String clusterProperty = System.getProperty( "cluster-mode" );
		if ( clusterProperty == null || !Boolean.parseBoolean( clusterProperty ) ){
			log.severe( "You've tried using Clustered version of the component but cluster-mode is disabled" );
			log.severe( "Shutting down system!" );
			System.exit( 1 );
		}
		String strategyProp = System.getProperty( "sm-cluster-strategy-class" );
		if ( strategyProp == null || !"tigase.server.cluster.strategy.OnlineUsersCachingStrategy".equals( strategyProp) ){
			log.severe( "You've tried using Clustered version of the component but ACS is disabled" );
			log.severe( "Shutting down system!" );
			System.exit( 1 );
		}
	}

	@Override
	public boolean addOutPacket(Packet packet) {
		return super.addOutPacket(packet);
	}

	@Override
	protected void onNodeConnected(JID jid) {
		super.onNodeConnected(jid);
		StrategyIfc strategy = kernel.getInstance(StrategyIfc.class);
		strategy.nodeConnected(jid);
	}

	@Override
	public void onNodeDisconnected(JID jid) {
		super.onNodeDisconnected(jid);
		StrategyIfc strategy = kernel.getInstance(StrategyIfc.class);
		strategy.nodeDisconnected(jid);
	}

	@Override
	public void processPacket(Packet packet) {
		StrategyIfc strategy = kernel.getInstance(StrategyIfc.class);
		boolean handled = strategy.processPacket(packet);

		if (!handled) {
			super.processPacket(packet);
		}
	}

	@Override
	public void setClusterController(ClusterControllerIfc cl_controller) {
		super.setClusterController(cl_controller);
		StrategyIfc strategy = kernel.getInstance(StrategyIfc.class);
		this.cl_controller = cl_controller;
		if (strategy != null) {
			strategy.setClusterController(cl_controller);
		}
	}

	@Override
	protected void registerModules(Kernel kernel) {
		super.registerModules(kernel);

		kernel.registerBean("strategy").asClass(ShardingStrategy.class).exec();
		kernel.registerBean(InMemoryMucRepositoryClustered.class).exec();
	}

	@Override
	public void start() {
		super.start();
		// strategy.start();
	}

	@Override
	public void stop() {
		StrategyIfc strategy = kernel.getInstance(StrategyIfc.class);
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
		StrategyIfc strategy = kernel.getInstance(StrategyIfc.class);
		cmpInfo = super.getComponentInfo();
		cmpInfo.getComponentData().put("MUCClusteringStrategy", (strategy != null) ? strategy.getClass() : null);

		return cmpInfo;
	}

}
