package com.familyhealth.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.NoSuchFileException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Against a local stand-in that answers like S3, so the SDK's real requests are checked: path-style
 * addresses, signed requests, and no checksum trailers (which some S3-compatible services reject).
 * The real bucket is checked by the smoke test after deployment.
 */
class S3DocumentStorageTest {

	private static final String KEY = "a".repeat(43);

	record Received(String method, String path, Map<String, List<String>> headers) {
	}

	private final Map<String, byte[]> objects = new ConcurrentHashMap<>();

	private final List<Received> received = new CopyOnWriteArrayList<>();

	private volatile int forcedStatus;

	private HttpServer server;

	private S3DocumentStorage storage;

	@BeforeEach
	void start() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/", this::handle);
		server.start();
		storage = new S3DocumentStorage("http://127.0.0.1:" + server.getAddress().getPort(), "auto", "demo-bucket",
				"test-access", "test-secret");
	}

	@AfterEach
	void stop() {
		server.stop(0);
	}

	private void handle(HttpExchange exchange) throws IOException {
		String path = exchange.getRequestURI().getPath();
		byte[] body = exchange.getRequestBody().readAllBytes();
		received.add(new Received(exchange.getRequestMethod(), path, Map.copyOf(exchange.getRequestHeaders())));
		if (forcedStatus != 0) {
			error(exchange, forcedStatus, "AccessDenied");
			return;
		}
		switch (exchange.getRequestMethod()) {
			case "PUT" -> {
				objects.put(path, body);
				exchange.getResponseHeaders().add("ETag", "\"etag\"");
				exchange.sendResponseHeaders(200, -1);
			}
			case "GET" -> {
				byte[] object = objects.get(path);
				if (object == null) {
					error(exchange, 404, "NoSuchKey");
					return;
				}
				exchange.sendResponseHeaders(200, object.length);
				exchange.getResponseBody().write(object);
			}
			case "DELETE" -> {
				objects.remove(path);
				exchange.sendResponseHeaders(204, -1);
			}
			case "HEAD" -> exchange.sendResponseHeaders(200, -1);
			default -> exchange.sendResponseHeaders(405, -1);
		}
		exchange.close();
	}

	private static void error(HttpExchange exchange, int status, String code) throws IOException {
		byte[] xml = ("<?xml version=\"1.0\" encoding=\"UTF-8\"?><Error><Code>" + code + "</Code><Message>m</Message></Error>")
			.getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().add("Content-Type", "application/xml");
		exchange.sendResponseHeaders(status, xml.length);
		exchange.getResponseBody().write(xml);
		exchange.close();
	}

	@Test
	void storesReadsAndDeletesAFileInThePrivateBucket() throws IOException {
		byte[] content = "synthetic report".getBytes(StandardCharsets.UTF_8);

		storage.store(KEY, new ByteArrayInputStream(content));
		try (InputStream read = storage.open(KEY)) {
			assertThat(read.readAllBytes()).isEqualTo(content);
		}
		storage.delete(KEY);

		assertThat(objects).isEmpty();
		assertThat(received).extracting(Received::path).containsOnly("/demo-bucket/" + KEY);
	}

	@Test
	void uploadIsSignedAndSentWithoutChecksumTrailers() throws IOException {
		storage.store(KEY, new ByteArrayInputStream(new byte[] { 1, 2, 3 }));

		Map<String, List<String>> headers = received.getFirst().headers();
		assertThat(headers.get("Authorization")).singleElement().asString().startsWith("AWS4-HMAC-SHA256");
		assertThat(headers.get("Content-length")).containsExactly("3");
		assertThat(headers).doesNotContainKeys("Content-encoding", "X-amz-trailer", "X-amz-sdk-checksum-algorithm");
	}

	@Test
	void missingFileIsReportedLikeLocalStorage() {
		assertThatThrownBy(() -> storage.open(KEY)).isInstanceOf(NoSuchFileException.class);
	}

	@Test
	void refusedRequestBecomesAnIOExceptionWithTheStatusOnly() {
		forcedStatus = 403;

		assertThatThrownBy(() -> storage.store(KEY, new ByteArrayInputStream(new byte[] { 1 })))
			.isInstanceOf(IOException.class)
			.hasMessageContaining("status 403")
			.hasMessageNotContaining(KEY);
		assertThatThrownBy(storage::checkBucket).isInstanceOf(IOException.class);
	}

	@Test
	void keyOutsideTheGeneratedFormatIsRejectedBeforeAnyRequest() {
		assertThatThrownBy(() -> storage.open("../other-bucket/x")).isInstanceOf(IllegalArgumentException.class);
		assertThat(received).isEmpty();
	}

	@Test
	void bucketWithoutEndpointOrKeysFailsAtStartup() {
		assertThatThrownBy(() -> new S3DocumentStorage("", "auto", "b", "k", "s")).hasMessageContaining("STORAGE_ENDPOINT");
		assertThatThrownBy(() -> new S3DocumentStorage("http://x", "auto", "b", "", "s"))
			.hasMessageContaining("STORAGE_ACCESS_KEY");
	}

}
