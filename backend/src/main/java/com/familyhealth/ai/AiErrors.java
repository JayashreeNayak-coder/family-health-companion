package com.familyhealth.ai;

import com.familyhealth.common.ApiException;
import org.springframework.http.HttpStatus;

/**
 * How a failed provider call is reported to a user waiting for an answer. Titles never quote the
 * request or the model's output. Manual workflows keep working in every case (docs/06 section 8.6).
 */
public final class AiErrors {

	private AiErrors() {
	}

	public static ApiException unavailable() {
		return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI_UNAVAILABLE",
				"AI suggestions are not available right now. Please try again later.");
	}

	public static ApiException toApi(AiException ex) {
		return switch (ex.code()) {
			case NOT_CONFIGURED, UNAVAILABLE, TIMEOUT, RATE_LIMITED -> unavailable();
			case BLOCKED -> new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "AI_DECLINED",
					"The AI service declined this request. Try different wording.");
			case MALFORMED_RESPONSE -> new ApiException(HttpStatus.BAD_GATEWAY, "AI_BAD_RESPONSE",
					"The AI service gave an answer that couldn't be used. Please try again.");
		};
	}

}
