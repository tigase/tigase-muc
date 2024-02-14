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
package tigase.muc.history;

import tigase.component.exceptions.RepositoryException;
import tigase.xml.Element;
import tigase.xmpp.jid.BareJID;
import tigase.xmpp.jid.JID;
import tigase.xmpp.mam.MAMRepository;
import tigase.xmpp.mam.Query;

public interface ExtendedMAMRepository<Q extends Query, I extends ExtendedMAMRepository.Item> extends MAMRepository<Q, I> {

	Item getItem(BareJID owner, String stableId) throws RepositoryException;

	void updateMessage(BareJID owner, String stableId, Element msg, String body) throws RepositoryException;

	interface Item extends MAMRepository.Item {

		JID getSenderJID();

	}

}
