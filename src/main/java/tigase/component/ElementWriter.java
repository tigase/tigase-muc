package tigase.component;

import java.util.Collection;

import tigase.server.Packet;
import tigase.xml.Element;

public interface ElementWriter {

	void write(Collection<Packet> elements);

	void write(final Packet element);

	void writeElement(Collection<Element> elements);

	void writeElement(final Element element);

}
