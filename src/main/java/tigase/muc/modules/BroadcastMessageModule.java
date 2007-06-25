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

public class BroadcastMessageModule extends AbstractMessageModule {

	private static final Criteria CRIT = new ElementCriteria("message", new String[] { "type" }, new String[] { "groupchat" });

	@Override
	public Criteria getModuleCriteria() {
		return CRIT;
	}

	protected void preProcess(RoomContext roomContext, Message element, String senderNick) throws MucInternalException {
		roomContext.getConversationHistory().add(element, roomContext.getId() + "/" + senderNick, roomContext.getId());
	}

}
