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
package tigase.muc;

import tigase.db.TigaseDBException;
import tigase.db.UserNotFoundException;
import tigase.db.UserRepository;
import tigase.form.Field;
import tigase.form.Field.FieldType;
import tigase.form.Form;
import tigase.xmpp.BareJID;

import java.util.*;

/**
 * @author bmalkow
 */
public class RoomConfig {

	public static final String MUC_ROOMCONFIG_ANONYMITY_KEY = "muc#roomconfig_anonymity";
	public static final String MUC_ROOMCONFIG_CHANGESUBJECT_KEY = "muc#roomconfig_changesubject";
	public static final String MUC_ROOMCONFIG_ENABLELOGGING_KEY = "muc#roomconfig_enablelogging";
	public static final String MUC_ROOMCONFIG_MAXHISTORY_KEY = "muc#maxhistoryfetch";
	public static final String MUC_ROOMCONFIG_MAXUSERS_KEY = "muc#roomconfig_maxusers";
	public static final String MUC_ROOMCONFIG_MEMBERSONLY_KEY = "muc#roomconfig_membersonly";
	public static final String MUC_ROOMCONFIG_ALLOWINVITES_KEY = "muc#roomconfig_allowinvites";
	public static final String MUC_ROOMCONFIG_MODERATEDROOM_KEY = "muc#roomconfig_moderatedroom";
	public static final String MUC_ROOMCONFIG_PASSWORDPROTECTEDROOM_KEY = "muc#roomconfig_passwordprotectedroom";
	public static final String MUC_ROOMCONFIG_PERSISTENTROOM_KEY = "muc#roomconfig_persistentroom";
	public static final String MUC_ROOMCONFIG_PUBLICROOM_KEY = "muc#roomconfig_publicroom";
	public static final String MUC_ROOMCONFIG_ROOMDESC_KEY = "muc#roomconfig_roomdesc";
	public static final String MUC_ROOMCONFIG_ROOMNAME_KEY = "muc#roomconfig_roomname";
	public static final String MUC_ROOMCONFIG_ROOMSECRET_KEY = "muc#roomconfig_roomsecret";
	public static final String TIGASE_ROOMCONFIG_PRESENCE_FILTERING = "tigase#presence_filtering";
	public static final String TIGASE_ROOMCONFIG_PRESENCE_FILTERED_AFFILIATIONS = "tigase#presence_filtered_affiliations";
	public static final String TIGASE_ROOMCONFIG_PRESENCE_DELIVERY_LOGIC = "tigase#presence_delivery_logic";
	public static final String TIGASE_ROOMCONFIG_WELCOME_MESSAGES = "tigase#welcome_messages";
	private static final String LOGGING_FORMAT_KEY = "logging_format";

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

	public static enum LogFormat {
		html,
		plain,
		xml
	}

	protected final Set<String> blacklist = new HashSet<String>();
	protected final Form form = new Form("form", null, null);
	private final ArrayList<RoomConfigListener> listeners = new ArrayList<RoomConfigListener>();
	private final BareJID roomJID;

	protected static <T extends Enum<T>> List<T> asEnum(Class<T> clazz, String[] values, Enum<?>[] defaultValues) {
		List<T> list = new ArrayList<>();
		if (values != null && values.length > 0) {
			for (String val : values) {
				list.add(Enum.valueOf(clazz, val));
			}
		} else if (null != defaultValues) {
			list.addAll((Collection<? extends T>) Arrays.asList(defaultValues));
		}
		return list;
	}

	protected static String[] asStringTable(Enum<?>[] values) {
		String[] result = new String[values.length];
		int i = 0;
		for (Enum<?> v : values) {
			result[i++] = v.name();
		}
		return result;
	}

	/**
	 * @param roomJID
	 */
	public RoomConfig(BareJID roomJID) {
		this.roomJID = roomJID;
		init();
	}

	public void addListener(RoomConfigListener listener) {
		this.listeners.add(listener);
	}

	private boolean asBoolean(Boolean value, boolean defaultValue) {
		return value == null ? defaultValue : value.booleanValue();
	}

	private String asString(String value, String defaultValue) {
		return value == null ? defaultValue : value;
	}

	@Override
	public RoomConfig clone() {
		final RoomConfig rc = new RoomConfig(getRoomJID());
		rc.blacklist.addAll(this.blacklist);
		rc.form.copyValuesFrom(form);
		return rc;
	}

