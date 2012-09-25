package tigase.component.exceptions;

import tigase.server.Packet;
import tigase.xmpp.Authorization;
import tigase.xmpp.PacketErrorTypeException;

public class ComponentException extends Exception {

	private static final long serialVersionUID = 1L;

	private Authorization errorCondition;

	private String text;

	public ComponentException(final Authorization errorCondition) {
		this(errorCondition, (String) null);
	}

	public ComponentException(Authorization errorCondition, String message) {
		this.errorCondition = errorCondition;
		this.text = message;
	}

	/**
	 * @return Returns the code.
	 */
	public String getCode() {
		return String.valueOf(this.errorCondition.getErrorCode());
	}

	public Authorization getErrorCondition() {
		return errorCondition;
	}

	@Override
	public String getMessage() {
		final StringBuilder sb = new StringBuilder();
		sb.append("[");
		sb.append(errorCondition.name()).append(" ");
		if (text != null) {
			sb.append("\"").append(text).append("\" ");
		}

		sb.append("]");
		return sb.toString();
	}

	/**
	 * @return Returns the name.
	 */
	public String getName() {
		return errorCondition.getCondition();
	}

	public String getText() {
		return text;
	}

	/**
	 * @return Returns the type.
	 */
	public String getType() {
		return errorCondition.getErrorType();
	}

	public Packet makeElement(Packet packet, boolean insertOriginal) throws PacketErrorTypeException {
		Packet result = errorCondition.getResponseMessage(packet, text, insertOriginal);
		return result;
	}

}
