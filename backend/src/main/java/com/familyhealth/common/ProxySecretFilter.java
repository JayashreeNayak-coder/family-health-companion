package com.familyhealth.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.regex.Pattern;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * For hosting behind the Cloudflare Pages proxy (DECISIONS.md, D-033). The backend's own address is
 * public, so every request must carry the secret only the proxy knows. Only the health check, used
 * by the host's own probe, is exempt. The proxy also sends the visitor's address, which replaces
 * the connecting address (Cloudflare's) so rate limits apply per visitor, not to everyone at once.
 *
 * <p>Off when no secret is set: development, Docker Compose behind nginx, and tests.
 */
public class ProxySecretFilter extends OncePerRequestFilter {

	public static final String SECRET_HEADER = "X-Proxy-Secret";

	public static final String CLIENT_ADDRESS_HEADER = "X-Client-IP";

	static final String HEALTH_PATH = "/actuator/health";

	/** An IPv4 or IPv6 literal. Never a host name, so it can't trigger a DNS lookup. */
	private static final Pattern ADDRESS = Pattern.compile("(?=.*[.:])[0-9A-Fa-f:.]{2,45}");

	private final byte[] secret;

	/** An empty secret turns the filter off. */
	public ProxySecretFilter(String secret) {
		this.secret = secret.getBytes(StandardCharsets.UTF_8);
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return secret.length == 0;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String given = request.getHeader(SECRET_HEADER);
		if (given == null || !MessageDigest.isEqual(secret, given.getBytes(StandardCharsets.UTF_8))) {
			if (HEALTH_PATH.equals(request.getRequestURI())) {
				chain.doFilter(request, response);
				return;
			}
			response.setStatus(HttpServletResponse.SC_FORBIDDEN);
			response.setContentType("application/problem+json");
			response.getWriter()
				.write("{\"type\":\"about:blank\",\"title\":\"Forbidden\",\"status\":403,\"code\":\"PROXY_REQUIRED\","
						+ "\"detail\":\"Open the app through its public address\"}");
			return;
		}
		String client = request.getHeader(CLIENT_ADDRESS_HEADER);
		if (client == null || !ADDRESS.matcher(client).matches()) {
			chain.doFilter(request, response);
			return;
		}
		chain.doFilter(new HttpServletRequestWrapper(request) {

			@Override
			public String getRemoteAddr() {
				return client;
			}

			@Override
			public String getRemoteHost() {
				return client;
			}

		}, response);
	}

}