	public String[] compareTo(RoomConfig oldConfig) {
		Set<String> result = new HashSet<String>();

		Set<String> vars = equals(oldConfig.form);
		for (String var : vars) {
			if ((MUC_ROOMCONFIG_ANONYMITY_KEY).equals(var)) {
				switch (getRoomAnonymity()) {
					case nonanonymous:
						result.add("172");
						break;
					case semianonymous:
						result.add("173");
						break;
					case fullanonymous:
						result.add("174");
						break;
				}
			} else if ((MUC_ROOMCONFIG_ENABLELOGGING_KEY).equals(var)) {
				result.add(isLoggingEnabled() ? "170" : "171");
			} else {
				result.add("104");

			}
		}

		return result.size() == 0 ? null : result.toArray(new String[]{});
	}

	public void copyFrom(Form configForm) {
		copyFrom(configForm, true);
	}

	/**
	 * @param form2
	 */
	public void copyFrom(Form configForm, boolean fireEvents) {
		final Set<String> modifiedVars = fireEvents ? equals(configForm) : null;
		form.copyValuesFrom(configForm);
		if (modifiedVars != null && modifiedVars.size() > 0) {
			fireConfigChanged(modifiedVars);
		}
	}

	public void copyFrom(RoomConfig c) {
		copyFrom(c.form, true);
	}

