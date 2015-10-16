/*
 * MUCComponentClustered.java
 *
 * Tigase Jabber/XMPP Server
 * Copyright (C) 2004-2013 "Tigase, Inc." <office@tigase.com>
 *
 */
package tigase.muc.cluster;

import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import tigase.cluster.api.ClusterControllerIfc;
import tigase.cluster.api.ClusteredComponentIfc;
import tigase.conf.ConfigurationException;
import tigase.kernel.core.Kernel;
import tigase.licence.LicenceChecker;
import tigase.muc.MUCComponent;
import tigase.osgi.ModulesManagerImpl;
import tigase.server.ComponentInfo;
import tigase.server.Packet;
import tigase.server.XMPPServer;
import tigase.xmpp.JID;

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
	public Map<String, Object> getDefaults(Map<String, Object> params) {
		Map<String, Object> defaults = super.getDefaults(params);
		defaults.put(STRATEGY_CLASS_KEY, DEF_STRATEGY_CLASS_VAL);
		return defaults;
	}

	@Override
	protected void changeRegisteredBeans(Map<String, Object> props) throws ConfigurationException, InstantiationException,
			IllegalAccessException, IllegalArgumentException, InvocationTargetException {
		super.changeRegisteredBeans(props);
		if (props.containsKey(STRATEGY_CLASS_KEY)) {
			String strategy_class = (String) props.get(STRATEGY_CLASS_KEY);
			try {
				Class<StrategyIfc> sClass = (Class<StrategyIfc>) ModulesManagerImpl.getInstance().forName(strategy_class);
				kernel.registerBean("strategy").asClass(sClass).exec();
			} catch (Exception ex) {
				if (!XMPPServer.isOSGi()) {
					log.log(Level.SEVERE, "Cannot find clustering strategy class: " + strategy_class, ex);
				}
				throw new ConfigurationException("Cannot find clustering strategy class: " + strategy_class);
			}
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
