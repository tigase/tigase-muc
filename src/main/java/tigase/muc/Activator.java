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
