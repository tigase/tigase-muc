package tigase.muc.modules.admin;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import tigase.criteria.Criteria;
import tigase.criteria.ElementCriteria;
import tigase.muc.Affiliation;
import tigase.muc.MucInternalException;
import tigase.muc.Role;
import tigase.muc.RoomContext;
import tigase.muc.modules.AbstractModule;
import tigase.muc.modules.RoomModule;
import tigase.muc.modules.PresenceModule;
import tigase.muc.xmpp.JID;
import tigase.muc.xmpp.stanzas.IQ;
import tigase.muc.xmpp.stanzas.IQType;
import tigase.muc.xmpp.stanzas.Presence;
import tigase.xml.Element;

public class AdminGetModule extends AbstractModule {

    private static final Criteria CRIT = new ElementCriteria("iq", new String[] { "type" }, new String[] { "get" }).add(ElementCriteria.name("query",
            "http://jabber.org/protocol/muc#admin"));

    private static final String XMLNS_MUC_ADMIN = "http://jabber.org/protocol/muc#admin";

    @Override
    public Criteria getModuleCriteria() {
        return CRIT;
    }

    @Override
    protected List<Element> intProcess(RoomContext roomContext, Element element) throws MucInternalException {
        IQ iq = new IQ(element);

        List<Element> result = new LinkedList<Element>();
        Element query = iq.getChild("query", XMLNS_MUC_ADMIN);
        List<Element> items = query.getChildren("/query");

        // Service Informs Admin or Owner of Success
        IQ answer = new IQ(IQType.RESULT);
        answer.setId(iq.getId());
        answer.setTo(iq.getFrom());
        answer.setFrom(JID.fromString(roomContext.getId()));

        Element answerQuery = new Element("query");
        answer.addChild(answerQuery);
        answerQuery.setAttribute("query", XMLNS_MUC_ADMIN);

        Set<JID> occupantsJid = new HashSet<JID>();
        for (Element item : items) {

            try {
                Affiliation reqAffiliation = Affiliation.valueOf(item.getAttribute("affiliation").toUpperCase());
                occupantsJid.addAll(roomContext.findBareJidsByAffiliations(reqAffiliation));
                if (reqAffiliation == Affiliation.NONE) {
                    occupantsJid.addAll(roomContext.findBareJidsWithoutAffiliations());
                }
            } catch (Exception e) {
                ;
            }

            try {
                Role reqRole = Role.valueOf(item.getAttribute("role").toUpperCase());
                occupantsJid.addAll(roomContext.findJidsByRole(reqRole));
            } catch (Exception e) {
                ;
            }

        }

        for (JID jid : occupantsJid) {
            Element answerItem = PresenceModule.preparePresenceSubItem(roomContext, jid, iq.getFrom());
            answerQuery.addChild(answerItem);
        }

        result.add(answer);
        return result;
    }

}
