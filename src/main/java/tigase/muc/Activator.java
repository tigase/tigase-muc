package tigase.muc;

import java.util.logging.Logger;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServiceReference;
import tigase.osgi.ModulesManager;

public class Activator implements BundleActivator, ServiceListener {

        private static final Logger log = Logger.getLogger(Activator.class.getCanonicalName());
        private Class<MUCComponent> mucComponentClass = null;
        private BundleContext context = null;
        private ServiceReference serviceReference = null;
        private ModulesManager serviceManager = null;

        @Override
        public void start(BundleContext bc) throws Exception {
                synchronized (this) {
                        context = bc;
                        mucComponentClass = MUCComponent.class;
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
                        //mucComponent.stop();
                        mucComponentClass = null;
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
                }
                else if (event.getType() == ServiceEvent.UNREGISTERING) {
                        if (serviceReference == event.getServiceReference()) {
                                unregisterAddons();
                                context.ungetService(serviceReference);
                                serviceManager = null;
                                serviceReference = null;
                        }
                }
        }

        private void registerAddons() {
                if (serviceManager != null) {
                        serviceManager.registerServerComponentClass(mucComponentClass);
                        serviceManager.update();
                }
        }

        private void unregisterAddons() {
                if (serviceManager != null) {
                        serviceManager.unregisterServerComponentClass(mucComponentClass);
                        serviceManager.update();
                }
        }
}
