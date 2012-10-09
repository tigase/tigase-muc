package tigase.component;

import java.util.Map;

import tigase.xmpp.BareJID;

public abstract class ComponentConfig {

	protected final AbstractComponent<?> component;

	private BareJID serviceName;

	protected ComponentConfig(AbstractComponent<?> component) {
		this.component = component;
	}

	public abstract Map<String, Object> getDefaults(Map<String, Object> params);

	public BareJID getServiceName() {
		if (serviceName == null) {
			serviceName = BareJID.bareJIDInstanceNS(component.getName());
		}
		return serviceName;
	}

	public abstract void setProperties(Map<String, Object> props);

}
