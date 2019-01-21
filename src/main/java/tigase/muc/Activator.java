/**
 * Tigase MUC - Multi User Chat component for Tigase
 * Copyright (C) 2007 Tigase, Inc. (office@tigase.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. Look for COPYING file in the top folder.
 * If not, see http://www.gnu.org/licenses/.
 */
package tigase.muc;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServiceReference;
import tigase.muc.history.DerbySqlHistoryProvider;
import tigase.muc.history.MemoryHistoryProvider;
import tigase.muc.history.MySqlHistoryProvider;
import tigase.muc.history.NoneHistoryProvider;
import tigase.muc.history.PostgreSqlHistoryProvider;
import tigase.muc.history.SqlserverSqlHistoryProvider;
import tigase.osgi.ModulesManager;

public class Activator implements BundleActivator, ServiceListener {

	private static final Logger log = Logger.getLogger(Activator.class.getCanonicalName());
	private BundleContext context = null;
	private Class<MUCComponent> mucComponentClass = null;
	private List<Class> repositoryClasses = null;
	private ModulesManager serviceManager = null;
	private ServiceReference serviceReference = null;

	private void registerAddons() {
		if (serviceManager != null) {
			serviceManager.registerServerComponentClass(mucComponentClass);
			for (Class repositoryClass : repositoryClasses) {
				serviceManager.registerClass(repositoryClass);
			}
			serviceManager.update();
		}
	}

	@Override
	public void serviceChanged(ServiceEvent event) {
		if (event.getType() == ServiceEvent.REGISTERED) {
			if (serviceReference == null) {
				serviceReference = event.getServiceReference();
				serviceManager = (ModulesManager) context.getService(serviceReference);
				registerAddons();
			}
		} else if (event.getType() == ServiceEvent.UNREGISTERING) {
			if (serviceReference == event.getServiceReference()) {
				unregisterAddons();
				context.ungetService(serviceReference);
				serviceManager = null;
				serviceReference = null;
			}
		}
	}

	@Override
	public void start(BundleContext bc) throws Exception {
		synchronized (this) {
			context = bc;
			mucComponentClass = MUCComponent.class;
			repositoryClasses = new ArrayList<Class>();
			repositoryClasses.add(DerbySqlHistoryProvider.class);
			repositoryClasses.add(MemoryHistoryProvider.class);
			repositoryClasses.add(MySqlHistoryProvider.class);
			repositoryClasses.add(NoneHistoryProvider.class);
			repositoryClasses.add(PostgreSqlHistoryProvider.class);
			repositoryClasses.add(SqlserverSqlHistoryProvider.class);
			bc.addServiceListener(this, "(&(objectClass=" + ModulesManager.class.getName() + "))");
			serviceReference = bc.getServiceReference(ModulesManager.class.getName());
			if (serviceReference != null) {
				serviceManager = (ModulesManager) bc.getService(serviceReference);
				registerAddons();
			}
		}
	}

	@Override
	public void stop(BundleContext bc) throws Exception {
		synchronized (this) {
			if (serviceManager != null) {
				unregisterAddons();
				context.ungetService(serviceReference);
				serviceManager = null;
				serviceReference = null;
			}
			// mucComponent.stop();
			mucComponentClass = null;
			repositoryClasses = null;
		}
	}

	private void unregisterAddons() {
		if (serviceManager != null) {
			serviceManager.unregisterServerComponentClass(mucComponentClass);
			for (Class repositoryClass : repositoryClasses) {
				serviceManager.unregisterClass(repositoryClass);
			}			
			serviceManager.update();
		}
	}
}
