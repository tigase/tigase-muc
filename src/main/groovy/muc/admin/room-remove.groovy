// AS:Description: Remove room
// AS:CommandId: room-remove
// AS:Component: muc
// AS:ComponentClass: tigase.muc.MUCComponent

import tigase.server.Command
import tigase.server.Packet

def ROOM_NAME_KEY = "room-name"
def REASON_KEY = "reason"
def ALTERNATE_JID_KEY = "alternate-jid"

def Packet p = (Packet)packet
def roomName = Command.getFieldValue(p, ROOM_NAME_KEY)
def reason = Command.getFieldValue(p, REASON_KEY)
def alternateJid = Command.getFieldValue(p, ALTERNATE_JID_KEY)

if (roomName == null) {
	// No data to process, let's ask user to provide
	// a list of words
	def res = (Packet)p.commandResult(Command.DataType.form)
	Command.addFieldValue(res, ROOM_NAME_KEY, "", "text-single", "Room name")
	Command.addFieldValue(res, REASON_KEY, "", "text-single", "Reason")
	Command.addFieldValue(res, ALTERNATE_JID_KEY, "", "jid-single", "Alternate room")
	return res
}

if (roomName != null){
	tigase.xmpp.BareJID jid;
	try{
		jid = tigase.xmpp.BareJID.bareJIDInstance(roomName+"@"+p.getStanzaTo().getBareJID().getDomain());
	}catch(tigase.util.TigaseStringprepException e){
		jid = tigase.xmpp.BareJID.bareJIDInstance(roomName);
	}

	def room=mucRepository.getRoom(jid)
	if(room==null)
		return "Room "+jid+" doesn't exists"
	ownerModule.destroy(room, alternateJid, reason);
	return "Room "+room.getRoomJID()+" removed";
}