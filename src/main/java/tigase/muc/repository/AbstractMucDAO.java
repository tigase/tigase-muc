/*
 * AbstractMucDAO.java
 *
 * Tigase Jabber/XMPP Multi-User Chat Component
 * Copyright (C) 2004-2017 "Tigase, Inc." <office@tigase.com>
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
package tigase.muc.repository;

import tigase.db.DataSource;
import tigase.xml.DomBuilderHandler;
import tigase.xml.Element;
import tigase.xml.SimpleParser;
import tigase.xml.SingletonFactory;

import java.util.Queue;

/**
 * Created by andrzej on 14.10.2016.
 */
public abstract class AbstractMucDAO<DS extends DataSource, ID>
		implements IMucDAO<DS, ID> {

	private final SimpleParser parser = SingletonFactory.getParserInstance();

	protected Element parseConfigElement(String cnfData) {
		if (cnfData == null) {
			return null;
		}

		char[] data = cnfData.toCharArray();
		DomBuilderHandler domHandler = new DomBuilderHandler();
		parser.parse(domHandler, data, 0, data.length);

		Queue<Element> q = domHandler.getParsedElements();
		if ((q != null) && (q.size() > 0)) {
			return q.element();
		}

		return null;
	}

}
