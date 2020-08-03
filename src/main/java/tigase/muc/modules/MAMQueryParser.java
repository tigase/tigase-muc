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
import tigase.xmpp.rsm.RSM;

@Bean(name = "mamQueryParser", parent = MUCComponent.class, active = true)
public class MAMQueryParser extends tigase.xmpp.mam.MAMQueryParser {

	@Override
	protected void validateRsm(RSM rsm) throws ComponentException {
		// RSM in MUC is not compatible UUID
		//super.validateRsm(rsm);
	}
}
