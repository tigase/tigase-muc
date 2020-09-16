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
import tigase.xmpp.jid.BareJID;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by andrzej on 17.10.2016.
 */
public class Converter {

	private static final Logger log = Logger.getLogger(Converter.class.getCanonicalName());
	private IMucDAO newRepo;
	private MucDAOOld oldRepo;
	private boolean stopOnError;


	public static void initLogger() {
		// @formatter:off
		String initial_config =
				"tigase.level=ALL\n" +
				"tigase.db.jdbc.level=INFO\n" +
				"tigase.xml.level=INFO\n" +
				"tigase.form.level=INFO\n" +
				"tigase.kernel.level=INFO\n" +
				"handlers=java.util.logging.ConsoleHandler java.util.logging.FileHandler\n" +
				"java.util.logging.ConsoleHandler.level=INFO\n" +
				"java.util.logging.ConsoleHandler.formatter=" + tigase.util.log.LogFormatter.class.getName() + "\n" +
				"java.util.logging.FileHandler.LEVEL=ALL\n" +
				"java.util.logging.FileHandler.formatter=" + tigase.util.log.LogFormatter.class.getName() + "\n" +
				"java.util.logging.FileHandler.pattern=muc_db_migration.log\n" +
				"tigase.useParentHandlers=true\n";
		// @formatter:on

		ConfiguratorAbstract.loadLogManagerConfig(initial_config);
	}

	public static void main(String[] argv) throws IOException {
		initLogger();

		if (argv == null || argv.length == 0) {
			System.out.println("\nConverter parameters:\n");
			System.out.println(
					" -in-repo-class tigase.db.jdbc.DataRepositoryImpl                           -      class implementing UserRepository");
			System.out.println(
					" -in 'jdbc:xxxx://localhost/tigasedb?user=tigase&password=tigase_pass'      -		uri of source database");
			System.out.println(
					" -out 'jdbc:xxxx://localhost/tigasedb?user=tigase&password=tigase_pass'     -		uri of destination database");
			System.out.println(
					" -stop-on-error                                                             -      whether to stop conversion on first error");
			return;
		}

		Converter converter = new Converter();

		log.info("parsing configuration parameters");
		String repoClass = null;
		String oldRepoUri = null;
		String newRepoUri = null;
		boolean stopOnError = false;
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
			} else if ("-stop-on-error".equals(arg)) {
				stopOnError = true;
			}
		}

		log.info("initializing converter");
		try {
			converter.init(repoClass, oldRepoUri, newRepoUri, stopOnError);
			log.info("Starting migration");
			final boolean conversionFinishedCorrectly = converter.convert();
			log.info(conversionFinishedCorrectly ? "Migration finished correctly" : "Migration FAILED!");
			System.exit(conversionFinishedCorrectly ? 0 : 1);
		} catch (Exception e) {
			log.log(Level.WARNING, "Migration failed: " + e, e);
			System.exit(1);
		}
	}

	/**
	 * @return {@code true} if the conversion was correct and {@code false} if there were errors
	 *
	 * @throws RepositoryException
	 */
	public boolean convert() throws Exception {
		AtomicLong failedConversionCount = new AtomicLong();
		AtomicLong warningConversionCount = new AtomicLong();
		final ArrayList<BareJID> roomsJIDList = oldRepo.getRoomsJIDList();
		RoomWithId room = null;
		String subject = null;
		Date subjectDate = null;
		String subjectNick = null;
		for (BareJID roomJid : roomsJIDList) {
			try {
				log.log(Level.FINE, "Reading details of room with jid: {0}", new Object[]{roomJid});
				room = (RoomWithId) oldRepo.readRoom(roomJid);
				subject = oldRepo.getSubject(roomJid);
				subjectDate = oldRepo.getSubjectCreationDate(roomJid);
				subjectNick = oldRepo.getSubjectCreatorNickname(roomJid);

				if (room == null || room.getConfig() == null) {
					warningConversionCount.incrementAndGet();
					log.log(Level.WARNING,
							"skipping conversion of room with jid " + roomJid + " - room configuration is missing!");
					oldRepo.destroyRoom(roomJid);
					continue;
				}

				if (newRepo.getRoom(roomJid) != null) {
					warningConversionCount.incrementAndGet();
					log.log(Level.INFO, "Room with jid " + roomJid + " already exists, skipping conversion!");
					continue;
				}

				log.log(Level.FINER,
						"Converting room with jid: {0}. Subject: {1}, SubjectCreationDate: {2}, SubjectCreatorNickname: {3}, Room affiliations: {4}, Room configuration: {5}",
						new Object[]{roomJid, subject, subjectDate, subjectNick, room.getAffiliations(),
									 room.getConfig().getAsElement()});
				newRepo.createRoom(room);

				if (subjectDate != null) {
					newRepo.setSubject(room, subject, subjectNick, subjectDate);
				}

				oldRepo.destroyRoom(roomJid);
				log.log(Level.FINE, "Room with jid: {0} converted successfully", new Object[]{roomJid});
			} catch (Exception e) {
				failedConversionCount.incrementAndGet();
				String roomDetails = room == null
									 ? "n/a"
									 : ", Room affiliations: " + room.getAffiliations() + ", Room configuration: " +
											 room.getConfig().getAsElement();
				log.log(Level.WARNING, "Error converting room with jid: " + roomJid + ". Subject: " + subject +
						", SubjectCreationDate: " + subjectDate + ", SubjectCreatorNickname: " + subjectNick +
						roomDetails, e);
				if (stopOnError) {
					throw new RuntimeException("Repository conversion failed", e);
				}
			}
		}
		log.log(Level.INFO, "Failed converting {0} out of {1} rooms",
				new Object[]{failedConversionCount.get(), roomsJIDList.size()});
		return failedConversionCount.get() == 0;
	}

	public void init(String repoClass, String oldRepoUri, String newRepoUri, boolean stopOnError) throws RepositoryException {
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
			dataSourceProps.put("schema-management", false);

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
			oldRepo.setIgnoreIncorrectRoomNames(true);

			newRepo = kernel.getInstance(IMucDAO.class);

			newRepo.setDataSource(ds);
			this.stopOnError = stopOnError;
		} catch (Exception ex) {
			throw new RepositoryException("could not initialize converter", ex);
		}
	}
}
