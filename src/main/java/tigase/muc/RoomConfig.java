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

//~--- non-JDK imports --------------------------------------------------------

import tigase.db.TigaseDBException;
import tigase.db.UserNotFoundException;
import tigase.db.UserRepository;

import tigase.form.Field;
import tigase.form.Field.FieldType;
import tigase.form.Form;

//~--- JDK imports ------------------------------------------------------------

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

//~--- classes ----------------------------------------------------------------

/**
 * @author bmalkow
 *
 */
public class RoomConfig {
	private static final String LOGGING_FORMAT_KEY = "logging_format";

	/** Field description */
	public static final String MUC_ROOMCONFIG_ANONYMITY_KEY = "muc#roomconfig_anonymity";

	/** Field description */
	public static final String MUC_ROOMCONFIG_CHANGESUBJECT_KEY = "muc#roomconfig_changesubject";

	/** Field description */
	public static final String MUC_ROOMCONFIG_ENABLELOGGING_KEY = "muc#roomconfig_enablelogging";

	/** Field description */
	public static final String MUC_ROOMCONFIG_MEMBERSONLY_KEY = "muc#roomconfig_membersonly";

	/** Field description */
	public static final String MUC_ROOMCONFIG_MODERATEDROOM_KEY = "muc#roomconfig_moderatedroom";

	/** Field description */
	public static final String MUC_ROOMCONFIG_PASSWORDPROTECTEDROOM_KEY =
		"muc#roomconfig_passwordprotectedroom";

	/** Field description */
	public static final String MUC_ROOMCONFIG_PERSISTENTROOM_KEY =
		"muc#roomconfig_persistentroom";

	/** Field description */
	public static final String MUC_ROOMCONFIG_PUBLICROOM_KEY = "muc#roomconfig_publicroom";

	/** Field description */
	public static final String MUC_ROOMCONFIG_ROOMDESC_KEY = "muc#roomconfig_roomdesc";

	/** Field description */
	public static final String MUC_ROOMCONFIG_ROOMNAME_KEY = "muc#roomconfig_roomname";

	/** Field description */
	public static final String MUC_ROOMCONFIG_ROOMSECRET_KEY = "muc#roomconfig_roomsecret";

	//~--- constant enums -------------------------------------------------------

	/**
	 * Enum description
	 *
	 */
	public static enum Anonymity {

		/**
		 * Fully-Anonymous Room -- a room in which the full JIDs or bare JIDs of
		 * occupants cannot be discovered by anyone, including room admins and
		 * room owners; such rooms are NOT RECOMMENDED or explicitly supported
		 * by MUC, but are possible using this protocol if a service
		 * implementation offers the appropriate configuration options; contrast
		 * with Non-Anonymous Room and Semi-Anonymous Room.
		 */
		fullanonymous,

		/**
		 * Non-Anonymous Room -- a room in which an occupant's full JID is
		 * exposed to all other occupants, although the occupant may choose any
		 * desired room nickname; contrast with Semi-Anonymous Room and
		 * Fully-Anonymous Room.
		 */
		nonanonymous,

		/**
		 * Semi-Anonymous Room -- a room in which an occupant's full JID can be
		 * discovered by room admins only; contrast with Fully-Anonymous Room
		 * and Non-Anonymous Room.
		 */
		semianonymous
	}

	/**
	 * Enum description
	 *
	 */
	public static enum LogFormat { html, plain, xml }

	//~--- fields ---------------------------------------------------------------

	protected final Set<String> blacklist = new HashSet<String>();
	protected final Form form = new Form("form", null, null);
	private final ArrayList<RoomConfigListener> listeners = new ArrayList<RoomConfigListener>();
	private final String roomId;

	//~--- constructors ---------------------------------------------------------

	/**
	 * @param roomId
	 */
	public RoomConfig(String roomId) {
		this.roomId = roomId;
		init();
	}

	//~--- methods --------------------------------------------------------------

	protected static String[] asStrinTable(Enum<?>[] values) {
		String[] result = new String[values.length];
		int i = 0;

		for (Enum<?> v : values) {
			result[i++] = v.name();
		}

		return result;
	}

	/**
	 * Method description
	 *
	 *
	 * @param listener
	 */
	public void addListener(RoomConfigListener listener) {
		this.listeners.add(listener);
	}

