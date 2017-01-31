package tigase.muc;

import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

import tigase.util.DateTimeFormatter;

public class DateUtil {

	private final static DateTimeFormatter formatter = new DateTimeFormatter();

	public static String formatDatetime(Date date) {
		return formatter.formatDateTime(date);
	}

	/**
	 * Used only in jabber:x:delivery
	 * 
	 * @param date
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
