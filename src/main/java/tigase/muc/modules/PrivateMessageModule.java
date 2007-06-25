package tigase.muc.modules;

import java.util.LinkedList;
import java.util.List;

import tigase.criteria.Criteria;
import tigase.criteria.ElementCriteria;
import tigase.muc.MucInternalException;
import tigase.muc.Role;
import tigase.muc.RoomContext;
import tigase.muc.xmpp.JID;
import tigase.muc.xmpp.stanzas.Message;
import tigase.xml.Element;

public class PrivateMessageModule extends AbstractMessageModule {

	private static final Criteria CRIT = ElementCriteria.name("message");

	@Override
	public Criteria getModuleCriteria() {
		return CRIT;
	}

	@Override
	protected List<Element> intProcess(RoomContext roomContext, Element el) throws MucInternalException {
		Message element = new Message(el);
		List<Element> result = new LinkedList<Element>();
		String senderNick = roomContext.getOccupantsByJID().get(element.getFrom());
		String recipentNick = element.getTo().getResource();

		// broadcast message
		if (roomContext.getRole(JID.fromString(element.getAttribute("from"))) == Role.VISITOR) {
			throw new MucInternalException(element, "not-acceptable", "406", "modify", "Only occupants are allowed to send messages to occupants");
		}

		JID recipentJID = roomContext.getOccupantsByNick().get(recipentNick);

		if (recipentJID == null) {
			throw new MucInternalException(element, "item-not-found", "404", "cancel");
		}

		preProcess(roomContext, element, senderNick);

		Element message = element.clone();
		message.setAttribute("from", roomContext.getId() + "/" + senderNick);
		message.setAttribute("to", recipentJID.toString());
		result.add(message);

		return result;
	}
}
