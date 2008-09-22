/*
 * Tigase Jabber/XMPP Multi-User Chat Component
 * Copyright (C) 2008 "Bartosz M. Małkowski" <bartosz.malkowski@tigase.org>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. Look for COPYING file in the top folder.
 * If not, see http://www.gnu.org/licenses/.
 *
 * $Rev$
 * Last modified by $Author$
 * $Date$
 */
package tigase.muc;

import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

/**
 * @author bmalkow
 * 
 */
public class XMPPDateTimeFormatter {

	public final static String FORMAT = "%1$tY-%1$tm-%1$tdT%1$tH:%1$tM:%1$tSZ";

	public XMPPDateTimeFormatter() {
	}

	public String format(final Date date) {
		Calendar now = Calendar.getInstance();
		now.setTimeZone(TimeZone.getTimeZone("GMT"));
		now.setTime(date);
		return String.format("%1$tY-%1$tm-%1$tdT%1$tH:%1$tM:%1$tSZ", now);
	}

	/**
	 * Used only in jabber:x:delivery
	 * 
	 * @param date
	 * @return
	 */
	public String formatOld(final Date date) {
		Calendar now = Calendar.getInstance();
		now.setTimeZone(TimeZone.getTimeZone("GMT"));
		now.setTime(date);
		return String.format("%1$tY%1$tm%1$tdT%1$tH:%1$tM:%1$tS", now);
	}

}
