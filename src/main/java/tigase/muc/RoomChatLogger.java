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

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * @author bmalkow
 * 
 */
public class RoomChatLogger {

	private final static String HTML_FORMAT = "<a name=\"%s\" href=\"#%s\" class=\"ts\">[%s]</a> <font class=\"mn\">&lt;%s&gt;</font>%s<br/>\n";

	private final static String PLAIN_FORMAT = "[%s] <%s> %s\n";

	private final static SimpleDateFormat sdf = new SimpleDateFormat("hh:mm:ss");

	public static void main(String[] args) throws IOException {

	}

	private final MucConfig config;

	/**
	 * @param config2
	 */
	public RoomChatLogger(MucConfig config2) {
		this.config = config2;
	}

	public void addMessage(RoomConfig.LogFormat logFormat, String roomId, Date date, String nickName, String message)
			throws IOException {
		String d = sdf.format(date);

		String line;
		String ext;

		switch (logFormat) {
		case html:
			line = String.format(HTML_FORMAT, d, d, d, nickName, message);
			ext = ".html";
			break;
		case xml:
			line = String.format(PLAIN_FORMAT, d, nickName, message);
			ext = ".xml";
			break;
		case plain:
			line = String.format(PLAIN_FORMAT, d, nickName, message);
			ext = ".txt";
			break;
		default:
			throw new RuntimeException("Unsupported log format: " + logFormat.name());
		}

		FileWriter fw = new FileWriter(new File(config.getLogDirectory() + "/" + roomId + ext), true);
		fw.append(line);
		fw.close();
	}

}
