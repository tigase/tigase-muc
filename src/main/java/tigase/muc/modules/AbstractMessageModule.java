package tigase.muc.modules;

import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;

import tigase.muc.MucInternalException;
import tigase.muc.Role;
import tigase.muc.RoomContext;
import tigase.muc.xmpp.JID;
import tigase.muc.xmpp.stanzas.Message;
import tigase.xml.Element;

public abstract class AbstractMessageModule extends AbstractModule {

	protected List<Element> broadCastToAll(RoomContext roomContext, Message element, String senderNick) {
		List<Element> result = new LinkedList<Element>();
		for (Entry<String, JID> entry : roomContext.getOccupantsByNick().entrySet()) {
			Element message = element.clone();
			message.setAttribute("from", roomContext.getId() + (senderNick == null ? "" : "/" + senderNick));
			message.setAttribute("to", entry.getValue().toString());
			result.add(message);
		}
		return result;
	}

	protected void preProcess(RoomContext roomContext, Message element, String senderNick) throws MucInternalException {
	}

	@Override
	protected List<Element> intProcess(RoomContext roomContext, Element el) throws MucInternalException {
		Message element = new Message(el);
		List<Element> result = new LinkedList<Element>();
		String senderNick = roomContext.getOccupantsByJID().get(element.getFrom());
		String recipentNick = element.getTo().getResource();

		// broadcast message
		if (roomContext.getRole(JID.fromString(element.getAttribute("from"))) == Role.VISITOR) {
			return result;
		}
		preProcess(roomContext, element, senderNick);

		result.addAll(broadCastToAll(roomContext, element, senderNick));

		return result;
	}
}
