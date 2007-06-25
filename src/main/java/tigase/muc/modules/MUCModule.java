package tigase.muc.modules;

import java.util.List;

import tigase.muc.RoomsContainer;
import tigase.xml.Element;

public interface MUCModule extends Module {

	List<Element> process(RoomsContainer roomsContainer, Element element);

}
