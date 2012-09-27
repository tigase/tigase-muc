package tigase.component;

import java.util.Map;

import tigase.xmpp.BareJID;

public abstract class ComponentConfig {

	protected final AbstractComponent<?> component;

	private final BareJID serviceName;

	protected ComponentConfig(AbstractComponent<?> component) {
		this.component = component;
		this.serviceName = BareJID.bareJIDInstanceNS(component.getName());
	}

	public abstract Map<String, Object> getDefaults(Map<String, Object> params);

	public BareJID getServiceName() {
		return serviceName;
	}

	public abstract void setProperties(Map<String, Object> props);

}
