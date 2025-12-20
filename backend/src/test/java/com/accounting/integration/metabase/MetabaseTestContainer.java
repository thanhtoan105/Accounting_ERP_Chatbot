package com.accounting.integration.metabase;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MetabaseTestContainer {

    private static final Logger log = LoggerFactory.getLogger(MetabaseTestContainer.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(Duration.ofSeconds(30))
            .readTimeout(Duration.ofSeconds(60))
            .build();

    public static final String ADMIN_EMAIL = "admin@test.com";
    public static final String ADMIN_PASSWORD = "TestPassword123!";
    public static final String ADMIN_FIRST_NAME = "Admin";
    public static final String ADMIN_LAST_NAME = "User";
    public static final String SITE_NAME = "Accounting Test";

    @SuppressWarnings("resource")
    public static GenericContainer<?> createContainer() {
        return new GenericContainer<>(DockerImageName.parse("metabase/metabase:v0.50.36"))
                .withExposedPorts(3000)
                .waitingFor(Wait.forHttp("/api/health")
                        .forStatusCode(200)
                        .withStartupTimeout(Duration.ofMinutes(5)))
                .withEnv("MB_DB_TYPE", "h2")
                .withEnv("JAVA_TOOL_OPTIONS", "-Xmx1g");
    }

    public static String getMetabaseUrl(GenericContainer<?> container) {
        return "http://" + container.getHost() + ":" + container.getMappedPort(3000);
    }

    public static String setupMetabaseAndGetSessionToken(String baseUrl) throws IOException {
        log.info("Starting Metabase initial setup at {}", baseUrl);

        if (isSetupComplete(baseUrl)) {
            log.info("Metabase already set up, authenticating...");
            return authenticate(baseUrl, ADMIN_EMAIL, ADMIN_PASSWORD);
        }

        String setupToken = getSetupToken(baseUrl);
        log.info("Got setup token: {}...", setupToken.substring(0, Math.min(8, setupToken.length())));

        completeSetup(baseUrl, setupToken);
        log.info("Metabase setup completed successfully");

        return authenticate(baseUrl, ADMIN_EMAIL, ADMIN_PASSWORD);
    }

    private static boolean isSetupComplete(String baseUrl) throws IOException {
        Request request = new Request.Builder()
                .url(baseUrl + "/api/session/properties")
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                return false;
            }
            JsonNode json = objectMapper.readTree(response.body().string());
            JsonNode hasUserSetup = json.get("has-user-setup");
            return hasUserSetup != null && hasUserSetup.asBoolean();
        }
    }

    private static String getSetupToken(String baseUrl) throws IOException {
        Request request = new Request.Builder()
                .url(baseUrl + "/api/session/properties")
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IOException("Failed to get setup token: " + response.code());
            }
            JsonNode json = objectMapper.readTree(response.body().string());
            JsonNode tokenNode = json.get("setup-token");
            if (tokenNode == null || tokenNode.isNull()) {
                throw new IOException("Setup token not found - Metabase may already be set up");
            }
            return tokenNode.asText();
        }
    }

    private static void completeSetup(String baseUrl, String setupToken) throws IOException {
        Map<String, Object> setupRequest = Map.of(
                "token", setupToken,
                "user", Map.of(
                        "email", ADMIN_EMAIL,
                        "password", ADMIN_PASSWORD,
                        "first_name", ADMIN_FIRST_NAME,
                        "last_name", ADMIN_LAST_NAME,
                        "site_name", SITE_NAME),
                "prefs", Map.of(
                        "site_name", SITE_NAME,
                        "site_locale", "en",
                        "allow_tracking", false));

        String jsonBody = objectMapper.writeValueAsString(setupRequest);

        Request request = new Request.Builder()
                .url(baseUrl + "/api/setup")
                .post(RequestBody.create(jsonBody, MediaType.parse("application/json")))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String body = response.body() != null ? response.body().string() : "no body";
                throw new IOException("Setup failed with status " + response.code() + ": " + body);
            }
            log.info("Setup API returned: {}", response.code());
        }
    }

    public static String authenticate(String baseUrl, String email, String password) throws IOException {
        Map<String, String> credentials = Map.of(
                "username", email,
                "password", password);

        String jsonBody = objectMapper.writeValueAsString(credentials);

        Request request = new Request.Builder()
                .url(baseUrl + "/api/session")
                .post(RequestBody.create(jsonBody, MediaType.parse("application/json")))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IOException("Authentication failed: " + response.code());
            }
            JsonNode json = objectMapper.readTree(response.body().string());
            String sessionId = json.get("id").asText();
            log.info("Authenticated successfully, session: {}...", sessionId.substring(0, Math.min(8, sessionId.length())));
            return sessionId;
        }
    }

    public static boolean checkHealth(String baseUrl) {
        try {
            Request request = new Request.Builder()
                    .url(baseUrl + "/api/health")
                    .get()
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                return response.isSuccessful();
            }
        } catch (Exception e) {
            log.warn("Health check failed: {}", e.getMessage());
            return false;
        }
    }
}
