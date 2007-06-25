package tigase.muc;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import tigase.db.TigaseDBException;
import tigase.db.UserExistsException;
import tigase.db.UserNotFoundException;
import tigase.db.UserRepository;
import tigase.disco.ServiceEntity;
import tigase.disco.ServiceIdentity;
import tigase.muc.xmpp.JID;

public class RoomsContainer {

	private Set<String> allRooms = new HashSet<String>();

	private Logger log = Logger.getLogger(this.getClass().getName());;

	private UserRepository mucRepository;

	private String myDomain;

	/**
	 * Loaded rooms
	 */
	private final Map<String, RoomContext> rooms = new HashMap<String, RoomContext>();

	private ServiceEntity serviceEntity;

	RoomsContainer(String myDomain, UserRepository mucRepository, ServiceEntity serviceEntity) {
		this.mucRepository = mucRepository;
		this.serviceEntity = serviceEntity;
		this.myDomain = myDomain;
	}

	public void addRoom(RoomContext context) {
		this.rooms.put(context.getId(), context);
		this.allRooms.add(context.getId());
	}

	public void configRoomDiscovery(final RoomConfiguration config) {
		ServiceEntity x = new ServiceEntity(config.getId(), config.getId(), config.getRoomconfigRoomname());
		x.addIdentities(new ServiceIdentity("conference", "text", config.getRoomconfigRoomname()));

		x.addFeatures("http://jabber.org/protocol/muc");
		if (config.isRoomconfigPasswordProtectedRoom()) {
			x.addFeatures("muc_passwordprotected");
		} else {
			x.addFeatures("muc_unsecured");
		}
		if (config.isRoomconfigPersistentRoom()) {
			x.addFeatures("muc_persistent");
		} else {
			x.addFeatures("muc_temporary");
		}
		if (config.isRoomconfigMembersOnly()) {
			x.addFeatures("muc_membersonly");
		} else {
			x.addFeatures("muc_open");
		}
		if (config.isRoomconfigModeratedRoom()) {
			x.addFeatures("muc_moderated");
		} else {
			x.addFeatures("muc_unmoderated");
		}
		if (config.affiliationCanViewJid(Affiliation.NONE)) {
			x.addFeatures("muc_nonanonymous");
		} else {
			x.addFeatures("muc_semianonymous");
		}

		if (serviceEntity != null) {
			serviceEntity.addItems(x);
		}

	}

	public void configRoomDiscovery(String jid) {
		try {
			RoomConfiguration config = new RoomConfiguration(myDomain, jid, this.mucRepository);
			configRoomDiscovery(config);
		} catch (Exception e) {
			log.log(Level.SEVERE, "Error on read room " + jid + " configuration", e);
		}
	}

	public void destroyRoom(JID room) {
		this.allRooms.remove(room.toString());
		this.rooms.remove(room.toString());
	}

	public RoomContext getRoomContext(String roomID) {
		return this.rooms.get(roomID);
	}

	public boolean isRoomExists(JID jid) {
		return this.allRooms.contains(jid.toString());
	}

	public void readAllRomms() {
		log.config("Reading rooms...");
		try {
			String[] roomsJid = this.mucRepository.getSubnodes(myDomain);
			allRooms.clear();
			if (roomsJid != null) {
				for (String jid : roomsJid) {
					allRooms.add(jid);
					configRoomDiscovery(jid);
				}
			}
		} catch (UserNotFoundException e) {
			try {
				this.mucRepository.addUser(myDomain);
			} catch (UserExistsException e1) {
				e1.printStackTrace();
			} catch (TigaseDBException e1) {
				e1.printStackTrace();
			}
		} catch (TigaseDBException e) {
			e.printStackTrace();
		}
		log.config(allRooms.size() + " known rooms.");
	}

	public void removeRoom(JID room) {
		this.rooms.remove(room.toString());
	}

}
