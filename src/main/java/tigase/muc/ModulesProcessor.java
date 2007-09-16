/*
 * Tigase Jabber/XMPP Multi User Chatroom Component
 * Copyright (C) 2007 "Bartosz M. Małkowski" <bartosz.malkowski@tigase.org>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. Look for COPYING file in the top folder.
 * If not, see http://www.gnu.org/licenses/.
 *
 * $Rev$
 * Last modified by $Author$
 * $Date$
 */
package tigase.muc;

import java.util.LinkedList;
import java.util.List;
import java.util.logging.Logger;

import tigase.muc.modules.BroadcastMessageModule;
import tigase.muc.modules.ChangeSubjectModule;
import tigase.muc.modules.InvitationModule;
import tigase.muc.modules.MUCModule;
import tigase.muc.modules.Module;
import tigase.muc.modules.MucVersionModule;
import tigase.muc.modules.RoomModule;
import tigase.muc.modules.PresenceModule;
import tigase.muc.modules.PrivateMessageModule;
import tigase.muc.modules.UniqueRoomNameModule;
import tigase.muc.modules.admin.AdminGetModule;
import tigase.muc.modules.admin.AdminSetModule;
import tigase.muc.modules.owner.OwnerGetModule;
import tigase.muc.modules.owner.OwnerSetModule;
import tigase.xml.Element;

/**
 * 
 * <p>
 * Created: 2007-06-20 19:54:19
 * </p>
 * 
 * @author bmalkow
 * @version $Rev$
 */
public class ModulesProcessor {

	private Logger log = Logger.getLogger(this.getClass().getName());

	private List<Module> modules = new LinkedList<Module>();

	public ModulesProcessor(RoomListener listener) {
		registerModule(new UniqueRoomNameModule());
		registerModule(new MucVersionModule());

		registerModule(new InvitationModule());
		registerModule(new ChangeSubjectModule());
		registerModule(new BroadcastMessageModule());
		registerModule(new PrivateMessageModule());

		registerModule(new PresenceModule(listener));

		registerModule(new AdminGetModule());
		registerModule(new AdminSetModule());

		registerModule(new OwnerGetModule());
		registerModule(new OwnerSetModule(listener));
	}

	public List<Element> processStanza(RoomContext roomContext, RoomsContainer roomsContainer, Element element) {
		for (Module module : modules) {
			if (module.getModuleCriteria().match(element)) {
				List<Element> result = null;
				if (module instanceof RoomModule && roomContext != null) {
					RoomModule roomModule = (RoomModule) module;
					result = roomModule.process(roomContext, element);
				} else if (module instanceof MUCModule && roomsContainer != null) {
					MUCModule mucModule = (MUCModule) module;
					result = mucModule.process(roomsContainer, element);
				}
				if (result != null && result.size() > 0) {
					return result;
				}
			}
		}
		return null;
	}

	public void registerModule(Module module) {
		this.modules.add(module);
		log.config("Registered MUC module: " + module.toString());
	}

}