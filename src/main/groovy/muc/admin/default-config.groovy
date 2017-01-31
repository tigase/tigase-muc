/*
 Sets default room configuration.
 AS:Description: Default room config
 AS:CommandId: default-room-config
 AS:Component: muc
 AS:ComponentClass: tigase.muc.MUCComponent
*/

package muc.admin;

import tigase.form.Form;
import tigase.muc.MUCComponent;
import tigase.muc.repository.IMucRepository;
import tigase.server.Command
import tigase.server.Iq;
import tigase.server.Packet

def SUBMIT = "submit";

def mucRepositoryModule = (IMucRepository)mucRepository;
def Iq p = (Iq)packet;
def admins = (Set)adminsSet

def stanzaFromBare = p.getStanzaFrom().getBareJID()
def isServiceAdmin = admins.contains(stanzaFromBare)
if (!isServiceAdmin) {
	def result = p.commandResult(Command.DataType.result)
	Command.addTextField(result, "Error", "You do not have enough permissions to access this data.");
	return result
}

def xform = Command.getData(p, "x", "jabber:x:data");
def submit = xform==null?false:xform.getAttributeStaticStr("type")==SUBMIT;

if (submit) {
	def command = p.getElement().getChild("command", "http://jabber.org/protocol/commands");
	def x = command.getChild("x", "jabber:x:data");

	def frm = new Form(x);
	def df = mucRepositoryModule.getDefaultRoomConfig();

	df.copyFrom(frm);

	mucRepositoryModule.updateDefaultRoomConfig(df)

	return "Default configurtation updated";
} else if (!submit) {
	def res = (Iq)p.commandResult(Command.DataType.form)
	def command = res.getElement().getChild("command", "http://jabber.org/protocol/commands");
	def x = command.getChild("x", "jabber:x:data");
	command.removeChild(x);

	def df = mucRepositoryModule.getDefaultRoomConfig().form;

	def frm = new Form(x);

	for (fld in df.getAllFields()) {
		frm.addField(fld);
	}

	frm.copyValuesFrom(df);

	command.addChild(frm.getElement());

	return res;
}