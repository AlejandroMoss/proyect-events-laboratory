package com.alejandro.callbacks;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CallbackApiTest {
  static final String PASSWORD = UUID.randomUUID().toString();
  static final String USERNAME = "test-service";
  static final String BODY =
      """
      {"eventId":"evt-1","type":"payment.created",
       "occurredAt":"2026-01-01T00:00:00.123456789Z","payload":{"amount":10,"currency":"COP"}}
      """;

  @Value("${local.server.port}")
  int port;

  @Autowired JdbcTemplate jdbc;
  @Autowired JsonMapper mapper;
  final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("callbacks.security.username", () -> USERNAME);
    registry.add("callbacks.security.password", () -> PASSWORD);
    registry.add("spring.datasource.password", () -> PASSWORD);
  }

  @Test
  void rejectsValuesUnsupportedByPostgresWithoutSuggestingRetry() throws Exception {
    var nul = BODY.replace("\"currency\":\"COP\"", "\"currency\":\"\\u0000\"");
    var response = post("payments", nul);
    assertThat(response.statusCode()).isEqualTo(400);
    assertThat(response.headers().firstValue("Retry-After")).isEmpty();
    var future = BODY.replace("2026-01-01T00:00:00.123456789Z", "+10000-01-01T00:00:00Z");
    assertThat(post("payments", future).statusCode()).isEqualTo(400);
    assertThat(count()).isZero();
  }

  @BeforeEach
  void cleanDatabase() {
    // ServiceConnection always targets the disposable Testcontainers database.
    jdbc.execute("TRUNCATE TABLE callback_events");
  }

  @Test
  void authenticatesAndExposesOnlyPublicHealth() throws Exception {
    assertThat(send("GET", "/api/v1/events", null, false).statusCode()).isEqualTo(401);
    var request =
        HttpRequest.newBuilder(url("/api/v1/events"))
            .header("Authorization", basic(USERNAME, UUID.randomUUID().toString()))
            .GET()
            .build();
    assertThat(client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode())
        .isEqualTo(401);
    assertThat(send("GET", "/actuator/health", null, false).statusCode()).isEqualTo(200);
    assertThat(send("GET", "/actuator/health/readiness", null, false).statusCode()).isEqualTo(200);
    assertThat(send("GET", "/actuator/metrics", null, false).statusCode()).isEqualTo(401);
    assertThat(send("GET", "/actuator/metrics", null, true).statusCode()).isEqualTo(200);
  }

  @Test
  void storesAndRetrievesEventAfterCommit() throws Exception {
    var response = post("payments", BODY);
    assertThat(response.statusCode()).isEqualTo(201);
    assertThat(response.headers().firstValue("Set-Cookie")).isEmpty();
    var body = json(response);
    assertThat(body.path("duplicate").asBoolean()).isFalse();
    assertThat(body.path("event").path("payload").path("amount").asInt()).isEqualTo(10);
    assertThat(body.path("event").path("occurredAt").asText())
        .isEqualTo("2026-01-01T00:00:00.123456Z");
    var get = send("GET", response.headers().firstValue("Location").orElseThrow(), null, true);
    assertThat(get.statusCode()).isEqualTo(200);
    assertThat(json(get)).isEqualTo(body.path("event"));
    assertThat(count()).isEqualTo(1);
  }

  @Test
  void acceptsSemanticJsonRetryWithoutDuplicating() throws Exception {
    var first = json(post("payments", BODY));
    var retry =
        post(
            "payments",
            BODY.replace(
                "\"amount\":10,\"currency\":\"COP\"", "\"currency\":\"COP\",\"amount\":10.00"));
    assertThat(retry.statusCode()).isEqualTo(200);
    assertThat(json(retry).path("duplicate").asBoolean()).isTrue();
    assertThat(json(retry).path("event").path("id")).isEqualTo(first.path("event").path("id"));
    assertThat(count()).isEqualTo(1);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "\"amount\":11",
        "\"type\":\"payment.failed\"",
        "\"occurredAt\":\"2026-01-02T00:00:00Z\""
      })
  void rejectsChangedContent(String replacement) throws Exception {
    post("payments", BODY);
    String changed =
        replacement.startsWith("\"amount\"")
            ? BODY.replace("\"amount\":10", replacement)
            : replacement.startsWith("\"type\"")
                ? BODY.replace("\"type\":\"payment.created\"", replacement)
                : BODY.replace("\"occurredAt\":\"2026-01-01T00:00:00.123456789Z\"", replacement);
    var response = post("payments", changed);
    assertThat(response.statusCode()).isEqualTo(409);
    assertThat(response.headers().firstValue("Content-Type").orElseThrow())
        .contains("application/problem+json");
    assertThat(count()).isEqualTo(1);
  }

  @Test
  void scopesIdentifiersBySource() throws Exception {
    assertThat(post("payments", BODY).statusCode()).isEqualTo(201);
    assertThat(post("orders", BODY).statusCode()).isEqualTo(201);
    assertThat(count()).isEqualTo(2);
  }

  @Test
  void handlesConcurrentRetriesAtomically() throws Exception {
    int requests = 8;
    var gate = new CountDownLatch(1);
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var futures =
          IntStream.range(0, requests)
              .mapToObj(
                  i ->
                      executor.submit(
                          (Callable<Integer>)
                              () -> {
                                gate.await();
                                return post("payments", BODY).statusCode();
                              }))
              .toList();
      gate.countDown();
      var statuses = new java.util.ArrayList<Integer>();
      for (var future : futures) {
        statuses.add(future.get(30, java.util.concurrent.TimeUnit.SECONDS));
      }
      assertThat(statuses).containsOnly(200, 201);
      assertThat(statuses.stream().filter(status -> status == 201).count()).isEqualTo(1);
      assertThat(count()).isEqualTo(1);
    }
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{}",
        "{\"eventId\":\"a\",\"type\":\"b\",\"occurredAt\":\"2026-01-01T00:00:00Z\",\"payload\":[]}",
        "{\"eventId\":\"a\",\"type\":\"b\",\"occurredAt\":\"2026-01-01T00:00:00Z\",\"payload\":null}",
        "{\"eventId\":\" \",\"type\":\"b\",\"occurredAt\":\"invalid\",\"payload\":{}}",
        "{invalid-json"
      })
  void rejectsInvalidBodies(String body) throws Exception {
    assertThat(post("payments", body).statusCode()).isEqualTo(400);
    assertThat(count()).isZero();
  }

  @Test
  void validatesSourceAndQueryParameters() throws Exception {
    assertThat(post("INVALID", BODY).statusCode()).isEqualTo(400);
    assertThat(send("GET", "/api/v1/events?size=101", null, true).statusCode()).isEqualTo(400);
    assertThat(send("GET", "/api/v1/events?page=-1", null, true).statusCode()).isEqualTo(400);
    assertThat(send("GET", "/api/v1/events?page=10001", null, true).statusCode()).isEqualTo(400);
    assertThat(send("GET", "/api/v1/events?source=INVALID", null, true).statusCode())
        .isEqualTo(400);
    assertThat(send("GET", "/api/v1/events/not-a-uuid", null, true).statusCode()).isEqualTo(400);
    assertThat(send("GET", "/api/v1/events/" + UUID.randomUUID(), null, true).statusCode())
        .isEqualTo(404);
  }

  @Test
  void filtersAndPaginates() throws Exception {
    post("payments", BODY);
    post("payments", BODY.replace("evt-1", "evt-2"));
    post("orders", BODY.replace("payment.created", "order.created"));
    var first = json(send("GET", "/api/v1/events?source=payments&size=1", null, true));
    assertThat(first.path("items").size()).isEqualTo(1);
    assertThat(first.path("hasNext").asBoolean()).isTrue();
    var second = json(send("GET", "/api/v1/events?source=payments&size=1&page=1", null, true));
    assertThat(second.path("hasNext").asBoolean()).isFalse();
    assertThat(second.path("items").get(0).path("id"))
        .isNotEqualTo(first.path("items").get(0).path("id"));
    var byType = json(send("GET", "/api/v1/events?type=order.created", null, true));
    assertThat(byType.path("items").size()).isEqualTo(1);
  }

  @Test
  void rejectsOversizedBodyWithKnownLength() throws Exception {
    var oversized =
        BODY.replace("\"currency\":\"COP\"", "\"large\":\"" + "x".repeat(1_048_576) + "\"");
    assertThat(post("payments", oversized).statusCode()).isEqualTo(413);
    assertThat(count()).isZero();
  }

  @Test
  void rejectsOversizedChunkedBody() throws Exception {
    var bytes = ("x".repeat(1_048_577)).getBytes(StandardCharsets.UTF_8);
    var request =
        authenticated("/api/v1/callbacks/payments")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofInputStream(() -> new ByteArrayInputStream(bytes)))
            .build();
    assertThat(client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode())
        .isEqualTo(413);
    assertThat(count()).isZero();
  }

  private long count() {
    return jdbc.queryForObject("SELECT count(*) FROM callback_events", Long.class);
  }

  private HttpResponse<String> post(String source, String body) throws Exception {
    return send("POST", "/api/v1/callbacks/" + source, body, true);
  }

  private HttpResponse<String> send(String method, String path, String body, boolean auth)
      throws Exception {
    var builder = auth ? authenticated(path) : HttpRequest.newBuilder(url(path));
    if (body != null) {
      builder.header("Content-Type", "application/json");
    }
    return client.send(
        builder
            .timeout(Duration.ofSeconds(20))
            .method(
                method,
                body == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(body))
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }

  private HttpRequest.Builder authenticated(String path) {
    return HttpRequest.newBuilder(url(path)).header("Authorization", basic(USERNAME, PASSWORD));
  }

  private static String basic(String user, String password) {
    return "Basic "
        + Base64.getEncoder()
            .encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8));
  }

  private URI url(String path) {
    return URI.create("http://localhost:" + port + path);
  }

  private JsonNode json(HttpResponse<String> response) {
    return mapper.readTree(response.body());
  }
}
