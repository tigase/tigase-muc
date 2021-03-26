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

package tigase.muc;

public class StatusCodes {

	/**
	 * Inform user that any occupant is allowed to see the user's full JID.
	 */
	public static final Integer OCCUPANT_IS_ALLOWED_TO_SEE_JID = 100;

	/**
	 * Inform user that presence refers to itself.
	 */
	public static final Integer SELF_PRESENCE = 110;

	/**
	 * Inform occupants that room logging is now enabled.
	 */
	public static final Integer ROOM_LOGGING_IS_ENABLED = 170;

	/**
	 * Inform occupants that room logging is now disabled.
	 */
	public static final Integer ROOM_LOGGING_IS_DISABLED = 171;

	/**
	 * Inform user that a new room has been created.
	 */
	public static final Integer NEW_ROOM = 201;

	/**
	 * Inform all occupants of new room nickname.
	 */
	public static final Integer NEW_NICKNAME = 303;

	/**
	 * Inform occupants that the room is now non-anonymous.
	 */
	public static final Integer ROOM_IS_NOW_NON_ANONYMOUS = 172;

	/**
	 * Inform occupants that the room is now semi-anonymous.
	 */
	public static final Integer ROOM_IS_NOW_SEMI_ANONYMOUS = 173;

	/**
	 * Inform occupants that a non-privacy-related room configuration change has occurred.
	 */
	public static final Integer CONFIGURATION_CHANGE = 104;

	/**
	 * Inform user that he or she has been kicked from the room.
	 */
	public static final Integer KICKED = 307;

	/**
	 * Inform user that he or she has been banned from the room.
	 */
	public static final Integer BANNED = 301;
	/**
	 * Inform users that a user was removed because of an error reply (for example
	 * when an s2s link fails between the MUC and the removed users server).
	 */
	public static final Integer REMOVED_FROM_ROOM = 333;

}
