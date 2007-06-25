package tigase.muc.modules;

import java.util.LinkedList;
import java.util.List;
import java.util.logging.Logger;

import tigase.muc.MucInternalException;
import tigase.muc.RoomContext;
import tigase.xml.Element;

public abstract class AbstractModule implements RoomModule {

	protected Logger log = Logger.getLogger(this.getClass().getName());

    protected abstract List<Element> intProcess(final RoomContext roomContext, final Element element) throws MucInternalException;

	@Override
	public final List<Element> process(final RoomContext roomContext, final Element element) {
		try {
			return intProcess(roomContext, element);
		} catch (MucInternalException e) {
			List<Element> result = new LinkedList<Element>();
			Element answer = e.makeElement(true);
			answer.setAttribute("from", roomContext.getId());
			return result;
		}

	}
}
