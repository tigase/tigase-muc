/**
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

/**
 * @author andrzej
 */
public class RoomAffiliation {

	public static final RoomAffiliation none = new RoomAffiliation(Affiliation.none, false, null);
	public static final RoomAffiliation outcast = new RoomAffiliation(Affiliation.outcast, false, null);
	public static final RoomAffiliation member = new RoomAffiliation(Affiliation.member, false, null);
	public static final RoomAffiliation admin = new RoomAffiliation(Affiliation.admin, false, null);
	public static final RoomAffiliation owner = new RoomAffiliation(Affiliation.owner, false, null);

	public static final RoomAffiliation memberPersistent = new RoomAffiliation(Affiliation.member, true, null);
	public static final RoomAffiliation adminPersistent = new RoomAffiliation(Affiliation.admin, true, null);
	public static final RoomAffiliation ownerPersistent = new RoomAffiliation(Affiliation.owner, true, null);


	public static RoomAffiliation from(Affiliation affiliation, boolean persistentOccupant, String nickname) {
		if (nickname != null) {
			return new RoomAffiliation(affiliation, persistentOccupant, nickname);
		} else {
			if (persistentOccupant) {
				switch (affiliation) {
					case admin:
						return RoomAffiliation.adminPersistent;
					case member:
						return RoomAffiliation.memberPersistent;
					case owner:
						return RoomAffiliation.ownerPersistent;
					case none:
						return RoomAffiliation.none;
					case outcast:
						return RoomAffiliation.outcast;
				}
			} else {
				switch (affiliation) {
					case admin:
						return RoomAffiliation.admin;
					case member:
						return RoomAffiliation.member;
					case none:
						return RoomAffiliation.none;
					case outcast:
						return RoomAffiliation.outcast;
					case owner:
						return RoomAffiliation.owner;
				}
			}
			throw new IllegalArgumentException();
		}
	}

	private final Affiliation affiliation;
	private final String nickname;
	private final boolean persistentOccupant;

	private RoomAffiliation(Affiliation affiliation, boolean persistentOccupant, String nickname) {
		this.affiliation = affiliation;
		this.nickname = nickname;
		this.persistentOccupant = persistentOccupant;
	}
	
	public Affiliation getAffiliation() {
		return affiliation;
	}

	public boolean isPersistentOccupant() {
		return persistentOccupant;
	}

	public String getRegisteredNickname() {
		return nickname;
	}

//	public static RoomAffiliation from(Affiliation affiliation, boolean persistentOccupant) {
//		if (persistentOccupant) {
//			switch (affiliation) {
//				case admin:
//					return RoomAffiliation.adminPersistent;
//				case member:
//					return RoomAffiliation.memberPersistent;
//				case owner:
//					return RoomAffiliation.ownerPersistent;
//				case none:
//					return RoomAffiliation.none;
//				case outcast:
//					return RoomAffiliation.outcast;
//			}
//		} else {
//			switch (affiliation) {
//				case admin:
//					return RoomAffiliation.admin;
//				case member:
//					return RoomAffiliation.member;
//				case none:
//					return RoomAffiliation.none;
//				case outcast:
//					return RoomAffiliation.outcast;
//				case owner:
//					return RoomAffiliation.owner;
//			}
//		}
//		throw new IllegalArgumentException();
//	}
//
//	public static RoomAffiliation valueof(String name) {
//		if (name != null && name.endsWith("-persistent")) {
//			Affiliation type = Affiliation.valueOf(name.substring(0, name.length() - "-persistent".length()));
//			switch (type) {
//				case admin:
//					return RoomAffiliation.adminPersistent;
//				case member:
//					return RoomAffiliation.memberPersistent;
//				case owner:
//					return RoomAffiliation.ownerPersistent;
//			}
//		} else {
//			switch (Affiliation.valueOf(name)) {
//				case admin:
//					return RoomAffiliation.admin;
//				case member:
//					return RoomAffiliation.member;
//				case none:
//					return RoomAffiliation.none;
//				case outcast:
//					return RoomAffiliation.outcast;
//				case owner:
//					return RoomAffiliation.owner;
//			}
//		}
//		throw new IllegalArgumentException();
//	}

//	public String toString() {
//		if (!isPersistentOccupant()) {
//			return affiliation.name();
//		} else {
//			return affiliation.name() + "-persistent";
//		}
//	}
	public String toString() {
		return "[aff: " + affiliation.name() + ",persistent: " + persistentOccupant + ",nickname: " + nickname + "]";
	}
}
