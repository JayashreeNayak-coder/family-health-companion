package com.familyhealth;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import tools.jackson.databind.json.JsonMapper;

/**
 * Behaves like the browser and SPA: keeps the session cookie between requests and fetches a real
 * CSRF token before each write. Nothing in the security chain is bypassed.
 */
public final class ApiClient {

	public static final String PASSWORD = "synthetic-password-1";

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private static final AtomicInteger NEXT_ADDRESS = new AtomicInteger();

	private final MockMvc mockMvc;

	private final String address;

	private Cookie sessionCookie;

	public ApiClient(MockMvc mockMvc) {
		// A new address for each client, so one test's failed logins can't rate limit another's.
		this(mockMvc, nextAddress());
	}

	public ApiClient(MockMvc mockMvc, String address) {
		this.mockMvc = mockMvc;
		this.address = address;
	}

	private static String nextAddress() {
		int next = NEXT_ADDRESS.incrementAndGet();
		return "10.0." + (next / 250) + "." + (1 + next % 250);
	}

	public static String uniqueEmail() {
		return "user-" + UUID.randomUUID() + "@example.test";
	}

	public String sessionId() {
		return sessionCookie == null ? null : sessionCookie.getValue();
	}

	public MockHttpServletResponse get(String path) throws Exception {
		return send(MockMvcRequestBuilders.get(path));
	}

	public String csrfToken() throws Exception {
		return JsonPath.read(get("/api/v1/auth/csrf").getContentAsString(), "$.token");
	}

	public MockHttpServletResponse post(String path, Map<String, ?> body) throws Exception {
		return postRaw(path, JSON.writeValueAsString(body));
	}

	public MockHttpServletResponse put(String path, Map<String, ?> body) throws Exception {
		return send(MockMvcRequestBuilders.put(path)
			.contentType(MediaType.APPLICATION_JSON)
			.content(JSON.writeValueAsString(body))
			.header("X-CSRF-TOKEN", csrfToken()));
	}

	public MockHttpServletResponse patch(String path, Map<String, ?> body) throws Exception {
		return send(MockMvcRequestBuilders.patch(path)
			.contentType(MediaType.APPLICATION_JSON)
			.content(JSON.writeValueAsString(body))
			.header("X-CSRF-TOKEN", csrfToken()));
	}

	public MockHttpServletResponse delete(String path) throws Exception {
		return send(MockMvcRequestBuilders.delete(path).header("X-CSRF-TOKEN", csrfToken()));
	}

	/** A multipart upload, as the browser's FormData sends it. */
	public MockHttpServletResponse upload(String path, String filename, String claimedType, byte[] content,
			Map<String, String> fields) throws Exception {
		MockMultipartHttpServletRequestBuilder request = MockMvcRequestBuilders.multipart(path)
			.file(new MockMultipartFile("file", filename, claimedType, content));
		fields.forEach(request::param);
		request.header("X-CSRF-TOKEN", csrfToken());
		return send(request);
	}

	/** A multipart PUT, as the photo endpoints take. */
	public MockHttpServletResponse uploadPut(String path, String filename, String claimedType, byte[] content)
			throws Exception {
		MockMultipartHttpServletRequestBuilder request = MockMvcRequestBuilders
			.multipart(org.springframework.http.HttpMethod.PUT, path)
			.file(new MockMultipartFile("file", filename, claimedType, content));
		request.header("X-CSRF-TOKEN", csrfToken());
		return send(request);
	}

	/** Registers and signs in a new account, and returns its ID. */
	public String signUp(String email, String displayName) throws Exception {
		String id = JsonPath.read(register(email, PASSWORD, displayName).getContentAsString(), "$.id");
		login(email, PASSWORD);
		return id;
	}

	public MockHttpServletResponse postRaw(String path, String body) throws Exception {
		return postWithToken(path, body, csrfToken());
	}

	public MockHttpServletResponse postWithToken(String path, String body, String token) throws Exception {
		MockHttpServletRequestBuilder request = MockMvcRequestBuilders.post(path).contentType(MediaType.APPLICATION_JSON).content(body);
		if (token != null) {
			request.header("X-CSRF-TOKEN", token);
		}
		return send(request);
	}

	public MockHttpServletResponse register(String email, String password) throws Exception {
		return register(email, password, "Synthetic User");
	}

	public MockHttpServletResponse register(String email, String password, String displayName) throws Exception {
		return post("/api/v1/auth/register", Map.of("email", email, "password", password, "displayName", displayName));
	}

	public MockHttpServletResponse login(String email, String password) throws Exception {
		return post("/api/v1/auth/login", Map.of("email", email, "password", password));
	}

	public static String json(Map<String, String> body) {
		return JSON.writeValueAsString(body);
	}

	private MockHttpServletResponse send(AbstractMockHttpServletRequestBuilder<?> request) throws Exception {
		if (sessionCookie != null) {
			request.cookie(sessionCookie);
		}
		request.with(mock -> {
			mock.setRemoteAddr(address);
			return mock;
		});
		MockHttpServletResponse response = mockMvc.perform(request).andReturn().getResponse();
		Cookie updated = response.getCookie("SESSION");
		if (updated != null) {
			sessionCookie = updated.getMaxAge() == 0 ? null : updated;
		}
		return response;
	}

}
