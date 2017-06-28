/*
 * Converter.java
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
package tigase.muc.repository.migration;

import tigase.component.DSLBeanConfigurator;
import tigase.component.exceptions.RepositoryException;
import tigase.conf.ConfiguratorAbstract;
import tigase.db.DataSource;
import tigase.db.DataSourceHelper;
import tigase.db.beans.DataSourceBean;
import tigase.db.beans.UserRepositoryMDPoolBean;
import tigase.eventbus.EventBusFactory;
import tigase.kernel.DefaultTypesConverter;
import tigase.kernel.beans.config.AbstractBeanConfigurator;
import tigase.kernel.core.Kernel;
import tigase.muc.MUCConfig;
import tigase.muc.Room;
import tigase.muc.RoomWithId;
import tigase.muc.repository.IMucDAO;
import tigase.muc.repository.MucDAOOld;

import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by andrzej on 17.10.2016.
 */
public class Converter {

	private static final Logger log = Logger.getLogger(Converter.class.getCanonicalName());

	public static void initLogger() {
		String initial_config = "tigase.level=ALL\n" + "tigase.db.jdbc.level=INFO\n" + "tigase.xml.level=INFO\n"
				+ "tigase.form.level=INFO\n" + "handlers=java.util.logging.ConsoleHandler java.util.logging.FileHandler\n"
				+ "java.util.logging.ConsoleHandler.level=ALL\n"
				+ "java.util.logging.ConsoleHandler.formatter=tigase.util.LogFormatter\n"
				+ "java.util.logging.FileHandler.formatter=tigase.util.LogFormatter\n"
				+ "java.util.logging.FileHandler.pattern=muc_db_migration.log\n" + "tigase.useParentHandlers=true\n";

		ConfiguratorAbstract.loadLogManagerConfig(initial_config);
	}

	public static void main(String[] argv) throws RepositoryException, IOException {
		initLogger();

		if (argv == null || argv.length == 0) {
			System.out.println("\nConverter paramters:\n");
			System.out.println(
					" -in-repo-class tigase.db.jdbc.DataRepositoryImpl                           -      class implementing UserRepository");
			System.out.println(
					" -in 'jdbc:xxxx://localhost/tigasedb?user=tigase&password=tigase_pass'      -		uri of source database");
			System.out.println(
					" -out 'jdbc:xxxx://localhost/tigasedb?user=tigase&password=tigase_pass'     -		uri of destination database");
			return;
		}

		Converter converter = new Converter();

		log.config("parsing configuration parameters");
		String repoClass = null;
		String oldRepoUri = null;
		String newRepoUri = null;
		for (int i = 0; i < argv.length; i++) {
			String arg = argv[i];
			if ("-in".equals(arg)) {
				i++;
				oldRepoUri = argv[i];
			} else if ("-out".equals(arg)) {
				i++;
				newRepoUri = argv[i];
			} else if ("-in-repo-class".equals(arg)) {
				i++;
				repoClass = argv[i];
			}
		}

		log.config("initializing converter");
		converter.init(repoClass, oldRepoUri, newRepoUri);

		log.info("starting migration");
		converter.convert();
		log.info("migration finished");
	}

	private IMucDAO newRepo;

	private MucDAOOld oldRepo;

	public void convert() throws RepositoryException {
		oldRepo.getRoomsJIDList().forEach(roomJid -> {
			try {
				RoomWithId room = (RoomWithId) oldRepo.readRoom(roomJid);
				String subject = oldRepo.getSubject(roomJid);
				Date subjectDate = oldRepo.getSubjectCreationDate(roomJid);
				String subjectNick = oldRepo.getSubjectCreatorNickname(roomJid);

				if (room == null || room.getConfig() == null) {
					log.log(Level.WARNING, "skipping conversion of room with jid " + roomJid + " - room configuration is missing!");
					oldRepo.destroyRoom(roomJid);
					return;
				}

				if (newRepo.getRoom(roomJid) != null)
					return;

				newRepo.createRoom(room);

				if (subjectDate != null) {
					newRepo.setSubject(room, subject, subjectNick, subjectDate);
				}

				oldRepo.destroyRoom(roomJid);
			} catch (RepositoryException e) {
				throw new RuntimeException("Repository conversion failed", e);
			}
		});
	}

	public void init(String repoClass, String oldRepoUri, String newRepoUri) throws RepositoryException {
		Kernel kernel = new Kernel();
		try {
			kernel.registerBean(DefaultTypesConverter.class).exec();
			kernel.registerBean(DSLBeanConfigurator.class).exec();
			DSLBeanConfigurator configurator = kernel.getInstance(DSLBeanConfigurator.class);

			Map<String, Object> props = new HashMap<>();
			Map<String, Object> dataSourceProps = new HashMap<>();
			Map<String, Object> defaultProps = new HashMap<>();
			defaultProps.put("uri", oldRepoUri);
			dataSourceProps.put("default", defaultProps);

			AbstractBeanConfigurator.BeanDefinition mucDataSourceProps = new AbstractBeanConfigurator.BeanDefinition();
			mucDataSourceProps.setBeanName("new-repo");
			mucDataSourceProps.put("uri", newRepoUri);
			dataSourceProps.put("new-repo", mucDataSourceProps);

			props.put("dataSource", dataSourceProps);
			if (repoClass != null) {
				Map<String, Object> userRepo = new HashMap<>();
				Map<String, Object> userDefaultProps = new HashMap<>();
				userDefaultProps.put("cls", repoClass);
				userRepo.put("default", userDefaultProps);
				props.put("userRepository", userRepo);
			}

			configurator.setProperties(props);

			kernel.registerBean("eventBus").asInstance(EventBusFactory.getInstance()).exportable().exec();
			kernel.registerBean(DataSourceBean.class).exportable().exec();
			kernel.registerBean(Room.RoomFactoryImpl.class).exec();
			kernel.registerBean(UserRepositoryMDPoolBean.class).exportable().exec();
			kernel.registerBean("muc-dao-old").asClass(MucDAOOld.class).exec();

			Class<IMucDAO> daoClass = DataSourceHelper.getDefaultClass(IMucDAO.class, newRepoUri);
			kernel.registerBean(MUCConfig.class).exec();
			kernel.registerBean("muc-dao").asClass(daoClass).exec();

			DataSource ds = ((DataSourceBean) kernel.getInstance("dataSource")).getRepository("new-repo");

			oldRepo = kernel.getInstance(MucDAOOld.class);

			newRepo = kernel.getInstance(IMucDAO.class);

			newRepo.setDataSource(ds);
		} catch (Exception ex) {
			throw new RepositoryException("could not initialize converter", ex);
		}
	}
}
