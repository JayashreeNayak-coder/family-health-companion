package com.familyhealth.ai;

/**
 * A provider call that didn't produce usable output. The code is safe to store and log: it never
 * holds prompt or response text.
 */
public class AiException extends RuntimeException {

	public enum Code {

		/** No provider is configured, or its key was refused. */
		NOT_CONFIGURED(false),
		UNAVAILABLE(true),
		TIMEOUT(true),
		RATE_LIMITED(false),
		/** The provider refused to answer, for example on safety grounds. */
		BLOCKED(false),
		/** The output didn't match the schema. It is rejected, never repaired by guessing. */
		MALFORMED_RESPONSE(false);

		private final boolean retryable;

		Code(boolean retryable) {
			this.retryable = retryable;
		}

		public boolean retryable() {
			return retryable;
		}

	}

	private final Code code;

	public AiException(Code code, String message) {
		super(message);
		this.code = code;
	}

	public AiException(Code code, String message, Throwable cause) {
		super(message, cause);
		this.code = code;
	}

	public Code code() {
		return code;
	}

}
