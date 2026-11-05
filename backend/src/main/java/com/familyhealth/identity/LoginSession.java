package com.familyhealth.identity;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

/** Starts and ends the server-side session. The controller stays free of Spring Security details. */
@Component
class LoginSession {

	private final SessionAuthenticationStrategy sessionStrategy;

	private final SecurityContextRepository contextRepository;

	private final SecurityContextLogoutHandler logoutHandler = new SecurityContextLogoutHandler();

	LoginSession(SessionAuthenticationStrategy sessionStrategy, SecurityContextRepository contextRepository) {
		this.sessionStrategy = sessionStrategy;
		this.contextRepository = contextRepository;
	}

	/** Rotates the session ID and the CSRF token, then stores the signed-in account in the session. */
	void start(AccountPrincipal principal, HttpServletRequest request, HttpServletResponse response) {
		Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of());
		sessionStrategy.onAuthentication(authentication, request, response);

		SecurityContext context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(authentication);
		SecurityContextHolder.setContext(context);
		contextRepository.saveContext(context, request, response);
	}

	void end(Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
		logoutHandler.logout(request, response, authentication);
	}

}
