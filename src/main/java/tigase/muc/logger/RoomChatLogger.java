/*
 * Tigase Jabber/XMPP Multi-User Chat Component
 * Copyright (C) 2008 "Bartosz M. Małkowski" <bartosz.malkowski@tigase.org>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. Look for COPYING file in the top folder.
 * If not, see http://www.gnu.org/licenses/.
 *
 * $Rev$
 * Last modified by $Author$
 * $Date$
 */
package tigase.muc.logger;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;

import tigase.muc.MucContext;
import tigase.muc.Room;
import tigase.muc.RoomConfig;
import tigase.xmpp.BareJID;
import tigase.xmpp.JID;

/**
 * @author bmalkow
 * 
 */
public class RoomChatLogger implements MucLogger {

	private static class Item {
		final String data;
		final File file;

		public Item(File file, String text) {
			this.file = file;
			this.data = text;
		}

	}

	private static class Worker extends Thread {

		private final LinkedList<Item> items = new LinkedList<Item>();

		@Override
		public void run() {
			try {
				while (true) {
					Item it = items.poll();
					if (it == null) {
						sleep(1000);
					} else {
						FileWriter fw = new FileWriter(it.file, true);
						fw.append(it.data);
						fw.close();
						sleep(15);
					}
				}
			} catch (InterruptedException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

	}

	private final static String JOIN_HTML_FORMAT = "<a name=\"%1$s\" href=\"#%1$s\" class=\"mj\">[%1$s]</a>%2$s joins the room<br/>\n";

	private final static String JOIN_PLAIN_FORMAT = "[%1$s] %2$s joins the room\n";

	private final static String LEAVE_HTML_FORMAT = "<a name=\"%1$s\" href=\"#%1$s\" class=\"ml\">[%1$s]</a>%2$s leaves the room<br/>\n";

	private final static String LEAVE_PLAIN_FORMAT = "[%1$s] %2$s leaves the room\n";

	private final static String MESSAGE_HTML_FORMAT = "<a name=\"%1$s\" href=\"%1$s\" class=\"ts\">[%1$s]</a> <font class=\"mn\">&lt;%2$s&gt;</font>%3$s<br/>\n";

	private final static String MESSAGE_PLAIN_FORMAT = "[%1$s] <%2$s> %3$s\n";

	private final static SimpleDateFormat sdf = new SimpleDateFormat("hh:mm:ss");

	private final static String SUBJECT_HTML_FORMAT = "<a name=\"%1$s\" href=\"#%1$s\" class=\"msc\">[%1$s]</a>%2$s has set the subject to: %3$s<br/>\n";

	private final static String SUBJECT_PLAIN_FORMAT = "[%1$s] %2$s has set the subject to: %3$s\n";

	private MucContext context;

	private final Worker worker = new Worker();

	/**
	 * @param config2
	 */
	public RoomChatLogger() {
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see tigase.muc.IChatRoomLogger#addJoin(tigase.muc.RoomConfig.LogFormat,
	 * java.lang.String, java.util.Date, java.lang.String)
	 */
	@Override
	public void addJoinEvent(Room room, Date date, JID senderJID, String nickName) {

		String pattern;
		switch (room.getConfig().getLoggingFormat()) {
		case html:
			pattern = JOIN_HTML_FORMAT;
			break;
		case xml:
			pattern = JOIN_PLAIN_FORMAT;
			break;
		case plain:
			pattern = JOIN_PLAIN_FORMAT;
			break;
		default:
			throw new RuntimeException("Unsupported log format: " + room.getConfig().getLoggingFormat());
		}
		addLine(pattern, room.getConfig().getLoggingFormat(), room.getRoomJID(), date, nickName, null);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see tigase.muc.IChatRoomLogger#addLeave(tigase.muc.RoomConfig.LogFormat,
	 * java.lang.String, java.util.Date, java.lang.String)
	 */
	@Override
	public void addLeaveEvent(Room room, Date date, JID senderJID, String nickName) {

		String pattern;
		switch (room.getConfig().getLoggingFormat()) {
		case html:
			pattern = LEAVE_HTML_FORMAT;
			break;
		case xml:
			pattern = LEAVE_PLAIN_FORMAT;
			break;
		case plain:
			pattern = LEAVE_PLAIN_FORMAT;
			break;
		default:
			throw new RuntimeException("Unsupported log format: " + room.getConfig().getLoggingFormat());
		}
		addLine(pattern, room.getConfig().getLoggingFormat(), room.getRoomJID(), date, nickName, null);
	}

	private void addLine(String pattern, RoomConfig.LogFormat logFormat, BareJID roomJID, Date date, String nickName,
			String text) {

		String d = sdf.format(date);
		Object[] values = new String[] { d, nickName, text };
		final String line = String.format(pattern, values);
		String ext;

		switch (logFormat) {
		case html:
			ext = ".html";
			break;
		case xml:
			ext = ".xml";
			break;
		case plain:
			ext = ".txt";
			break;
		default:
			throw new RuntimeException("Unsupported log format: " + logFormat.name());
		}

		Item it = new Item(new File(context.getChatLoggingDirectory() + "/" + roomJID + ext), line);
		this.worker.items.add(it);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * tigase.muc.IChatRoomLogger#addMessage(tigase.muc.RoomConfig.LogFormat,
	 * java.lang.String, java.util.Date, java.lang.String, java.lang.String)
	 */
	@Override
	public void addMessage(Room room, String message, JID senderJid, String senderNickname, Date time) {

		String pattern;
		switch (room.getConfig().getLoggingFormat()) {
		case html:
			pattern = MESSAGE_HTML_FORMAT;
			break;
		case xml:
			pattern = MESSAGE_PLAIN_FORMAT;
			break;
		case plain:
			pattern = MESSAGE_PLAIN_FORMAT;
			break;
		default:
			throw new RuntimeException("Unsupported log format: " + room.getConfig().getLoggingFormat());
		}
		addLine(pattern, room.getConfig().getLoggingFormat(), room.getRoomJID(), time, senderNickname, message);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * tigase.muc.IChatRoomLogger#addSubject(tigase.muc.RoomConfig.LogFormat,
	 * java.lang.String, java.util.Date, java.lang.String, java.lang.String)
	 */
	@Override
	public void addSubjectChange(Room room, String message, JID senderJid, String senderNickname, Date time) {

		String pattern;
		switch (room.getConfig().getLoggingFormat()) {
		case html:
			pattern = SUBJECT_HTML_FORMAT;
			break;
		case xml:
			pattern = SUBJECT_PLAIN_FORMAT;
			break;
		case plain:
			pattern = SUBJECT_PLAIN_FORMAT;
			break;
		default:
			throw new RuntimeException("Unsupported log format: " + room.getConfig().getLoggingFormat());
		}
		addLine(pattern, room.getConfig().getLoggingFormat(), room.getRoomJID(), time, senderNickname, message);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see tigase.muc.logger.MucLogger#init(java.util.Map)
	 */
	@Override
	public void init(MucContext context) {
		this.context = context;
		this.worker.start();
	}
}
