package tigase.muc.modules;

import java.security.SecureRandom;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import tigase.criteria.Criteria;
import tigase.criteria.ElementCriteria;
import tigase.muc.RoomsContainer;
import tigase.muc.xmpp.JID;
import tigase.muc.xmpp.stanzas.IQ;
import tigase.muc.xmpp.stanzas.IQType;
import tigase.xml.Element;

public class UniqueRoomNameModule implements MUCModule {

	private final static String LETTERS_TO_UNIQUE_NAME = "abcdefghijklmnopqrstuvwxyz0123456789";

	private static Random random = new SecureRandom();

	@Override
	public List<Element> process(RoomsContainer roomsContainer, Element element) {
		IQ iq = new IQ(element);

		if (!(iq.getTo().getResource() == null && iq.getTo().getUsername() == null)) {
			return null;
		}

		IQ result = new IQ(IQType.RESULT);
		result.setTo(iq.getFrom());
		result.setFrom(iq.getTo());
		result.setId(iq.getId());

		String id;
		String roomHost = iq.getTo().getBareJID().toString();
		do {
			id = "";
			for (int i = 0; i < 32; i++) {
				id += LETTERS_TO_UNIQUE_NAME.charAt(random.nextInt(LETTERS_TO_UNIQUE_NAME.length()));
			}
		} while (roomsContainer.isRoomExists(JID.fromString((id + "@" + roomHost))));

		Element unique = new Element("unique", id, new String[] { "xmlns" }, new String[] { "http://jabber.org/protocol/muc#unique" });
		result.addChild(unique);
		List<Element> resultS = new LinkedList<Element>();
		resultS.add(result);
		return resultS;
	}

	private static final Criteria CRIT = new ElementCriteria("iq", new String[] { "type", "xmlns" }, new String[] { "get",
			"http://jabber.org/protocol/muc#unique" });

	@Override
	public Criteria getModuleCriteria() {
		return CRIT;
	}

}
