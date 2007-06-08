/*  tigase-muc
 *  Copyright (C) 2007 by Bartosz M. Małkowski
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software Foundation,
 *  Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 *  $Id$
 */
package tigase.muc;

import java.io.IOException;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import tigase.db.DBInitException;
import tigase.db.UserRepository;
import tigase.db.xml.XMLRepository;
import tigase.muc.xmpp.JID;
import tigase.muc.xmpp.stanzas.IQ;
import tigase.muc.xmpp.stanzas.Message;
import tigase.muc.xmpp.stanzas.Presence;
import tigase.test.junit.JUnitXMLIO;
import tigase.test.junit.XMPPTestCase;
import tigase.xml.Element;

/**
 * 
 * <p>
 * Created: 2007-06-08 10:09:37
 * </p>
 * 
 * @author bmalkow
 * @version $Rev$
 */
public class EmptyRoomTest extends XMPPTestCase {

    private Room room;

    @Before
    public void init() {
        UserRepository repository = new XMLRepository();
        try {
            repository.initRepository("dupa.xml");
        } catch (DBInitException e) {
            e.printStackTrace();
            Assert.fail(e.getMessage());
        }
        this.room = new Room("namespace", null, repository, "darkcave@macbeth.shakespeare.lit", JID
                .fromString("crone1@shakespeare.lit/desktop"), true);
    }

    @Test
    public void test_1() {
        JUnitXMLIO xmlio = new JUnitXMLIO() {
            @Override
            public void write(Element data) throws IOException {
                String name = data.getName();
                if ("presence".equals(name)) {
                    send(room.processInitialStanza((new Presence(data))));
                } else if ("iq".equals(name)) {
                    send(room.processStanza((new IQ(data))));
                } else if ("message".equals(name)) {
                    send(room.processStanza((new Message(data))));
                } else {
                    Assert.fail("Unknown stanza type");
                }
            }
        };
        test("src/test/scripts/processPresence-empty.cor", xmlio);
    }

}
