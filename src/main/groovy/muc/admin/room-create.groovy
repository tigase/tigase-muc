package muc.admin
// AS:Description: Create room
// AS:CommandId: room-create
// AS:Component: muc
// AS:ComponentClass: tigase.muc.MUCComponent

import tigase.form.Form
import tigase.muc.Affiliation
import tigase.muc.Room
import tigase.muc.RoomConfig
import tigase.server.Command
import tigase.server.Iq
import tigase.server.Packet
import tigase.util.stringprep.TigaseStringprepException
import tigase.xml.Element
import tigase.xmpp.jid.BareJID

def ROOM_NAME_KEY = "room-name"

def Iq p = (Iq) packet
def roomName = Command.getFieldValue(p, ROOM_NAME_KEY)

if (roomName == null) {
    RoomConfig roomConfig = mucRepository.getDefaultRoomConfig();

    // No data to process, let's ask user to provide
    // a list of words
    def Packet res = p.commandResult(Command.DataType.form)
    Command.addFieldValue(res, ROOM_NAME_KEY, "", "text-single", "Room name")



    Element command = res.getElement().getChild("command")
    Element x = command.getChild("x", "jabber:x:data")

    x.addChildren(roomConfig.getConfigForm().getElement().getChildren())


    return res
}

if (roomName != null) {
    BareJID jid;
    try {
        jid = BareJID.bareJIDInstance(roomName + "@" + p.getStanzaTo().getBareJID().getDomain());
    } catch (TigaseStringprepException e) {
        jid = BareJID.bareJIDInstance(roomName);
    }

    Element command = p.getElement().getChild("command")
    Element x = command.getChild("x", "jabber:x:data")
    Form f = new Form(x)

    def Room room = mucRepository.createNewRoom(jid, p.getStanzaFrom());
    room.addAffiliationByJid(p.getStanzaFrom().getBareJID(), Affiliation.owner);
    room.getConfig().copyFrom(f)
    room.setRoomLocked(false);
    room.getConfig().notifyConfigUpdate(true);

    return "Room " + room.getRoomJID() + " created";
}