	/**
	 * Method description
	 *
	 *
	 * @return
	 */
	@Override
	public RoomConfig clone() {
		final RoomConfig rc = new RoomConfig(this.roomId);

		rc.blacklist.addAll(this.blacklist);
		rc.form.copyValuesFrom(form);

		return rc;
	}

	/**
	 * Method description
	 *
	 *
	 * @param oldConfig
	 *
	 * @return
	 */
	public String[] compareTo(RoomConfig oldConfig) {
		Set<String> result = new HashSet<String>();
		Set<String> vars = equals(oldConfig.form);

		for (String var : vars) {
			if ((MUC_ROOMCONFIG_ANONYMITY_KEY).equals(var)) {
				switch (getRoomAnonymity()) {
					case nonanonymous :
						result.add("172");

						break;

					case semianonymous :
						result.add("173");

						break;

					case fullanonymous :
						result.add("174");

						break;
				}
			} else {
				if ((MUC_ROOMCONFIG_ENABLELOGGING_KEY).equals(var)) {
					result.add(isLoggingEnabled() ? "170" : "171");
				} else {
					result.add("104");
				}
			}
		}

		return (result.size() == 0) ? null : result.toArray(new String[] {});
	}

	/**
	 * Method description
	 *
	 *
	 * @param configForm
	 */
	public void copyFrom(Form configForm) {
		copyFrom(configForm, true);
	}

	/**
	 *
	 * @param configForm
	 * @param fireEvents
	 */
	public void copyFrom(Form configForm, boolean fireEvents) {
		final Set<String> modifiedVars = fireEvents ? equals(configForm) : null;

		form.copyValuesFrom(configForm);

		if ((modifiedVars != null) && (modifiedVars.size() > 0)) {
			fireConfigChanged(modifiedVars);
		}
	}

	/**
	 * Method description
	 *
	 *
	 * @param c
	 */
	public void copyFrom(RoomConfig c) {
		copyFrom(c.form, true);
	}

	/**
	 *
	 * @param c
	 * @param fireEvents
	 */
	public void copyFrom(RoomConfig c, boolean fireEvents) {
		copyFrom(c.form, fireEvents);
	}

	//~--- get methods ----------------------------------------------------------

	/**
	 * Method description
	 *
	 *
	 * @return
	 */
	public Form getConfigForm() {
		return form;
	}

	/**
	 * Method description
	 *
	 *
	 * @return
	 */
	public LogFormat getLoggingFormat() {
		try {
			String tmp = form.getAsString(LOGGING_FORMAT_KEY);

			return (tmp == null) ? LogFormat.html : LogFormat.valueOf(tmp);
		} catch (Exception e) {
			return LogFormat.html;
		}
	}

	/**
	 * Method description
	 *
	 *
	 * @return
	 */
	public String getPassword() {
		return asString(form.getAsString(MUC_ROOMCONFIG_ROOMSECRET_KEY), "");
	}

	/**
	 * Method description
	 *
	 *
	 * @return
	 */
	public Anonymity getRoomAnonymity() {
		try {
			String tmp = form.getAsString(MUC_ROOMCONFIG_ANONYMITY_KEY);

			return (tmp == null) ? Anonymity.semianonymous : Anonymity.valueOf(tmp);
		} catch (Exception e) {
			return Anonymity.semianonymous;
		}
	}

	/**
	 * Method description
	 *
	 *
	 * @return
	 */
	public String getRoomDesc() {
		return form.getAsString(MUC_ROOMCONFIG_ROOMDESC_KEY);
	}

	/**
	 * Method description
	 *
	 *
	 * @return
	 */
	public String getRoomId() {
		return roomId;
	}

	/**
	 * Method description
	 *
	 *
	 * @return
	 */
	public String getRoomName() {
		return form.getAsString(MUC_ROOMCONFIG_ROOMNAME_KEY);
	}

	/**
	 * Method description
	 *
	 *
	 * @return
	 */
	public boolean isChangeSubject() {
		return asBoolean(form.getAsBoolean(MUC_ROOMCONFIG_CHANGESUBJECT_KEY), false);
	}

	/**
	 * Method description
	 *
	 *
	 * @return
	 */
	public boolean isLoggingEnabled() {
		return asBoolean(form.getAsBoolean(MUC_ROOMCONFIG_ENABLELOGGING_KEY), false);
	}

