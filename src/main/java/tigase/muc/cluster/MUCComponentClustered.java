/**
 * Tigase ACS - MUC Component - Tigase Advanced Clustering Strategy - MUC Component
 * Copyright (C) 2013 Tigase, Inc. (office@tigase.com) - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package tigase.muc.cluster;

import tigase.cluster.api.ClusterControllerIfc;
import tigase.cluster.api.ClusteredComponentIfc;
import tigase.cluster.api.CommandListener;
import tigase.kernel.beans.Bean;
import tigase.kernel.beans.Initializable;
import tigase.kernel.beans.Inject;
import tigase.kernel.beans.selector.ClusterModeRequired;
import tigase.kernel.beans.selector.ConfigType;
import tigase.kernel.beans.selector.ConfigTypeEnum;
import tigase.kernel.beans.selector.ServerBeanSelector;
import tigase.kernel.core.Kernel;
import tigase.licence.LicenceChecker;
import tigase.muc.MUCComponent;
import tigase.server.ComponentInfo;
import tigase.server.Packet;
import tigase.sys.TigaseRuntime;
import tigase.xmpp.jid.JID;

import java.util.List;
import java.util.logging.Logger;

/**
 * MUCComponent is class implementing clustering support for MUCComponent.
 *
 * @author andrzej
 */
@Bean(name = "muc", parent = Kernel.class, active = true)
@ConfigType(ConfigTypeEnum.DefaultMode)
@ClusterModeRequired(active = true)
public class MUCComponentClustered
		extends MUCComponent
		implements ClusteredComponentIfc, Initializable {

	private static final Logger log = Logger.getLogger(MUCComponentClustered.class.getCanonicalName());

	protected LicenceChecker licenceChecker;
	private ClusterControllerIfc clusterController;
	private ComponentInfo cmpInfo = null;
	@Inject
	private List<CommandListener> commandListeners;
	@Inject
	private StrategyIfc strategy;

	public MUCComponentClustered() {
/*
		 FIXME - restore this check! strategy is configured as a bean inside SM, how to access it?

		 move this to ::register(Kernel) method!

		 Wojtek: currently even in register(Kernel) method this won't work reliably because
		 of the order in which Beans are initialised (it may happen that SM won't be available yet.
		 .getInstance() would create new instance (if doesn't exists, which may not always be needed)
		 let's leave it for now.
*/
//		String strategyProp = System.getProperty( "sm-cluster-strategy-class" );
//		if ( strategyProp == null || !"tigase.server.cluster.strategy.OnlineUsersCachingStrategy".equals( strategyProp) ){
//			log.severe( "You've tried using Clustered version of the component but ACS is disabled" );
//			log.severe( "Shutting down system!" );
//			System.exit( 1 );
//		}
	}

	@Override
	public boolean addOutPacket(Packet packet) {
		return super.addOutPacket(packet);
	}

	@Override
	public void onNodeDisconnected(JID jid) {
		super.onNodeDisconnected(jid);
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
	public void register(Kernel kernel) {
		if (!ServerBeanSelector.getClusterMode(kernel)) {
			TigaseRuntime.getTigaseRuntime()
					.shutdownTigase(new String[]{
							"You've tried using Clustered version of the component but cluster-mode is disabled",
							"Shutting down system!"});
		}
		super.register(kernel);
	}

	@Override
	public void setClusterController(ClusterControllerIfc cl_controller) {
		super.setClusterController(cl_controller);
		if (clusterController != null && commandListeners != null) {
			for (CommandListener cmd : commandListeners) {
				clusterController.removeCommandListener(cmd);
			}
		}
		clusterController = cl_controller;
		if (clusterController != null && commandListeners != null) {
			for (CommandListener cmd : commandListeners) {
				clusterController.setCommandListener(cmd);
			}
		}
		if (strategy != null) {
			strategy.setClusterController(cl_controller);
		}

		kernel.registerBean("clusterController").asInstance(cl_controller).exec();
	}

	public void setCommandListeners(List<CommandListener> commandListeners) {
		if (clusterController != null && this.commandListeners != null) {
			for (CommandListener cmd : commandListeners) {
				clusterController.removeCommandListener(cmd);
			}
		}
		this.commandListeners = commandListeners;
		if (clusterController != null && commandListeners != null) {
			for (CommandListener cmd : commandListeners) {
				clusterController.setCommandListener(cmd);
			}
		}
	}

	public void setStrategy(StrategyIfc strategy) {
		if (this.strategy != null) {
			strategy.setClusterController(null);
		}
		this.strategy = strategy;
		if (this.strategy != null) {
			this.strategy.setClusterController(this.clusterController);
		}
	}

	/**
	 * Allows to obtain various informations about components
	 *
	 * @return information about particular component
	 */
	@Override
	public ComponentInfo getComponentInfo() {
		cmpInfo = super.getComponentInfo();
		cmpInfo.getComponentData().put("MUCClusteringStrategy", (strategy != null) ? strategy.getClass() : null);

		return cmpInfo;
	}

	@Override
	public void initialize() {
		super.initialize();
		licenceChecker = LicenceChecker.getLicenceChecker("acs");
	}

	@Override
	protected void onNodeConnected(JID jid) {
		super.onNodeConnected(jid);
		strategy.nodeConnected(jid);
	}

}
