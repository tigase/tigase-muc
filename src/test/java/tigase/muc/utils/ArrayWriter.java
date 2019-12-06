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
package tigase.muc.utils;

import tigase.component.PacketWriter;
import tigase.component.responses.AsyncCallback;
import tigase.server.Packet;
import tigase.xml.Element;

import java.util.ArrayList;
import java.util.Collection;

public final class ArrayWriter
		implements PacketWriter {

	private final ArrayList<Element> elements = new ArrayList<Element>();

	public void clear() {
		elements.clear();
	}

	public ArrayList<Element> getElements() {
		return elements;
	}

	@Override
	public void write(Collection<Packet> elements) {
		for (Packet packet : elements) {
			this.elements.add(packet.getElement());
		}
	}

	@Override
	public void write(Packet element) {
		this.elements.add(element.getElement());
	}

	@Override
	public void write(Packet packet, AsyncCallback callback) {
		write(packet);
	}

}
