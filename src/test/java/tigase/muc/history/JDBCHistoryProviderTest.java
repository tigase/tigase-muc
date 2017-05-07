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
import tigase.db.util.SchemaLoader;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Arrays;

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
			SchemaLoader loader = SchemaLoader.newInstance("jdbc");
			SchemaLoader.Parameters params = loader.createParameters();
			params.parseUri(uri);
			params.setDbRootCredentials(null, null);
			loader.init(params);
			loader.validateDBConnection();
			loader.validateDBExists();
			Assert.assertEquals(SchemaLoader.Result.ok, loader.loadSchema(PROJECT_ID, VERSION));
			loader.shutdown();
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
