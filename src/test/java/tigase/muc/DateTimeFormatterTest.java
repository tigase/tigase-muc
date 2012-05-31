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

import static org.junit.Assert.assertEquals;

import java.util.Calendar;
import java.util.TimeZone;

import org.junit.Assert;
import org.junit.Test;

/**
 * @author bmalkow
 * 
 */
public class DateTimeFormatterTest {

	DateTimeFormatter dt = new DateTimeFormatter();

	@Test
	public void testFormat01() {
		Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
		cal.set(2009, 8, 11, 11, 12, 13);

		String actual = dt.formatDateTime(cal.getTime());
		assertEquals("2009-09-11T11:12:13Z", actual);
	}

	@Test
	public void testFormat02() {
		Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("Europe/Warsaw"));
		cal.set(2009, 8, 11, 13, 12, 13);

		String actual = dt.formatDateTime(cal.getTime());
		assertEquals("2009-09-11T11:12:13Z", actual);
	}

	@Test
	public void testFormat03() {
		Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
		cal.set(2009, 8, 11, 11, 12, 13);

		String actual = dt.formatDate(cal.getTime());
		assertEquals("2009-09-11", actual);
	}

	@Test
	public void testFormat04() {
		Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("Europe/Warsaw"));
		cal.set(2009, 8, 11, 13, 12, 13);

		String actual = dt.formatDate(cal.getTime());
		assertEquals("2009-09-11", actual);
	}

	@Test
	public void testFormat05() {
		Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
		cal.set(2009, 8, 11, 11, 12, 13);

		String actual = dt.formatTime(cal.getTime());
		assertEquals("11:12:13Z", actual);
	}

	@Test
	public void testFormat06() {
		Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("Europe/Warsaw"));
		cal.set(2009, 8, 11, 13, 12, 13);

		String actual = dt.formatTime(cal.getTime());
		assertEquals("11:12:13Z", actual);
	}

	/**
	 * Test method for
	 * {@link tigase.muc.DateTimeFormatter#parseDateTime(java.lang.String)}.
	 */
	@Test
	public void testParse() {

	}

	@Test
	public void testParse01() throws Exception {
		Calendar actual = dt.parseDateTime("11:12:13Z");
		Calendar expected = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
		expected.clear();
		expected.set(Calendar.HOUR_OF_DAY, 11);
		expected.set(Calendar.MINUTE, 12);
		expected.set(Calendar.SECOND, 13);
		assertEquals(expected, actual);
	}

	@Test
	public void testParse02() throws Exception {
		Calendar actual = dt.parseDateTime("11:12:13-01:30");
		Calendar expected = Calendar.getInstance(TimeZone.getTimeZone("GMT-01:30"));
		expected.clear();
		expected.set(Calendar.HOUR_OF_DAY, 11);
		expected.set(Calendar.MINUTE, 12);
		expected.set(Calendar.SECOND, 13);

		assertEquals(expected, actual);
	}

	@Test
	public void testParse03() throws Exception {
		Calendar actual = dt.parseDateTime("2009-09-11T11:12:13-01:30");
		Calendar expected = Calendar.getInstance(TimeZone.getTimeZone("GMT-01:30"));
		expected.clear();
		expected.set(2009, 8, 11, 11, 12, 13);
		assertEquals(expected, actual);
	}

	@Test
	public void testParse04() throws Exception {
		try {
			dt.parseDateTime("2009-09-11T11:12:13");
			Assert.fail("Must throw IllegalArgumentException");
		} catch (IllegalArgumentException e) {
			// OK
		}
	}

	@Test
	public void testParse05() throws Exception {
		Calendar actual = dt.parseDateTime("2009-09-11");
		Calendar expected = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
		expected.clear();
		expected.set(2009, 8, 11);
		assertEquals(expected, actual);
	}

	@Test
	public void testParse06() throws Exception {
		Calendar actual = dt.parseDateTime("2009-09-11T11:12:13Z");
		Calendar expected = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
		expected.clear();
		expected.set(2009, 8, 11, 11, 12, 13);
		assertEquals(expected, actual);
	}

	@Test
	public void testParse07() throws Exception {
		Calendar actual = dt.parseDateTime("11:12:13");
		Calendar expected = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
		expected.clear();
		expected.set(Calendar.HOUR_OF_DAY, 11);
		expected.set(Calendar.MINUTE, 12);
		expected.set(Calendar.SECOND, 13);
		assertEquals(expected, actual);
	}
}
