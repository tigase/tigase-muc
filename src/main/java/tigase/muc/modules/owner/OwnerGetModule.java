package tigase.muc.modules.owner;

import java.util.LinkedList;
import java.util.List;

import tigase.criteria.Criteria;
import tigase.criteria.ElementCriteria;
import tigase.muc.Affiliation;
import tigase.muc.MucInternalException;
import tigase.muc.RoomContext;
import tigase.muc.modules.AbstractModule;
import tigase.muc.xmpp.stanzas.IQ;
import tigase.xml.Element;

public class OwnerGetModule extends AbstractModule {

    private static final Criteria CRIT = new ElementCriteria("iq", new String[] { "type" }, new String[] { "get" }).add(ElementCriteria.name("query",
            "http://jabber.org/protocol/muc#owner"));

    private static final String XMLNS_MUC_OWNER = "http://jabber.org/protocol/muc#owner";

    @Override
    public Criteria getModuleCriteria() {
        return CRIT;
    }

    @Override
    protected List<Element> intProcess(RoomContext roomContext, Element element) throws MucInternalException {
        IQ iq = new IQ(element);
        if (Affiliation.OWNER != roomContext.getAffiliation(iq.getFrom())) {
            throw new MucInternalException(iq, "forbidden", "403", "auth");
        }
        List<Element> result = new LinkedList<Element>();
        Element query = iq.getChild("query");

        if (query.getChildren() == null || query.getChildren().size() == 0) {
            Element answer = new Element("iq");
            answer.addAttribute("id", iq.getAttribute("id"));
            answer.addAttribute("type", "result");
            answer.addAttribute("to", iq.getAttribute("from"));
            answer.addAttribute("from", roomContext.getId());

            Element answerQuery = new Element("query", new String[] { "xmlns" }, new String[] { "http://jabber.org/protocol/muc#owner" });
            answer.addChild(answerQuery);

            answerQuery.addChild(roomContext.getFormElement().getElement());

            result.add(answer);
        }

        return result;
    }

}
