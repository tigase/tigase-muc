package tigase.muc.modules;

import tigase.criteria.Criteria;
import tigase.criteria.ElementCriteria;
import tigase.muc.MucInternalException;
import tigase.muc.Role;
import tigase.muc.RoomContext;
import tigase.muc.xmpp.stanzas.Message;
import tigase.xml.Element;

public class ChangeSubjectModule extends AbstractMessageModule {

	private static final Criteria CRIT = (new ElementCriteria("message", new String[] { "type" }, new String[] { "groupchat" })).add(ElementCriteria.name("subject"));

	@Override
	public Criteria getModuleCriteria() {
		return CRIT;
	}

	protected void preProcess(RoomContext roomContext, Message element, String senderNick) throws MucInternalException {
		Element subject = element.getChild("subject");

		if (!roomContext.isRoomconfigChangeSubject() && roomContext.getRole(element.getFrom()) != Role.MODERATOR) {
			throw new MucInternalException(element, "forbidden", "403", "auth");
		}
		roomContext.setCurrentSubject(new Message(element.clone()));
		roomContext.getCurrentSubject().setAttribute("from", roomContext.getId() + "/" + senderNick);
	}

}
