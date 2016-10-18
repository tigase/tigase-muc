/*
 * JDBCHistoryProviderTest.java
 *
 * Tigase Multi User Chat Component
 * Copyright (C) 2004-2016 "Tigase, Inc." <office@tigase.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
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
package tigase.muc.history;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import tigase.db.DataRepository;
import tigase.util.SchemaLoader;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Properties;

/**
 * Created by andrzej on 16.10.2016.
 */
public class JDBCHistoryProviderTest
		extends AbstractHistoryProviderTest<DataRepository> {

	private static final String PROJECT_ID = "muc";
	private static final String VERSION = "2.5.0";

	@BeforeClass
	public static void loadSchema() {
		if (uri.startsWith("jdbc:")) {
			String dbType;
			String dbName = null;
			String dbHostname = null;
			String dbUser = null;
			String dbPass = null;

			int idx = uri.indexOf(":", 5);
			dbType = uri.substring(5, idx);
			if ("jtds".equals(dbType)) dbType = "sqlserver";

			String rest = null;
			switch (dbType) {
				case "derby":
					dbName = uri.substring(idx+1, uri.indexOf(";"));
					break;
				case "sqlserver":
					idx = uri.indexOf("//", idx) + 2;
					rest = uri.substring(idx);
					for (String x : rest.split(";")) {
						if (!x.contains("=")) {
							dbHostname = x;
						} else {
							String p[] = x.split("=");
							switch (p[0]) {
								case "databaseName":
									dbName = p[1];
									break;
								case "user":
									dbUser = p[1];
									break;
								case "password":
									dbPass = p[1];
									break;
								default:
									// unknown setting
									break;
							}
						}
					}
					break;
				default:
					idx = uri.indexOf("//", idx) + 2;
					rest = uri.substring(idx);
					idx = rest.indexOf("/");
					dbHostname = rest.substring(0, idx);
					rest = rest.substring(idx+1);
					idx = rest.indexOf("?");
					dbName = rest.substring(0, idx);
					rest = rest.substring(idx + 1);
					for (String x : rest.split("&")) {
						String p[] = x.split("=");
						if (p.length < 2)
							continue;
						switch (p[0]) {
							case "user":
								dbUser = p[1];
								break;
							case "password":
								dbPass = p[1];
								break;
							default:
								break;
						}
					}
					break;
			}

			Properties props = new Properties();
			if (dbType != null)
				props.put("dbType", dbType);
			if (dbName != null)
				props.put("dbName", dbName);
			if (dbHostname != null)
				props.put("dbHostname", dbHostname);
			if (dbUser != null)
				props.put("rootUser", dbUser);
			if (dbPass != null)
				props.put("rootPass", dbPass);
			if (dbUser != null)
				props.put("dbUser", dbUser);
			if (dbPass != null)
				props.put("dbPass", dbPass);

			SchemaLoader loader = SchemaLoader.newInstance(props);
			loader.validateDBConnection(props);
			loader.validateDBExists(props);
			props.put("file", "database/" + dbType + "-" + PROJECT_ID + "-schema-" + VERSION + ".sql");
			Assert.assertEquals(SchemaLoader.Result.ok, loader.loadSchemaFile(props));
			loader.shutdown(props);
		}
	}

	@AfterClass
	public static void cleanDerby() {
		if (uri.contains("jdbc:derby:")) {
			File f = new File("derby_test");
			if (f.exists()) {
				try ( Connection conn = DriverManager.getConnection(uri + ";shutdown=true" ) ) {
					conn.close();
				} catch ( SQLException e ) {
					//e.printStackTrace();
				}
				if (f.listFiles() != null) {
					Arrays.asList(f.listFiles()).forEach(f2 -> {
						if (f2.listFiles() != null) {
							Arrays.asList(f2.listFiles()).forEach(f3 -> f3.delete());
						}
						f2.delete();
					});
				}
				f.delete();
			}
		}
	}

}
