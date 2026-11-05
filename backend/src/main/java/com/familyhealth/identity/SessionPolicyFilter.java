package com.familyhealth.identity;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Ends sessions that Spring Session alone would keep: those past the absolute lifetime (it only
 * tracks idle time), and those whose account has since been disabled or removed. The request
 * then continues signed out.
 */
class SessionPolicyFilter extends OncePerRequestFilter {

	private final UserAccountRepository accounts;

	private final Clock clock;

	private final Duration absoluteTimeout;

	SessionPolicyFilter(UserAccountRepository accounts, Clock clock, Duration absoluteTimeout) {
		this.accounts = accounts;
		this.clock = clock;
		this.absoluteTimeout = absoluteTimeout;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		HttpSession session = request.getSession(false);
		if (session != null && (isTooOld(session) || accountIsGone())) {
			session.invalidate();
			SecurityContextHolder.clearContext();
		}
		chain.doFilter(request, response);
	}

	private boolean isTooOld(HttpSession session) {
		return clock.millis() - session.getCreationTime() > absoluteTimeout.toMillis();
	}

	private boolean accountIsGone() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		return authentication != null && authentication.getPrincipal() instanceof AccountPrincipal principal
				&& !accounts.existsByIdAndStatus(principal.accountId(), AccountStatus.ACTIVE);
	}

}
