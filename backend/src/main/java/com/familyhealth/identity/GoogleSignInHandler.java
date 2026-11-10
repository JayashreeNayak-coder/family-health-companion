package com.familyhealth.identity;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

/**
 * Turns a finished Google sign-in into the app's own session, holding only the account ID like a
 * password login, then returns to the app. A refusal clears the session and returns to sign-in
 * with a reason code the page explains.
 */
@Component
class GoogleSignInHandler implements AuthenticationSuccessHandler, AuthenticationFailureHandler {

	private final GoogleAccounts googleAccounts;

	private final LoginSession loginSession;

	private final String publicUrl;

	GoogleSignInHandler(GoogleAccounts googleAccounts, LoginSession loginSession,
			@Value("${app.public-url}") String publicUrl) {
		this.googleAccounts = googleAccounts;
		this.loginSession = loginSession;
		this.publicUrl = publicUrl.endsWith("/") ? publicUrl.substring(0, publicUrl.length() - 1) : publicUrl;
	}

	@Override
	public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
			Authentication authentication) throws IOException {
		GoogleAccounts.Outcome outcome = authentication.getPrincipal() instanceof OidcUser user
				? googleAccounts.signIn(user.getSubject(), user.getEmail(), Boolean.TRUE.equals(user.getEmailVerified()),
						user.getFullName())
				: new GoogleAccounts.Refused(GoogleAccounts.Refusal.MISSING_DETAILS);
		switch (outcome) {
			case GoogleAccounts.SignedIn signedIn -> {
				loginSession.start(new AccountPrincipal(signedIn.accountId()), request, response);
				response.sendRedirect(publicUrl + "/");
			}
			case GoogleAccounts.Refused refused -> {
				loginSession.end(authentication, request, response);
				response.sendRedirect(signInWith(refused.reason().name()));
			}
		}
	}

	@Override
	public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException exception) throws IOException {
		response.sendRedirect(signInWith("FAILED"));
	}

	private String signInWith(String reason) {
		return publicUrl + "/sign-in?google=" + reason.toLowerCase(Locale.ROOT).replace('_', '-');
	}

}
