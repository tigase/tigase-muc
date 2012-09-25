package tigase.component.modules;

import java.util.ArrayList;
import java.util.Collection;
import java.util.logging.Level;
import java.util.logging.Logger;

import tigase.component.ElementWriter;
import tigase.component.exceptions.ComponentException;
import tigase.criteria.Criteria;
import tigase.server.Packet;
import tigase.util.TigaseStringprepException;

public class ModulesManager {

	private Logger log = Logger.getLogger(this.getClass().getName());

	private final ArrayList<Module> modules = new ArrayList<Module>();

	private final ElementWriter writer;

	public ModulesManager() {
		this.writer = null;
	}

	public ModulesManager(ElementWriter writer) {
		this.writer = writer;
	}

	public Collection<String> getFeatures() {
		final ArrayList<String> features = new ArrayList<String>();
		for (Module m : modules) {
			String[] fs = m.getFeatures();
			if (fs != null) {
				for (String string : fs) {
					features.add(string);
				}
			}
		}
		return features;
	}

	public boolean process(final Packet packet) throws ComponentException, TigaseStringprepException {
		return process(packet, this.writer);
	}

	public boolean process(final Packet packet, final ElementWriter writer) throws ComponentException,
			TigaseStringprepException {
		if (writer == null)
			throw new Error("ElementWriter is null");
		boolean handled = false;
		if (log.isLoggable(Level.FINER)) {
			log.finest("Processing packet: " + packet.toString());
		}

		for (Module module : this.modules) {
			Criteria criteria = module.getModuleCriteria();
			if (criteria != null && criteria.match(packet.getElement())) {
				handled = true;
				if (log.isLoggable(Level.FINER)) {
					log.finer("Handled by module " + module.getClass());
				}
				module.process(packet);
				if (log.isLoggable(Level.FINEST)) {
					log.finest("Finished " + module.getClass());
				}
				break;
			}
		}
		return handled;
	}

	public <T extends Module> T register(final T module) {
		if (log.isLoggable(Level.CONFIG))
			log.config("Register Component module: " + module.getClass().getCanonicalName());
		this.modules.add(module);
		return module;
	}

	public void unregister(final Module module) {
		if (log.isLoggable(Level.CONFIG))
			log.config("Unregister Component module: " + module.getClass().getCanonicalName());
		this.modules.remove(module);
	}

}
