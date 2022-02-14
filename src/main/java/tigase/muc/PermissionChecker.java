/*
 * Tigase MUC - Multi User Chat component for Tigase
 * Copyright (C) 2007 Tigase, Inc. (office@tigase.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. Look for COPYING file in the top folder.
 * If not, see http://www.gnu.org/licenses/.
 */

package tigase.muc;

import tigase.component.exceptions.RepositoryException;
import tigase.form.Form;
import tigase.kernel.beans.Bean;
import tigase.kernel.beans.Inject;
import tigase.muc.exceptions.MUCException;
import tigase.muc.repository.IMucRepository;
import tigase.server.BasicComponent;
import tigase.server.CmdAcl;
import tigase.xmpp.Authorization;
import tigase.xmpp.jid.BareJID;
import tigase.xmpp.jid.JID;

import java.util.Optional;

@Bean(name = "permissionChecker", parent = MUCComponent.class, active = true, exportable = true)
public class PermissionChecker {

	@Inject(nullAllowed = true, bean = "service")
	private BasicComponent component;
	@Inject
	private MUCConfig config;

	@Inject
	private IMucRepository mucRepository;

	/**
	 * Checks privileges to create new room.
	 *
	 * @param roomJID JabberID of room to create.
	 * @param senderJid JabberID of creation request sender.
	 * @param roomConfiguration optional room configuration.
	 *
	 * @throws MUCException if privileges are insufficient. Error condition argument is {@linkplain Authorization#FORBIDDEN FORBIDDEN}.
	 */
	public void checkCreatePermission(final BareJID roomJID, final JID senderJid, final Form roomConfiguration)
			throws MUCException, RepositoryException {
		final RoomConfig roomConfig = mucRepository.getDefaultRoomConfig().clone();
		if (roomConfiguration != null) {
			roomConfig.copyFrom(roomConfiguration, false);
		}

		if (roomConfig.isRoomconfigPublicroom() && !checkAcl(roomJID, senderJid, config.getPublicRoomCreationAcl())) {
			throw new MUCException(Authorization.FORBIDDEN, "You don't have enough permissions to create public room");
		} else if (!checkAcl(roomJID, senderJid, config.getHiddenRoomCreationAcl())) {
			throw new MUCException(Authorization.FORBIDDEN, "You don't have enough permissions to create hidden room");
		}

	}

	/**
	 * Checks privileges to update room visibility.
	 *
	 * @param room room to be updated.
	 * @param senderJid JabberID of update request sender.
	 * @param form new configuration form.
	 *
	 * @throws MUCException if privileges are insufficient. Error condition argument is {@linkplain Authorization#FORBIDDEN FORBIDDEN}.
	 */
	public void checkUpdateVisibilityPermission(final Room room, final JID senderJid, final Form form)
			throws MUCException {
		final Boolean willBePublic = form.getAsBoolean(RoomConfig.MUC_ROOMCONFIG_PUBLICROOM_KEY);
		// check permissions only if we are changing visibility
		if (willBePublic != null) {
			if (room.getConfig().isRoomconfigPublicroom() != willBePublic) {
				// we are changing visibility
				// block only making private room public, but allow anyone to convert public room to private
				if (willBePublic && !checkAcl(room.getRoomJID(), senderJid, config.getPublicRoomCreationAcl())) {
					throw new MUCException(Authorization.FORBIDDEN, "You don't have enough permissions to make room public");
				}
			}
		}
	}

	private boolean checkAcl(final BareJID roomJID, final JID senderJid, final CmdAcl.Type type) {
		switch (type) {
			case ALL:
				return true;
			case LOCAL:
				return component.isLocalDomain(senderJid.getDomain());
			case ADMIN:
				return component.isAdmin(senderJid);
			case DOMAIN: {
				String domain = roomJID.getDomain().substring(component.getName().length() + 1);
				return domain.equals(senderJid.getDomain());
			}
			case DOMAIN_ADMIN: {
				String domain = roomJID.getDomain().substring(component.getName().length() + 1);
				return Optional.ofNullable(component.getVHostItem(domain))
						.filter(vhost -> vhost.isAdmin(senderJid.toString()))
						.isPresent() || component.isAdmin(senderJid);
			}
			case DOMAIN_OWNER: {
				String domain = roomJID.getDomain().substring(component.getName().length() + 1);
				return Optional.ofNullable(component.getVHostItem(domain))
						.filter(vhost -> vhost.isOwner(senderJid.toString()))
						.isPresent();
			}
			case JID:
			case NONE:
			default:
				return false;
		}
	}

}
