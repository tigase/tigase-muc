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
package tigase.muc.repository;

import java.util.logging.Level;
import java.util.logging.Logger;

import tigase.component.PropertiesBeanConfigurator;
import tigase.db.RepositoryFactory;
import tigase.db.UserRepository;
import tigase.kernel.KernelException;
import tigase.kernel.beans.BeanFactory;
import tigase.kernel.beans.Inject;
import tigase.muc.MUCConfig;

/**
 * @author bmalkow
 *
 */
public class UserRepositoryFactory implements BeanFactory<UserRepository> {

	@Inject(nullAllowed = false)
	private MUCConfig config;

	@Inject
	private PropertiesBeanConfigurator configurator;

	protected Logger log = Logger.getLogger(this.getClass().getName());

	@Override
	public UserRepository createInstance() throws KernelException {
		try {
			final String cls_name = config.getRepositoryClassName();
			final String res_uri = config.getRepositoryUri();

			log.config("Initializing MUC Repository" + "; cls_name: " + cls_name + "; res_uri: " + res_uri);

			UserRepository userRepository;
			if (cls_name != null && res_uri != null) {
				userRepository = RepositoryFactory.getUserRepository(cls_name, res_uri, null);
			} else {
				userRepository = (UserRepository) configurator.getProperties().get(RepositoryFactory.SHARED_USER_REPO_PROP_KEY);
			}

			return userRepository;

		} catch (Exception e) {
			log.log(Level.WARNING, "Cannot initialize MUC Repository", e);
			throw new KernelException("Cannot create instance of UserRepository", e);
		}
	}

}
