/*
 * DateUtil.java
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

package tigase.muc;

import tigase.util.datetime.DateTimeFormatter;

import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

public class DateUtil {

	private final static DateTimeFormatter formatter = new DateTimeFormatter();

	public static String formatDatetime(Date date) {
		return formatter.formatDateTime(date);
	}

	/**
	 * Used only in jabber:x:delivery
	 *
	 * @param date
	 *
	 * @return
	 */
	public static String formatOld(final Date date) {
		Calendar now = Calendar.getInstance();
		now.setTimeZone(TimeZone.getTimeZone("GMT"));
		now.setTime(date);
		return String.format("%1$tY%1$tm%1$tdT%1$tH:%1$tM:%1$tS", now);
	}

	public static Date parse(String s) {
		try {
			return formatter.parseDateTime(s).getTime();
		} catch (Exception e) {
			return null;
		}
	}

	private DateUtil() {
	}
}
