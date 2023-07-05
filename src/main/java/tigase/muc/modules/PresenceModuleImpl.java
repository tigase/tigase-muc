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

import tigase.component.exceptions.RepositoryException;
import tigase.criteria.Criteria;
import tigase.criteria.ElementCriteria;
import tigase.eventbus.HandleEvent;
import tigase.kernel.beans.Bean;
import tigase.kernel.beans.Initializable;
import tigase.kernel.beans.Inject;
import tigase.kernel.beans.UnregisterAware;
import tigase.muc.*;
import tigase.muc.PermissionChecker.ROOM_VISIBILITY_PERMISSION;
import tigase.muc.exceptions.MUCException;
import tigase.muc.history.HistoryProvider;
import tigase.muc.logger.MucLogger;
import tigase.muc.repository.IMucRepository;
import tigase.server.Message;
import tigase.server.Packet;
import tigase.util.datetime.TimestampHelper;
import tigase.util.stringprep.TigaseStringprepException;
import tigase.xml.Element;
import tigase.xml.XMLNodeIfc;
import tigase.xmpp.Authorization;
import tigase.xmpp.jid.BareJID;
import tigase.xmpp.jid.JID;

import java.text.ParseException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author bmalkow
 */
@Bean(name = PresenceModuleImpl.ID, parent = MUCComponent.class, active = true)
public class PresenceModuleImpl
		extends AbstractMucModule
		implements PresenceModule, Initializable, UnregisterAware {

	protected static final Logger log = Logger.getLogger(PresenceModule.class.getName());
	private static final Criteria CRIT = ElementCriteria.name("presence");
	private final Set<Criteria> allowedElements = new HashSet<Criteria>();
	@Inject
	private MUCConfig config;
	private TimestampHelper dateTimeFormatter = new TimestampHelper();
	@Inject(nullAllowed = true)
	private Ghostbuster2 ghostbuster;
	@Inject
	private HistoryProvider historyProvider;
	@Inject(nullAllowed = true)
	private MucLogger mucLogger;
	@Inject
	private PermissionChecker permissionChecker;
	@Inject
	private IMucRepository repository;

//	public static void addCodes(PresenceWrapper wrapper, boolean newRoomCreated, final String newNickName) {
//		if (newRoomCreated) {
//			wrapper.addStatusCode(201);
//		}
//		if (newNickName != null) {
//			wrapper.addStatusCode(303);
//
//			for (Element item : wrapper.items) {
//				item.setAttribute("nick", newNickName);
//			}
//		}
//	}

	public PresenceModuleImpl() {
		allowedElements.add(ElementCriteria.name("show"));
		allowedElements.add(ElementCriteria.name("status"));
		allowedElements.add(ElementCriteria.name("priority"));
		allowedElements.add(ElementCriteria.xmlns("http://jabber.org/protocol/caps"));
	}

	@Override
	public void sendPresencesToNewOccupant(Room room, JID senderJID) throws TigaseStringprepException {
		BareJID currentOccupantJid = senderJID.getBareJID();
		Affiliation senderAffiliation = room.getAffiliation(currentOccupantJid).getAffiliation();

		// in filtered room we skip sending occupants list to new occupants
		// witout propper affiliation
		if (room.getConfig().isPresenceFilterEnabled() &&
				!room.getConfig().getPresenceFilteredAffiliations().contains(senderAffiliation)) {
			if (log.isLoggable(Level.FINEST)) {
				log.log(Level.FINEST, "Filtering enabled: " + room.getConfig().isPresenceFilterEnabled() +
						"; new occupant doesn't have propper affiliation -  skip sending occupants list");
			}
			return;
		}

		for (String occupantNickname : room.getOccupantsNicknames(true)) {
			final BareJID occupantJid = room.getOccupantsJidByNickname(occupantNickname);

			if (occupantJid == null) {
				// why the hell occupantJid is null?
				continue;
			}

			// we don't include current user in occupants presence broadcast
			if (currentOccupantJid != null && currentOccupantJid.equals(occupantJid)) {
				continue;
			}

			Affiliation affiliation = room.getAffiliation(occupantJid).getAffiliation();
			if (room.getConfig().isPresenceFilterEnabled() &&
					!room.getConfig().getPresenceFilteredAffiliations().contains(affiliation)) {
				if (log.isLoggable(Level.FINEST)) {
					log.log(Level.FINEST, "Filtering enabled: " + room.getConfig().isPresenceFilterEnabled() +
							"; target occupant doesn't have propper affiliation -  don't include him in the list");
				}
				continue;
			}

			Element op = room.getLastPresenceCopyByJid(occupantJid);

			if (op == null) {
				continue;
			}

			final Collection<JID> occupantJIDs = room.getOccupantsJidsByNickname(occupantNickname);
			final BareJID occupantBareJID = room.getOccupantsJidByNickname(occupantNickname);
			final Affiliation occupantAffiliation = room.getAffiliation(occupantBareJID).getAffiliation();
			final Role occupantRole = room.getRole(occupantNickname);

			if (config.isMultiItemMode()) {
				PresenceWrapper l = PresenceWrapper.preparePresenceW(room, senderJID, op.clone(), occupantBareJID,
																	 occupantJIDs, occupantNickname,
																	 occupantAffiliation, occupantRole);
				write(l.packet);
			} else {
				for (JID jid : occupantJIDs) {
					Collection<JID> z = new ArrayList<>(1);
					z.add(jid);
					PresenceWrapper l = PresenceWrapper.preparePresenceW(room, senderJID, op.clone(), occupantBareJID,
																		 z, occupantNickname, occupantAffiliation,
																		 occupantRole);
					write(l.packet);
				}
			}
		}
	}

	@Override
	public void doQuit(final Room room, final JID senderJID, final Integer... selfStatusCodes)
			throws TigaseStringprepException {
		final String leavingNickname = room.getOccupantsNickname(senderJID);
		if (leavingNickname == null) {
			if (log.isLoggable(Level.FINE)) {
				log.fine("JID " + senderJID + " has no name. It is not occupant of room " + room.getRoomJID());
			}
			return;
		}
		final RoomAffiliation leavingAffiliation = room.getAffiliation(leavingNickname);
		final Role leavingRole = room.getRole(leavingNickname);

		Element presenceElement = new Element("presence");
		presenceElement.setAttribute("type", "unavailable");

		if (log.isLoggable(Level.FINER)) {
			log.finer(
					"Occupant " + senderJID + " known as " + leavingNickname + " is leaving room " + room.getRoomJID());
		}

		Collection<JID> occupantJIDs = new ArrayList<JID>(room.getOccupantsJidsByNickname(leavingNickname));
		boolean nicknameGone = room.removeOccupant(senderJID);
		ghostbuster.remove(senderJID, room);

		room.updatePresenceByJid(senderJID, leavingNickname, null);

		if (config.isMultiItemMode()) {
			final PresenceWrapper selfPresence = PresenceWrapper.preparePresenceW(room, senderJID, presenceElement,
																				  senderJID.getBareJID(), occupantJIDs,
																				  leavingNickname,
																				  leavingAffiliation.getAffiliation(),
																				  Role.none);
			if (selfStatusCodes != null) {
				for (Integer statusCode : selfStatusCodes) {
					selfPresence.addStatusCode(statusCode);
				}
			}
			write(selfPresence.packet);
		} else {
			Collection<JID> z = new ArrayList<JID>(1);
			z.add(senderJID);

			final PresenceWrapper selfPresence = PresenceWrapper.preparePresenceW(room, senderJID, presenceElement,
																				  senderJID.getBareJID(), z,
																				  leavingNickname,
																				  leavingAffiliation.getAffiliation(),
																				  Role.none);
			if (selfStatusCodes != null) {
				for (Integer statusCode : selfStatusCodes) {
					selfPresence.addStatusCode(statusCode);
				}
			}
			write(selfPresence.packet);
		}

		// TODO if highest priority is gone, then send current highest priority
		// to occupants
		if (nicknameGone) {
			for (String occupantNickname : room.getOccupantsNicknames(false)) {
				for (JID occupantJid : room.getOccupantsJidsByNickname(occupantNickname)) {

					presenceElement = room.getLastPresenceCopy(senderJID.getBareJID(), leavingNickname);
					if (presenceElement == null) {
						presenceElement = new Element("presence", new String[]{"type"}, new String[]{"unavailable"});
					}

					PresenceWrapper presence = PresenceWrapper.preparePresenceW(room, occupantJid, presenceElement,
																				senderJID.getBareJID(), occupantJIDs,
																				leavingNickname,
																				leavingAffiliation.getAffiliation(),
																				room.getRole(leavingNickname));

					write(presence.packet);
				}
			}
			if (room.getConfig().isLoggingEnabled()) {
				addLeaveToHistory(room, new Date(), senderJID, leavingNickname);
			}
		} else {
			occupantJIDs = new ArrayList<JID>(room.getOccupantsJidsByNickname(leavingNickname));

			Role role = room.getRole(leavingNickname);
			Element pe = room.getLastPresenceCopy(senderJID.getBareJID(), leavingNickname);
			if (pe == null) {
				pe = new Element("presence", new String[]{"type"}, new String[]{"unavailable"});
			}
			for (String occupantNickname : room.getOccupantsNicknames(false)) {
				for (JID occupantJid : room.getOccupantsJidsByNickname(occupantNickname)) {
					if (config.isMultiItemMode()) {
						PresenceWrapper presence = PresenceWrapper.preparePresenceW(room, occupantJid, pe.clone(),
																					senderJID.getBareJID(),
																					occupantJIDs, leavingNickname,
																					leavingAffiliation.getAffiliation(),
																					role);
						write(presence.packet);
					} else {
						for (JID jid : occupantJIDs) {
							Collection<JID> z = new ArrayList<JID>(1);
							z.add(jid);
							PresenceWrapper presence = PresenceWrapper.preparePresenceW(room, occupantJid, pe.clone(),
																						senderJID.getBareJID(), z,
																						leavingNickname,
																						leavingAffiliation.getAffiliation(),
																						role);
							write(presence.packet);
						}
					}
				}
			}
		}

		if (!leavingAffiliation.isPersistentOccupant()) {
			Element event = new Element("RoomLeave", new String[]{"xmlns"}, new String[]{"tigase:events:muc"});
			event.addChild(new Element("room", room.getRoomJID().toString()));
			event.addChild(new Element("nickname", leavingNickname));
			event.addChild(new Element("jid", senderJID.toString()));
			fireEvent(event);
		}

		if (room.getOccupantsCount() == 0) {
			if (!room.getConfig().isPersistentRoom()) {
				if ((historyProvider != null)) {
					if (log.isLoggable(Level.FINE)) {
						log.fine("Removing history of room " + room.getRoomJID());
					}
					historyProvider.removeHistory(room);
				} else if (log.isLoggable(Level.FINE)) {
					log.fine("Cannot remove history of room " + room.getRoomJID() +
									 " because history provider is not available.");
				}
			} else if (log.isLoggable(Level.FINE)) {
				log.fine("Room persistent. History will not be removed.");
			}
			repository.leaveRoom(room);

			Element emptyRoomEvent = new Element("EmptyRoom", new String[]{"xmlns"}, new String[]{"tigase:events:muc"});
			emptyRoomEvent.addChild(new Element("room", room.getRoomJID().toString()));
			fireEvent(emptyRoomEvent);
		}
	}

	@Override
	public String[] getFeatures() {
		return null;
	}

	@Override
	public Criteria getModuleCriteria() {
		return CRIT;
	}

	@Override
	public void process(Packet element) throws MUCException, TigaseStringprepException {
		final JID senderJID = JID.jidInstance(element.getAttributeStaticStr(Packet.FROM_ATT));
		final BareJID roomJID = BareJID.bareJIDInstance(element.getAttributeStaticStr(Packet.TO_ATT));
		final String nickName = getNicknameFromJid(JID.jidInstance(element.getAttributeStaticStr(Packet.TO_ATT)));
		final String presenceType = element.getAttributeStaticStr(Packet.TYPE_ATT);

		// final String id = element.getAttribute("id");
		if ((presenceType != null) && "error".equals(presenceType)) {
			if (log.isLoggable(Level.FINER)) {
				log.finer("Ignoring presence with type='" + presenceType + "' from " + senderJID);
			}

			return;
		}
		if (nickName == null) {
			throw new MUCException(Authorization.JID_MALFORMED);
		}
		try {
			Room room = repository.getRoom(roomJID);

			if ((presenceType != null) && "unavailable".equals(presenceType)) {
				processExit(room, element.getElement(), senderJID);

				return;
			}

			final boolean groupchat10 = element.getElement().getChild("x", "http://jabber.org/protocol/muc") == null;

			final String knownNickname;
			final boolean roomCreated;

			if (room == null) {

				if (groupchat10) {
					sendUnavailableResponseForGroupchat10(element);
					return;
				}

				if (log.isLoggable(Level.FINEST)) {
					log.finest(
							"Creating new room '" + roomJID + "' by user " + nickName + "' <" + senderJID.toString() +
									">");
				}

				final ROOM_VISIBILITY_PERMISSION createRoomPermission = permissionChecker.getCreateRoomPermission(
						roomJID, senderJID);

				room = repository.createNewRoom(roomJID, senderJID);
				room.addAffiliationByJid(senderJID.getBareJID(), RoomAffiliation.owner);
				room.setRoomLocked(config.isNewRoomLocked());
				roomCreated = true;
				knownNickname = null;

				switch (createRoomPermission) {
					case PUBLIC:
						room.getConfig().setValue(RoomConfig.MUC_ROOMCONFIG_PUBLICROOM_KEY, true);
						room.getConfig().notifyConfigUpdate(Set.of(RoomConfig.MUC_ROOMCONFIG_PUBLICROOM_KEY));
						break;
					case HIDDEN:
						room.getConfig().setValue(RoomConfig.MUC_ROOMCONFIG_PUBLICROOM_KEY, false);
						room.getConfig().notifyConfigUpdate(Set.of(RoomConfig.MUC_ROOMCONFIG_PUBLICROOM_KEY));
						break;
				}

				room.getConfig().notifyConfigUpdate(true);
				room.setNewSubject(null, nickName);
			} else {
				roomCreated = false;
				knownNickname = room.getOccupantsNickname(senderJID);
			}

			if ((knownNickname != null) && !knownNickname.equals(nickName)) {
				processChangeNickname(room, element.getElement(), senderJID, knownNickname, nickName);
			} else if (!groupchat10 || (knownNickname == null)) {
				if (groupchat10) {
					sendUnavailableResponseForGroupchat10(element);
					return;
				}
				processEntering(room, roomCreated, element.getElement(), senderJID, nickName);
			} else if (knownNickname.equals(nickName)) {
				processChangeAvailabilityStatus(room, element.getElement(), senderJID, knownNickname);
			}
		} catch (MUCException e) {
			throw e;
		} catch (TigaseStringprepException e) {
			throw e;
		} catch (RepositoryException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void initialize() {
		eventBus.registerAll(this);
	}

	@Override
	public void beforeUnregister() {
		eventBus.unregisterAll(this);
	}

	@HandleEvent()
	void handleAffiliationChangedEvent(final AffiliationChangedEvent event) throws Exception {
		if (event.getRoom().isOccupantOnline(event.getJid())) {
			return;
		}

		if (event.getOldAffiliation().isPersistentOccupant() && !event.getNewAffiliation().isPersistentOccupant()) {
			// exit
			JID sender = JID.jidInstanceNS(event.getJid());
			sendPresenceToAllOccupants(event.getRoom(), sender, false, null);
		} else if (!event.getOldAffiliation().isPersistentOccupant() &&
				event.getNewAffiliation().isPersistentOccupant()) {
			// enter
			JID sender = JID.jidInstanceNS(event.getJid());
			sendPresenceToAllOccupants(event.getRoom(), sender, false, null);
		}
	}

	protected Element clonePresence(Element element) {
		Element presence = new Element(element);
		if (config.isPresenceFilterEnabled()) {
			List<Element> cc = element.getChildren();

			if (cc != null) {
				@SuppressWarnings("rawtypes") List<XMLNodeIfc> children = new ArrayList<XMLNodeIfc>();

				for (Element c : cc) {
					for (Criteria crit : allowedElements) {
						if (crit.match(c)) {
							children.add(c);

							break;
						}
					}
				}
				presence.setChildren(children);
			}
		}

		Element toRemove = presence.getChild("x", "http://jabber.org/protocol/muc");

		if (toRemove != null) {
			presence.removeChild(toRemove);
		}

		return presence;
	}

	protected PresenceWrapper preparePresence(JID destinationJID, final Element presence, Room room, JID occupantJID,
											  boolean newRoomCreated) throws TigaseStringprepException {
		final PresenceWrapper wrapper = PresenceWrapper.preparePresenceW(room, destinationJID, presence, occupantJID);

		return wrapper;
	}

	protected void processChangeAvailabilityStatus(final Room room, final Element presenceElement, final JID senderJID,
												   final String nickname) throws TigaseStringprepException {
		if (log.isLoggable(Level.FINEST)) {
			log.finest("Processing stanza " + presenceElement.toString());
		}

		// we only update presence if the room is not filtered or user is on the list of desired affiliations
		if (!room.getConfig().isPresenceFilterEnabled() || (room.getConfig().isPresenceFilterEnabled() &&
				!room.getConfig().getPresenceFilteredAffiliations().isEmpty() && room.getConfig()
				.getPresenceFilteredAffiliations()
				.contains(room.getAffiliation(senderJID.getBareJID()).getAffiliation()))) {
			room.updatePresenceByJid(null, nickname, clonePresence(presenceElement));
		}

		Element pe = room.getLastPresenceCopyByJid(senderJID.getBareJID());

		sendPresenceToAllOccupants(pe, room, senderJID, false, null);
	}

	protected void processChangeNickname(final Room room, final Element element, final JID senderJID,
										 final String senderNickname, final String newNickName)
			throws TigaseStringprepException, MUCException {
		if (log.isLoggable(Level.FINEST)) {
			log.finest("Processing stanza " + element.toString());
		}

		throw new MUCException(Authorization.FEATURE_NOT_IMPLEMENTED, "Changing nickname is not supported yet.");

		// TODO Example 23. Service Denies Room Join Because Roomnicks Are
		// Locked Down (???)
	}

	protected void processEntering(final Room room, final boolean roomCreated, final Element element,
								   final JID senderJID, final String nickname)
			throws MUCException, TigaseStringprepException {
		if (log.isLoggable(Level.FINEST)) {
			log.finest("Processing stanza " + element.toString());
		}

		RoomAffiliation affiliation = room.getAffiliation(senderJID.getBareJID());
		validateRTBL(senderJID.getBareJID(), affiliation.getAffiliation());

		final Element xElement = element.getChild("x", "http://jabber.org/protocol/muc");
		final Element password = (xElement == null) ? null : xElement.getChild("password");

		if (room.getConfig().isPasswordProtectedRoom()) {
			final String psw = (password == null) ? null : password.getCData();
			final String roomPassword = room.getConfig().getPassword();

			if ((psw == null) || !psw.equals(roomPassword)) {

				// Service Denies Access Because No Password Provided
				if (log.isLoggable(Level.FINEST)) {
					log.finest("Password '" + psw + "' is not match to room password '" + roomPassword + "' ");
				}

				throw new MUCException(Authorization.NOT_AUTHORIZED);
			}
		}
		if (room.isRoomLocked() && (affiliation.getAffiliation() != Affiliation.owner)) {

			// Service Denies Access Because Room Does Not (Yet) Exist
			throw new MUCException(Authorization.ITEM_NOT_FOUND, null, "Room exists but is locked");
		}
		if (!affiliation.getAffiliation().isEnterOpenRoom()) {

			// Service Denies Access Because User is Banned
			if (log.isLoggable(Level.FINEST)) {
				log.finest("User " + nickname + "' <" + senderJID.toString() + "> is on rooms '" + room.getRoomJID() +
								   "' blacklist");
			}

			throw new MUCException(Authorization.FORBIDDEN);
		} else if (room.getConfig().isRoomMembersOnly() && !affiliation.getAffiliation().isEnterMembersOnlyRoom()) {

			// Service Denies Access Because User Is Not on Member List
			if (log.isLoggable(Level.FINEST)) {
				log.finest(
						"User " + nickname + "' <" + senderJID.toString() + "> is NOT on rooms '" + room.getRoomJID() +
								"' member list.");
			}

			throw new MUCException(Authorization.REGISTRATION_REQUIRED);
		}

		if (room.getOccupantsJidsByNickname(nickname)
				.stream()
				.map(JID::getBareJID)
				.filter(current -> !current.equals(senderJID.getBareJID()))
				.findAny()
				.isPresent()) {

			throw new MUCException(Authorization.NOT_ALLOWED, "Nickname already in use.");
		}
		// should we ban users from joining under different nickname if user is persistent?
		// I'm not convinced that we need that for now
//		if (affiliation.isPersistentOccupant() && !senderJID.getBareJID().equals(nickname)) {
//			throw new MUCException(Authorization.NOT_ACCEPTABLE, "User " + senderJID.toString() + " needs to join room using nickname " + senderJID.getBareJID().toString());
//		}

		final BareJID currentOccupantJid = room.getOccupantsJidByNickname(nickname);

		if ((currentOccupantJid != null) &&
				(!config.isMultiItemMode() || !currentOccupantJid.equals(senderJID.getBareJID()))) {

			// Service Denies Access Because of Nick Conflict
			throw new MUCException(Authorization.CONFLICT);
		}

		final Integer roomMaxUsers = room.getConfig().getMaxUsers();
		if (roomMaxUsers != null && currentOccupantJid == null && room.getOccupantsCount() >= roomMaxUsers) {
			log.finest(
					"User " + nickname + "' <" + senderJID.toString() + "> cannot join to room '" + room.getRoomJID() +
							"' because maximum number of occupants is reached.");
			throw new MUCException(Authorization.SERVICE_UNAVAILABLE, "Reached maximum number of occupants.");
		}

		final Integer roomMaxResources = room.getConfig().getMaxUserResources();
		if (roomMaxResources != null && room.getOccupantsJidsByNickname(nickname).size() >= roomMaxResources) {
			log.finest(
					"User " + nickname + "' <" + senderJID.toString() + "> cannot join to room '" + room.getRoomJID() +
							"' because maximum number of the same occupant resources is reached.");
			throw new MUCException(Authorization.SERVICE_UNAVAILABLE, "Reached maximum number of occupant resources");
		}

		// we need to have those values ready before we will be able to join to the room as parsing those may throw exceptions
		Integer maxchars = null;
		Integer maxstanzas = null;
		Integer seconds = null;
		Date since = null;
		Element hist = (xElement == null) ? null : xElement.getChild("history");

		if (hist != null) {
			maxchars = attrToInteger(hist, "maxchars", null);
			maxstanzas = attrToInteger(hist, "maxstanzas", null);
			seconds = attrToInteger(hist, "seconds", null);
			try {
				since = dateTimeFormatter.parseTimestamp(hist.getAttributeStaticStr("since"));
			} catch (ParseException ex) {
				throw new MUCException(Authorization.BAD_REQUEST, "Invalid value for attribute since");
			}
		}

		if ((!affiliation.isPersistentOccupant()) && this.config.isAutomaticallyPersistOccupantOnJoin()) {
			affiliation = RoomAffiliation.from(affiliation.getAffiliation() == Affiliation.none
											   ? Affiliation.member
											   : affiliation.getAffiliation(), true,
											   affiliation.getRegisteredNickname());
			try {
				room.addAffiliationByJid(senderJID.getBareJID(), affiliation);
			} catch (RepositoryException ex) {
				throw new MUCException(Authorization.INTERNAL_SERVER_ERROR);
			}
		}

		// TODO Service Informs User that Room Occupant Limit Has Been Reached
		// Service Sends Presence from Existing Occupants to New Occupant
		sendPresencesToNewOccupant(room, senderJID);
		sendPresenceFromRoomToNewOccupant(room, senderJID);

		final Role newRole = Room.getDefaultRole(room.getConfig(), affiliation.getAffiliation());

		if (log.isLoggable(Level.FINEST)) {
			log.finest(
					"Occupant '" + nickname + "' <" + senderJID.toString() + "> is entering room " + room.getRoomJID() +
							" as role=" + newRole.name() + ", affiliation=" + affiliation.getAffiliation().name());
		}

		Element pe = clonePresence(element);
		room.addOccupantByJid(senderJID, nickname, newRole, pe);

		if (ghostbuster != null) {
			ghostbuster.add(senderJID, room);
		}

		// if (currentOccupantJid == null) {

		// Service Sends New Occupant's Presence to All Occupants
		// Service Sends New Occupant's Presence to New Occupant
		sendPresenceToAllOccupants(room, senderJID, roomCreated, null);
		// }

		Element event = new Element("RoomJoin", new String[]{"xmlns"}, new String[]{"tigase:events:muc"});
		event.addChild(new Element("room", room.getRoomJID().toString()));
		event.addChild(new Element("nickname", nickname));
		event.addChild(new Element("jid", senderJID.toString()));
		fireEvent(event);

		sendHistoryToUser(room, senderJID, maxchars, maxstanzas, seconds, since);
		sendSubject(room, senderJID);
		if (room.isRoomLocked() && config.isWelcomeMessagesEnabled() && room.getConfig().isWelcomeMessageEnabled()) {
			sendMucMessage(room, room.getOccupantsNickname(senderJID), "Room is locked. Please configure.");
		}
		if (roomCreated) {
			fireEvent(RoomConfigurationModule.createRoomCreatedEvent(room));

			if (config.isWelcomeMessagesEnabled() && room.getConfig().isWelcomeMessageEnabled()) {
				StringBuilder sb = new StringBuilder();
				sb.append("Welcome! You created new Multi User Chat Room.");
				if (room.isRoomLocked()) {
					sb.append(" Room is locked now. Configure it please!");
				} else if (config.isNewRoomLocked()) {
					sb.append(" Room is unlocked and ready for occupants!");
				}
				sb.append("\n");
				if (room.getConfig().isRoomconfigPublicroom()) {
					sb.append(" You've created new public room");
				} else {
					sb.append(" You've created new hidden room. It's not visible in service discovery and you have to invite participants");
				}
				sendMucMessage(room, room.getOccupantsNickname(senderJID), sb.toString());
			}
		}
		if (room.getConfig().isLoggingEnabled()) {
			addJoinToHistory(room, new Date(), senderJID, nickname);
		}

		if (log.isLoggable(Level.FINEST)) {
			log.finest(room.getDebugInfoOccupants());
		}

	}

	protected void processExit(final Room room, final Element presenceElement, final JID senderJID)
			throws MUCException, TigaseStringprepException {
		if (log.isLoggable(Level.FINEST)) {
			log.finest("Processing stanza " + presenceElement.toString());
		}
		if (room == null) {
			throw new MUCException(Authorization.ITEM_NOT_FOUND, "Unknown room");
		}

		final String leavingNickname = room.getOccupantsNickname(senderJID);

		if (leavingNickname == null) {
			if (log.isLoggable(Level.FINE)) {
				log.fine("JID " + senderJID + " has no name. It is not occupant of room " + room.getRoomJID());
			}
			return;
		}
		doQuit(room, senderJID);
	}

	protected void sendPresenceToAllOccupants(final Element $presence, Room room, JID senderJID, boolean newRoomCreated,
											  final String newNickName) throws TigaseStringprepException {

		final String occupantNickname = room.getOccupantsNickname(senderJID);
		final BareJID occupantJID = Optional.ofNullable(room.getOccupantsJidByNickname(occupantNickname))
				.orElse(senderJID.getBareJID());
		final Affiliation occupantAffiliation = room.getAffiliation(occupantJID).getAffiliation();
		final Role occupantRole = room.getRole(occupantNickname);

		Collection<String> occupantsNicknames;

		if (room.getConfig().isPresenceFilterEnabled()) {
			if (room.getConfig().getPresenceFilteredAffiliations().contains(occupantAffiliation)) {
				// we only want users with propper affiliation
				occupantsNicknames = room.getPresenceFiltered().getOccupantsPresenceFilteredNicknames();
			} else {
				// only send presence back to user that joined
				occupantsNicknames = Arrays.asList(occupantNickname);
			}
		} else {
			// no filtering, send presence to all users
			occupantsNicknames = room.getOccupantsNicknames(false);
		}

		if (log.isLoggable(Level.FINEST)) {
			log.log(Level.FINEST, "Sending presence to all occupants, filtering enabled: " +
					room.getConfig().isPresenceFilterEnabled() + ", occupantsNicknames: " +
					Arrays.asList(occupantsNicknames));
		}

		for (String destinationNickname : occupantsNicknames) {
			for (JID destinationJID : room.getOccupantsJidsByNickname(destinationNickname)) {

				if (config.isMultiItemMode()) {
					PresenceWrapper presence = preparePresence(destinationJID, $presence.clone(), room, senderJID,
															   newRoomCreated);
					if (newRoomCreated) {
						presence.addStatusCode(StatusCodes.NEW_ROOM);
					}
					write(presence.packet);
				} else {
					for (JID jid : room.getOccupantsJidsByNickname(occupantNickname)) {
						Collection<JID> z = new ArrayList<JID>(1);
						z.add(jid);
						PresenceWrapper l = PresenceWrapper.preparePresenceW(room, destinationJID, $presence.clone(),
																			 occupantJID, z, occupantNickname,
																			 occupantAffiliation, occupantRole);
						if (newRoomCreated) {
							l.addStatusCode(StatusCodes.NEW_ROOM);
						}

						write(l.packet);
					}
				}
			}
		}
	}

	protected Element sendPresenceToAllOccupants(Room room, JID senderJID, boolean newRoomCreated,
												 final String newNickName) throws TigaseStringprepException {
		Element presence;

		if (newNickName != null) {
			presence = new Element("presence");
			presence.setAttribute("type", "unavailable");
		} else if (room.getOccupantsNickname(senderJID) == null) {
			presence = new Element("presence");
			presence.setAttribute("type", "unavailable");
		} else {
			presence = room.getLastPresenceCopyByJid(senderJID.getBareJID());
		}
		if (presence != null) {
			sendPresenceToAllOccupants(presence, room, senderJID, newRoomCreated, newNickName);
		}
		return presence;
	}

	private void sendUnavailableResponseForGroupchat10(Packet packet) throws TigaseStringprepException {
		Element presence = new Element("presence");
		presence.setAttribute("to", packet.getStanzaFrom().toString());
		presence.setAttribute("from", packet.getStanzaTo().toString());
		presence.setAttribute("type", "unavailable");

		Element x = new Element("x", new String[]{"xmlns"}, new String[]{"http://jabber.org/protocol/muc#user"});
		x.addChild(new Element("status", new String[]{"code"}, new String[]{StatusCodes.SELF_PRESENCE.toString()}));
		x.addChild(new Element("status", new String[]{"code"}, new String[]{StatusCodes.KICKED.toString()}));
		x.addChild(new Element("status", new String[]{"code"}, new String[]{StatusCodes.REMOVED_FROM_ROOM.toString()}));

		Element item = new Element("item", new String[]{"affiliation", "role"}, new String[]{"none", "none"});
		item.addChild(new Element("reason", "You are not in the room."));
		x.addChild(item);

		presence.addChild(x);
		write(Packet.packetInstance(presence));

	}

	/**
	 * Implementation based on https://xmpp.org/extensions/xep-0045.html#enter-subject
	 *
	 * After the room has optionally sent the discussion history to the new occupant, it SHALL send the current room
	 * subject. This is a <message/> stanza from the room JID (or from the occupant JID of the entity that set
	 * the subject), with a <subject/> element but no <body/> element, as shown in the following example. In addition,
	 * the subject SHOULD be stamped with Delayed Delivery (XEP-0203) [14] information qualified by
	 * the 'urn:xmpp:delay' namespace to indicate the time at which the subject was last modified.
	 * If the <delay/> element is included, its 'from' attribute MUST be set to the JID of the room itself.
	 *
	 * If there is no subject set, the room MUST return an empty <subject/> element. The <delay/> SHOULD be included
	 * if the subject was actively cleared and MAY be omitted if the room never had a subject set.
	 */
	private void sendSubject(Room room, JID senderJID) throws TigaseStringprepException {
		String nick = room.getSubjectChangerNick() != null
					  ? room.getSubjectChangerNick()
					  : room.getOccupantsNicknames(room.getCreatorJid()).stream().findAny().orElse("");

		final Date subjectChangeDate = room.getSubjectChangeDate() != null ? room.getSubjectChangeDate() : new Date();
		String subjectId = GroupchatMessageModule.generateSubjectId(subjectChangeDate,
																	room.getSubject() == null ? "" : room.getSubject());
		Element message = new Element(Message.ELEM_NAME,
									  new String[]{Packet.TYPE_ATT, Packet.FROM_ATT, Packet.TO_ATT, Packet.ID_ATT},
									  new String[]{"groupchat", room.getRoomJID() + "/" + nick, senderJID.toString(),
												   subjectId});

		message.addChild(new Element("subject", room.getSubject()));

		if ((room.getSubjectChangerNick() != null) && (room.getSubjectChangeDate() != null)) {
			String stamp = dateTimeFormatter.formatWithMs(subjectChangeDate);
			Element delay = new Element("delay", new String[]{"xmlns", "stamp"}, new String[]{"urn:xmpp:delay", stamp});

			delay.setAttribute("jid", String.valueOf(room.getRoomJID()));

			message.addChild(delay);

			if (config.useLegacyDelayedDelivery()) {
				Element x = new Element("x", new String[]{"xmlns", "stamp"}, new String[]{"jabber:x:delay",
																						  dateTimeFormatter.formatInLegacyDelayedDelivery(
																								  subjectChangeDate)});
				message.addChild(x);
			}
		}

		Packet p = Packet.packetInstance(message);
		p.setXMLNS(Packet.CLIENT_XMLNS);

		write(p);
	}

	private void sendPresenceFromRoomToNewOccupant(Room room, JID occupandJid) throws TigaseStringprepException {
		if (room.getAvatarHash() != null) {
			Element presence = new Element("presence");
			presence.setAttribute("to", occupandJid.toString());
			presence.setAttribute("from", room.getRoomJID().toString());

			Element photoX = new Element("x", new String[]{"xmlns"}, new String[]{"vcard-temp:x:update"});
			photoX.addChild(new Element("photo", room.getAvatarHash()));
			presence.addChild(photoX);

			write(Packet.packetInstance(presence));
		}
	}

	private Integer toInteger(String v, Integer defaultValue) throws NumberFormatException {
		if (v == null) {
			return defaultValue;
		}
		return Integer.parseInt(v);
	}

	private Integer attrToInteger(Element elem, String attr, Integer defaultValue) throws MUCException {
		try {
			return toInteger(elem.getAttributeStaticStr(attr), defaultValue);
		} catch (NumberFormatException ex) {
			throw new MUCException(Authorization.BAD_REQUEST, "Invalid value for attribute " + attr);
		}
	}

	private void addJoinToHistory(Room room, Date date, JID senderJID, String nickName) {
		if (historyProvider != null) {
			historyProvider.addJoinEvent(room, date, senderJID, nickName);
		}
		if ((mucLogger != null) && room.getConfig().isLoggingEnabled()) {
			mucLogger.addJoinEvent(room, date, senderJID, nickName);
		}
	}

	private void addLeaveToHistory(Room room, Date date, JID senderJID, String nickName) {
		if (historyProvider != null) {
			historyProvider.addLeaveEvent(room, date, senderJID, nickName);
		}
		if ((mucLogger != null) && room.getConfig().isLoggingEnabled()) {
			mucLogger.addLeaveEvent(room, date, senderJID, nickName);
		}
	}

	private void sendHistoryToUser(final Room room, final JID senderJID, final Integer maxchars,
								   final Integer maxstanzas, final Integer seconds, final Date since) {
		if (log.isLoggable(Level.FINEST)) {
			log.finest("Sending history to user using: " + historyProvider + " history provider");
		}

		if (historyProvider != null) {
			historyProvider.getHistoryMessages(room, senderJID, maxchars, maxstanzas, seconds, since, writer);
		}
	}

	public static class DelayDeliveryThread
			extends Thread {

		private final LinkedList<Element[]> items = new LinkedList<Element[]>();
		private final DelDeliverySend sender;

		public DelayDeliveryThread(DelDeliverySend component) {
			this.sender = component;
		}

		public void put(Collection<Element> elements) {
			if ((elements != null) && (elements.size() > 0)) {
				items.push(elements.toArray(new Element[]{}));
			}
		}

		public void put(Element element) {
			items.add(new Element[]{element});
		}

		@Override
		public void run() {
			try {
				do {
					sleep(553);
					if (items.size() > 0) {
						Element[] toSend = items.poll();

						if (toSend != null) {
							for (Element element : toSend) {
								try {
									Packet p = Packet.packetInstance(element);
									p.setXMLNS(Packet.CLIENT_XMLNS);
									sender.sendDelayedPacket(p);
								} catch (TigaseStringprepException ex) {
									if (log.isLoggable(Level.CONFIG)) {
										log.log(Level.CONFIG, "Packet addressing problem, stringprep failed: " + element);
									}
								}
							}
						}
					}
				} while (true);
			} catch (InterruptedException e) {
				log.log(Level.WARNING, "Error during delayed delivery", e);
			}
		}

		public interface DelDeliverySend {

			void sendDelayedPacket(Packet packet);
		}
	}

}