	/**
	 * Method description
	 *
	 *
	 * @return
	 */
	public boolean isPasswordProtectedRoom() {
		return asBoolean(form.getAsBoolean(MUC_ROOMCONFIG_PASSWORDPROTECTEDROOM_KEY), false);
	}

	/**
	 * Method description
	 *
	 *
	 * @return
	 */
	public boolean isPersistentRoom() {
		return asBoolean(form.getAsBoolean(MUC_ROOMCONFIG_PERSISTENTROOM_KEY), false);
	}

	/**
	 * Method description
	 *
	 *
	 * @return
	 */
	public boolean isRoomMembersOnly() {
		return asBoolean(form.getAsBoolean(MUC_ROOMCONFIG_MEMBERSONLY_KEY), false);
	}

	/**
	 * Method description
	 *
	 *
	 * @return
	 */
	public boolean isRoomModerated() {
		return asBoolean(form.getAsBoolean(MUC_ROOMCONFIG_MODERATEDROOM_KEY), false);
	}

	/**
	 * Make Room Publicly Searchable
	 *
	 * @return
	 */
	public boolean isRoomconfigPublicroom() {
		return asBoolean(form.getAsBoolean(MUC_ROOMCONFIG_PUBLICROOM_KEY), true);
	}

	//~--- methods --------------------------------------------------------------

	/**
	 * Method description
	 *
	 *
	 * @param repository
	 * @param config
	 * @param subnode
	 *
	 * @throws TigaseDBException
	 * @throws UserNotFoundException
	 */
	public void read(final UserRepository repository, final MucConfig config,
			final String subnode)
			throws UserNotFoundException, TigaseDBException {
		String[] keys = repository.getKeys(config.getServiceBareJID(), subnode);

		if (keys != null) {
			for (String key : keys) {
				String[] values = repository.getDataList(config.getServiceBareJID(), subnode, key);

				setValues(key, values);
			}
		}
	}

	/**
	 * Method description
	 *
	 *
	 * @param listener
	 */
	public void removeListener(RoomConfigListener listener) {
		this.listeners.remove(listener);
	}

	/**
	 * Method description
	 *
	 *
	 * @param repo
	 * @param config
	 * @param subnode
	 *
	 * @throws TigaseDBException
	 * @throws UserNotFoundException
	 */
	public void write(final UserRepository repo, final MucConfig config, final String subnode)
			throws UserNotFoundException, TigaseDBException {
		List<Field> fields = form.getAllFields();

		for (Field field : fields) {
			if ((field.getVar() != null) &&!this.blacklist.contains(field.getVar())) {
				String[] values = field.getValues();
				String value = field.getValue();

				if ((values == null) || (values.length == 0)) {
					repo.removeData(config.getServiceBareJID(), subnode, field.getVar());
				} else {
					if (values.length == 1) {
						repo.setData(config.getServiceBareJID(), subnode, field.getVar(), value);
					} else {
						repo.setDataList(config.getServiceBareJID(), subnode, field.getVar(), values);
					}
				}
			}
		}
	}

	protected void init() {
		form.addField(Field.fieldTextSingle(MUC_ROOMCONFIG_ROOMNAME_KEY, "",
				"Natural-Language Room Name"));
		form.addField(Field.fieldTextSingle(MUC_ROOMCONFIG_ROOMDESC_KEY, "",
				"Short Description of Room"));
		form.addField(Field.fieldBoolean(MUC_ROOMCONFIG_PERSISTENTROOM_KEY, Boolean.FALSE,
				"Make Room Persistent?"));
		form.addField(Field.fieldBoolean(MUC_ROOMCONFIG_PUBLICROOM_KEY, Boolean.TRUE,
				"Make Room Publicly Searchable?"));
		form.addField(Field.fieldBoolean(MUC_ROOMCONFIG_MODERATEDROOM_KEY, Boolean.FALSE,
				"Make Room Moderated?"));
		form.addField(Field.fieldBoolean(MUC_ROOMCONFIG_MEMBERSONLY_KEY, Boolean.FALSE,
				"Make Room Members Only?"));
		form.addField(Field.fieldBoolean(MUC_ROOMCONFIG_PASSWORDPROTECTEDROOM_KEY, Boolean.FALSE,
				"Password Required to Enter?"));
		form.addField(Field.fieldTextSingle(MUC_ROOMCONFIG_ROOMSECRET_KEY, "", "Password"));
		form.addField(Field.fieldListSingle(MUC_ROOMCONFIG_ANONYMITY_KEY,
				Anonymity.semianonymous.name(), "Room anonymity level:",
					new String[] { "Non-Anonymous Room",
				"Semi-Anonymous Room", "Fully-Anonymous Room" }, new String[] {
				Anonymity.nonanonymous.name(),
				Anonymity.semianonymous.name(), Anonymity.fullanonymous.name() }));
		form.addField(Field.fieldBoolean(MUC_ROOMCONFIG_CHANGESUBJECT_KEY, Boolean.FALSE,
				"Allow Occupants to Change Subject?"));
		form.addField(Field.fieldBoolean(MUC_ROOMCONFIG_ENABLELOGGING_KEY, Boolean.FALSE,
				"Enable Public Logging?"));
		form.addField(Field.fieldListSingle(LOGGING_FORMAT_KEY, LogFormat.html.name(),
				"Logging format:", new String[] { "HTML",
				"Plain text" }, new String[] { LogFormat.html.name(), LogFormat.plain.name() }));
	}

