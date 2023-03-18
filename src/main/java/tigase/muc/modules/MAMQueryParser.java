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
package tigase.muc.modules;

import tigase.component.exceptions.ComponentException;
import tigase.kernel.beans.Bean;
import tigase.muc.MUCComponent;
import tigase.server.Packet;
import tigase.xmpp.Authorization;
import tigase.xmpp.mam.Query;
import tigase.xmpp.rsm.RSM;

import java.util.Date;

@Bean(name = "mamQueryParser", parent = MUCComponent.class, active = true)
public class MAMQueryParser extends tigase.xmpp.mam.MAM2QueryParser {

	@Override
	public Query parseQuery(Query query, Packet packet) throws ComponentException {
		Query request = super.parseQuery(query, packet);
		handleOldIds(request);
		return request;
	}

	@Override
	protected void validateRsm(RSM rsm) throws ComponentException {
		// RSM in MUC is were not UUIDs, we need to handle it so, do not validate!
		//super.validateRsm(rsm);
	}

	protected void handleOldIds(Query request) throws ComponentException {
		if (request.getRsm().getBefore() != null && !maybeUUID(request.getRsm().getBefore())) {
			try {
				Date before = new Date(Long.parseLong(request.getRsm().getBefore()));
				if (request.getEnd() == null || request.getEnd().after(before)) {
					request.setEnd(before);
				}
				request.getRsm().setBefore(null);
				request.getRsm().setHasBefore(true);
			} catch (NumberFormatException ex) {
				throw new ComponentException(Authorization.NOT_ACCEPTABLE, "Invalid RSM before value");
			}
		}
		if (request.getRsm().getAfter() != null && !maybeUUID(request.getRsm().getAfter())) {
			try {
				Date after = new Date(Long.parseLong(request.getRsm().getAfter()));
				if (request.getStart() == null || request.getStart().before(after)) {
					request.setStart(after);
				}
				request.getRsm().setAfter(null);
			} catch (NumberFormatException ex) {
				throw new ComponentException(Authorization.NOT_ACCEPTABLE, "Invalid RSM after value");
			}
		}
	}

	private boolean maybeUUID(String name) {
		if (name.charAt(8) != '-') {
			return false;
		}
		if (name.charAt(13) != '-') {
			return false;
		}
		if (name.charAt(18) != '-') {
			return false;
		}
		if (name.charAt(23) != '-') {
			return false;
		}
		return true;
	}
}