	/**
	 * @param defaultRoomConfig
	 * @param b
	 */
	public void copyFrom(RoomConfig c, boolean fireEvents) {
		copyFrom(c.form, fireEvents);
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
				if (!tmp) {
					result.add(field.getVar());
				}
			}
		}

		return result;
	}

	private void fireConfigChanged(final Set<String> modifiedVars, boolean initialConfigUpdate) {
		for (RoomConfigListener listener : this.listeners) {
			if (!initialConfigUpdate) {
				listener.onConfigChanged(this, modifiedVars);
			} else {
				listener.onInitialRoomConfig(this);
			}
		}
	}

	private void fireConfigChanged(final Set<String> modifiedVars) {
		fireConfigChanged(modifiedVars, false);
	}

	public Form getConfigForm() {
		return form;
	}

	public LogFormat getLoggingFormat() {
		try {
			String tmp = form.getAsString(LOGGING_FORMAT_KEY);
			return tmp == null ? LogFormat.html : LogFormat.valueOf(tmp);
		} catch (Exception e) {
			return LogFormat.html;
		}
	}

	public Integer getMaxHistory() {
		try {
			return form.getAsInteger(MUC_ROOMCONFIG_MAXHISTORY_KEY);
		} catch (Exception e) {
			return 50;
		}
	}

	public Integer getMaxUsers() {
		try {
			String v = form.getAsString(MUC_ROOMCONFIG_MAXUSERS_KEY);
			if (v == null || v.isEmpty()) {
				return null;
			}
			return Integer.valueOf(v);
		} catch (Exception e) {
			return null;
		}
	}

	public String getPassword() {
		return asString(form.getAsString(MUC_ROOMCONFIG_ROOMSECRET_KEY), "");
	}

	public PresenceStore.PresenceDeliveryLogic getPresenceDeliveryLogic() {
		String PDLasString = form.getAsString(TIGASE_ROOMCONFIG_PRESENCE_DELIVERY_LOGIC);
		PresenceStore.PresenceDeliveryLogic pdl = PresenceStore.PresenceDeliveryLogic.valueOf(PDLasString);
		return pdl;
	}

	public Collection<Affiliation> getPresenceFilteredAffiliations() {
		String[] presenceFrom = form.getAsStrings(TIGASE_ROOMCONFIG_PRESENCE_FILTERED_AFFILIATIONS);
		return asEnum(Affiliation.class, presenceFrom, null);
	}

	public Anonymity getRoomAnonymity() {
		try {
			String tmp = form.getAsString(MUC_ROOMCONFIG_ANONYMITY_KEY);
			return tmp == null ? Anonymity.semianonymous : Anonymity.valueOf(tmp);
		} catch (Exception e) {
			return Anonymity.semianonymous;
		}
	}

	public String getRoomDesc() {
		return form.getAsString(MUC_ROOMCONFIG_ROOMDESC_KEY);
	}

	public BareJID getRoomJID() {
		return roomJID;
	}

	public String getRoomName() {
		return form.getAsString(MUC_ROOMCONFIG_ROOMNAME_KEY);
	}

	protected void init() {
		form.addField(Field.fieldTextSingle(MUC_ROOMCONFIG_ROOMNAME_KEY, "", "Natural-Language Room Name"));
		form.addField(Field.fieldTextSingle(MUC_ROOMCONFIG_ROOMDESC_KEY, "", "Short Description of Room"));
		form.addField(Field.fieldBoolean(MUC_ROOMCONFIG_PERSISTENTROOM_KEY, Boolean.FALSE, "Make Room Persistent?"));
		form.addField(
				Field.fieldBoolean(MUC_ROOMCONFIG_PUBLICROOM_KEY, Boolean.TRUE, "Make Room Publicly Searchable?"));
		form.addField(Field.fieldBoolean(MUC_ROOMCONFIG_MODERATEDROOM_KEY, Boolean.FALSE, "Make Room Moderated?"));
		form.addField(Field.fieldBoolean(MUC_ROOMCONFIG_MEMBERSONLY_KEY, Boolean.FALSE, "Make Room Members Only?"));
		form.addField(
				Field.fieldBoolean(MUC_ROOMCONFIG_ALLOWINVITES_KEY, Boolean.TRUE, "Allow Occupants to Invite Others?"));
		form.addField(Field.fieldBoolean(MUC_ROOMCONFIG_PASSWORDPROTECTEDROOM_KEY, Boolean.FALSE,
										 "Password Required to Enter?"));
		form.addField(Field.fieldTextSingle(MUC_ROOMCONFIG_ROOMSECRET_KEY, "", "Password"));

		form.addField(Field.fieldListSingle(MUC_ROOMCONFIG_ANONYMITY_KEY, Anonymity.semianonymous.name(),
											"Room anonymity level:",
											new String[]{"Non-Anonymous Room", "Semi-Anonymous Room",
														 "Fully-Anonymous Room"},
											new String[]{Anonymity.nonanonymous.name(), Anonymity.semianonymous.name(),
														 Anonymity.fullanonymous.name()}));
		form.addField(Field.fieldBoolean(MUC_ROOMCONFIG_CHANGESUBJECT_KEY, Boolean.FALSE,
										 "Allow Occupants to Change Subject?"));

		form.addField(Field.fieldBoolean(MUC_ROOMCONFIG_ENABLELOGGING_KEY, Boolean.FALSE, "Enable Public Logging?"));
		form.addField(Field.fieldListSingle(LOGGING_FORMAT_KEY, LogFormat.html.name(), "Logging format:",
											new String[]{"HTML", "Plain text"},
											new String[]{LogFormat.html.name(), LogFormat.plain.name()}));

		form.addField(Field.fieldTextSingle(MUC_ROOMCONFIG_MAXHISTORY_KEY, "50",
											"Maximum Number of History Messages Returned by Room"));

		form.addField(Field.fieldListSingle(MUC_ROOMCONFIG_MAXUSERS_KEY, "", "Maximum Number of Occupants",
											new String[]{"10", "20", "30", "50", "100", "None"},
											new String[]{"10", "20", "30", "50", "100", ""}));

		form.addField(Field.fieldListSingle(TIGASE_ROOMCONFIG_PRESENCE_DELIVERY_LOGIC,
											PresenceStore.PresenceDeliveryLogic.PREFERE_PRIORITY.toString(),
											"Presence delivery logic",
											asStringTable(PresenceStore.PresenceDeliveryLogic.values()),
											asStringTable(PresenceStore.PresenceDeliveryLogic.values())));

		form.addField(Field.fieldBoolean(TIGASE_ROOMCONFIG_PRESENCE_FILTERING, Boolean.FALSE,
										 "Enable filtering of presence (broadcasting presence only between selected groups"));

		form.addField(Field.fieldListMulti(TIGASE_ROOMCONFIG_PRESENCE_FILTERED_AFFILIATIONS, null,
										   "Affiliations for which presence should be delivered",
										   asStringTable(Affiliation.values()), asStringTable(Affiliation.values())));
		form.addField(Field.fieldBoolean(TIGASE_ROOMCONFIG_WELCOME_MESSAGES, Boolean.TRUE,
										 "Send welcome messages " + "on room creation"));
	}

	public boolean isChangeSubject() {
		return asBoolean(form.getAsBoolean(MUC_ROOMCONFIG_CHANGESUBJECT_KEY), false);
	}

	public boolean isInvitingAllowed() {
		return asBoolean(form.getAsBoolean(MUC_ROOMCONFIG_ALLOWINVITES_KEY), true);
	}

	public boolean isLoggingEnabled() {
		return asBoolean(form.getAsBoolean(MUC_ROOMCONFIG_ENABLELOGGING_KEY), false);
	}

	public boolean isPasswordProtectedRoom() {
		return asBoolean(form.getAsBoolean(MUC_ROOMCONFIG_PASSWORDPROTECTEDROOM_KEY), false);
	}

	public boolean isPersistentRoom() {
		return asBoolean(form.getAsBoolean(MUC_ROOMCONFIG_PERSISTENTROOM_KEY), false);
	}

	public boolean isPresenceFilterEnabled() {
		return asBoolean(form.getAsBoolean(TIGASE_ROOMCONFIG_PRESENCE_FILTERING), false);
	}

	public boolean isRoomMembersOnly() {
		return asBoolean(form.getAsBoolean(MUC_ROOMCONFIG_MEMBERSONLY_KEY), false);
	}

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

	public boolean isWelcomeMessageEnabled() {
		return asBoolean(form.getAsBoolean(TIGASE_ROOMCONFIG_WELCOME_MESSAGES), true);
	}

	public void notifyConfigUpdate(boolean initialConfigUpdate) {
		HashSet<String> vars = new HashSet<String>();
		for (Field f : form.getAllFields()) {
			vars.add(f.getVar());
		}
		fireConfigChanged(vars, initialConfigUpdate);
	}

	public void notifyConfigUpdate() {
		notifyConfigUpdate(false);
	}

	public void read(final UserRepository repository, final MucContext config, final String subnode)
			throws UserNotFoundException, TigaseDBException {
		String[] keys = repository.getKeys(config.getServiceName(), subnode);
		if (keys != null) {
			for (String key : keys) {
				String[] values = repository.getDataList(config.getServiceName(), subnode, key);
				setValues(key, values);
			}
		}
	}

	public void removeListener(RoomConfigListener listener) {
		this.listeners.remove(listener);
	}

	public void setValue(String var, Object data) {
		Field f = form.get(var);

		if (f == null) {
			return;
		} else if (data == null) {
			f.setValues(new String[]{});
		} else if (data instanceof String) {
			String str = (String) data;
			if (f.getType() == FieldType.bool && !"0".equals(str) && !"1".equals(str) &&
					!"true".equalsIgnoreCase(str) && !"false".equalsIgnoreCase(str)) {
				throw new RuntimeException("Boolean fields allows only '1', 'true', '0', 'false' values");
			}
			f.setValues(new String[]{str});
		} else if (data instanceof Boolean && f.getType() == FieldType.bool) {
			boolean b = ((Boolean) data).booleanValue();
			f.setValues(new String[]{b ? "1" : "0"});
		} else if (data instanceof String[] &&
				(f.getType() == FieldType.list_multi || f.getType() == FieldType.text_multi)) {
			String[] d = (String[]) data;
			f.setValues(d);
		} else {
			throw new RuntimeException(
					"Cannot match type " + data.getClass().getCanonicalName() + " to field type " + f.getType().name());
		}

	}

	public void setValues(String var, String[] data) {
		if (data == null || data.length > 1) {
			setValue(var, data);
		} else if (data.length == 0) {
			setValue(var, null);
		} else {
			setValue(var, data[0]);
		}
	}

	public void write(final UserRepository repo, final MucContext config, final String subnode)
			throws UserNotFoundException, TigaseDBException {
		List<Field> fields = form.getAllFields();
		for (Field field : fields) {
			if (field.getVar() != null && !this.blacklist.contains(field.getVar())) {
				String[] values = field.getValues();
				String value = field.getValue();
				if (values == null || values.length == 0) {
					repo.removeData(config.getServiceName(), subnode, field.getVar());
				} else if (values.length == 1) {
					repo.setData(config.getServiceName(), subnode, field.getVar(), value);
				} else {
					repo.setDataList(config.getServiceName(), subnode, field.getVar(), values);
				}
			}
		}
	}

	public static interface RoomConfigListener {

		void onConfigChanged(RoomConfig roomConfig, Set<String> modifiedVars);

		void onInitialRoomConfig(RoomConfig roomConfig);
	}

}