	private boolean asBoolean(Boolean value, boolean defaultValue) {
		return (value == null) ? defaultValue : value.booleanValue();
	}

	private String asString(String value, String defaultValue) {
		return (value == null) ? defaultValue : value;
	}

	private Set<String> equals(Form form) {
		final HashSet<String> result = new HashSet<String>();

		/*
		 * for (Field field : this.form.getAllFields()) { Field of =
		 * form.get(field.getVar()); if (of == null) {
		 * result.add(field.getVar()); } else { boolean tmp =
		 * Arrays.equals(field.getValues(), of.getValues()); if (!tmp)
		 * result.add(field.getVar()); } }
		 */
		for (Field field : form.getAllFields()) {
			Field of = this.form.get(field.getVar());

			if (of == null) {
				result.add(field.getVar());
			} else {
				boolean tmp = Arrays.equals(field.getValues(), of.getValues());

				if ( !tmp) {
					result.add(field.getVar());
				}
			}
		}

		return result;
	}

	private void fireConfigChanged(final Set<String> modifiedVars) {
		for (RoomConfigListener listener : this.listeners) {
			listener.onConfigChanged(this, modifiedVars);
		}
	}

	//~--- set methods ----------------------------------------------------------

	private void setValue(String var, Object data) {
		Field f = form.get(var);

		if (f == null) {
			return;
		} else {
			if (data == null) {
				f.setValues(new String[] {});
			} else {
				if (data instanceof String) {
					String str = (String) data;

					if ((f.getType() == FieldType.bool) &&!"0".equals(str) &&!"1".equals(str)) {
						throw new RuntimeException("Boolean fields allows only '1' or '0' values");
					}

					f.setValues(new String[] { str });
				} else {
					if ((data instanceof Boolean) && (f.getType() == FieldType.bool)) {
						boolean b = ((Boolean) data).booleanValue();

						f.setValues(new String[] { b ? "1" : "0" });
					} else {
						if ((data instanceof String[])
								&& ((f.getType() == FieldType.list_multi)
									|| (f.getType() == FieldType.text_multi))) {
							String[] d = (String[]) data;

							f.setValues(d);
						} else {
							throw new RuntimeException("Cannot match type "
									+ data.getClass().getCanonicalName() + " to field type "
										+ f.getType().name());
						}
					}
				}
			}
		}
	}

	private void setValues(String var, String[] data) {
		if ((data == null) || (data.length > 1)) {
			setValue(var, data);
		} else {
			if (data.length == 0) {
				setValue(var, null);
			} else {
				setValue(var, data[0]);
			}
		}
	}

	//~--- inner interfaces -----------------------------------------------------

	/**
	 * Interface description
	 *
	 *
	 * @version        5.0.0, 2010.03.27 at 05:04:46 GMT
	 * @author         Artur Hefczyc <artur.hefczyc@tigase.org>
	 */
	public static interface RoomConfigListener {

		/**
		 * @param room
		 *            TODO
		 * @param modifiedVars
		 */
		void onConfigChanged(RoomConfig roomConfig, Set<String> modifiedVars);
	}
}


//~ Formatted in Sun Code Convention


//~ Formatted by Jindent --- http://www.jindent.com
