package com.familyhealth.identity;

import jakarta.servlet.DispatcherType;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.web.servlet.HandlerExceptionResolver;

/** Everything requires sign-in except the listed public endpoints. See docs/06 section 2. */
@Configuration(proxyBeanMethods = false)
class SecurityConfig {

	@Bean
	PasswordEncoder passwordEncoder() {
		return PasswordEncoderFactories.createDelegatingPasswordEncoder();
	}

	@Bean
	SecurityContextRepository securityContextRepository() {
		return new HttpSessionSecurityContextRepository();
	}

	/** The token lives in the server-side session. It is never placed in a cookie the page can read. */
	@Bean
	CsrfTokenRepository csrfTokenRepository() {
		return new HttpSessionCsrfTokenRepository();
	}

	/** Applied at login: new session ID against fixation, and a new CSRF token. */
	@Bean
	SessionAuthenticationStrategy sessionAuthenticationStrategy(CsrfTokenRepository csrfTokens) {
		return new CompositeSessionAuthenticationStrategy(
				List.of(new ChangeSessionIdAuthenticationStrategy(), new CsrfAuthenticationStrategy(csrfTokens)));
	}

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, CsrfTokenRepository csrfTokens,
			SecurityContextRepository contexts, UserAccountRepository accounts, Clock clock, AuthService authService,
			@Value("${app.session.absolute-timeout}") Duration absoluteTimeout,
			@Qualifier("handlerExceptionResolver") HandlerExceptionResolver errors,
			ObjectProvider<ClientRegistrationRepository> googleRegistrations, GoogleSignInHandler googleHandler)
			throws Exception {
		ClientRegistrationRepository google = googleRegistrations.getIfAvailable();
		http
			.authorizeHttpRequests(requests -> {
				requests.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
					.requestMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/login",
							"/api/v1/auth/password-reset/request", "/api/v1/auth/password-reset/confirm")
					.permitAll()
					.requestMatchers(HttpMethod.GET, "/api/v1/auth/csrf", "/api/v1/auth/providers", "/actuator/health")
					.permitAll()
					// The emergency card's link is its own key: approved lines of an enabled card only (docs/06 section 6).
					.requestMatchers(HttpMethod.GET, "/api/v1/public/emergency-cards/*")
					.permitAll();
				if (google != null) {
					requests.requestMatchers(HttpMethod.GET, "/oauth2/authorization/google",
							GoogleSignInConfig.REDIRECT_PATH).permitAll();
				}
				requests.anyRequest().authenticated();
			})
			.csrf(csrf -> csrf.csrfTokenRepository(csrfTokens))
			.securityContext(context -> context.securityContextRepository(contexts))
			// A signed-out request must not create a session row just to remember where it was going.
			.requestCache(AbstractHttpConfigurer::disable)
			// AuthController handles logout, so it follows the same error and audit rules as everything else.
			.logout(AbstractHttpConfigurer::disable)
			.headers(headers -> headers
				.referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.NO_REFERRER))
				.contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'")))
			.exceptionHandling(handling -> handling
				.authenticationEntryPoint((request, response, ex) -> errors.resolveException(request, response, null, ex))
				.accessDeniedHandler(accessDeniedHandler(authService, errors)))
			.addFilterAfter(new SessionPolicyFilter(accounts, clock, absoluteTimeout), SecurityContextHolderFilter.class);
		if (google != null) {
			// Only when Google keys are set (D-028). The handler swaps Google's login for the app's own session.
			http.oauth2Login(login -> login.clientRegistrationRepository(google)
				.loginPage("/sign-in")
				.successHandler(googleHandler)
				.failureHandler(googleHandler));
		}
		return http.build();
	}

	/**
	 * Audits denials by signed-in accounts only. Anonymous CSRF failures are routine after a
	 * session expires, and auditing them would let anyone fill the table without signing in.
	 */
	private static AccessDeniedHandler accessDeniedHandler(AuthService authService, HandlerExceptionResolver errors) {
		return (request, response, ex) -> {
			Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
			if (authentication != null && authentication.getPrincipal() instanceof AccountPrincipal principal) {
				authService.recordDenied(principal, ex instanceof CsrfException ? "CSRF" : "FORBIDDEN");
			}
			errors.resolveException(request, response, null, ex);
		};
	}

}
