package com.accounting.integration.metabase;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.accounting.integration.metabase.dto.MetabaseDatabaseResponse;
import com.accounting.integration.metabase.dto.MetabaseGroupResponse;
import com.accounting.integration.metabase.dto.MetabaseMembershipResponse;
import com.accounting.integration.metabase.dto.MetabaseSessionResponse;
import com.accounting.integration.metabase.dto.MetabaseUserListResponse;
import com.accounting.integration.metabase.dto.MetabaseUserResponse;

import jakarta.annotation.PostConstruct;

@Component
public class MetabaseApiClient {

    private static final Logger log = LoggerFactory.getLogger(MetabaseApiClient.class);

    private static final int MAX_RETRIES = 3;
    private static final long[] RETRY_DELAYS_MS = {1000, 2000, 4000};

    @Value("${metabase.site-url}")
    private String siteUrl;

    @Value("${metabase.api-key:}")
    private String apiKey;

    @Value("${metabase.admin-email:}")
    private String adminEmail;

    @Value("${metabase.admin-password:}")
    private String adminPassword;

    private RestClient restClient;
    private String sessionToken;

    @PostConstruct
    public void init() {
        this.restClient = RestClient.builder()
                .baseUrl(siteUrl)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
        log.info("MetabaseApiClient initialized with siteUrl: {}", siteUrl);
    }

    public String authenticate() {
        log.debug("Authenticating with Metabase");
        Map<String, String> credentials = Map.of(
                "username", adminEmail,
                "password", adminPassword);

        MetabaseSessionResponse response = executeWithRetry(() -> restClient.post()
                .uri("/api/session")
                .body(credentials)
                .retrieve()
                .body(MetabaseSessionResponse.class));

        this.sessionToken = response.sessionId();
        log.info("Successfully authenticated with Metabase");
        return sessionToken;
    }

    public MetabaseUserResponse createUser(String email, String firstName, String lastName) {
        log.debug("Creating Metabase user: {}", email);
        Map<String, Object> userData = new HashMap<>();
        userData.put("email", email);
        userData.put("first_name", firstName);
        userData.put("last_name", lastName);

        MetabaseUserResponse response = executeWithRetry(() -> buildAuthenticatedClient()
                .post()
                .uri("/api/user")
                .body(userData)
                .retrieve()
                .body(MetabaseUserResponse.class));

        log.info("Created Metabase user: {} (id={})", email, response.id());
        return response;
    }

    public MetabaseUserResponse updateUser(Long userId, String email, String firstName, String lastName) {
        log.debug("Updating Metabase user: {}", userId);
        Map<String, Object> userData = new HashMap<>();
        if (email != null) {
            userData.put("email", email);
        }
        if (firstName != null) {
            userData.put("first_name", firstName);
        }
        if (lastName != null) {
            userData.put("last_name", lastName);
        }

        MetabaseUserResponse response = executeWithRetry(() -> buildAuthenticatedClient()
                .put()
                .uri("/api/user/{id}", userId)
                .body(userData)
                .retrieve()
                .body(MetabaseUserResponse.class));

        log.info("Updated Metabase user: {}", userId);
        return response;
    }

    public void deactivateUser(Long userId) {
        log.debug("Deactivating Metabase user: {}", userId);
        Map<String, Object> userData = Map.of("is_active", false);

        executeWithRetry(() -> buildAuthenticatedClient()
                .put()
                .uri("/api/user/{id}", userId)
                .body(userData)
                .retrieve()
                .body(MetabaseUserResponse.class));

        log.info("Deactivated Metabase user: {}", userId);
    }

    public Optional<MetabaseUserResponse> findUserByEmail(String email) {
        log.debug("Finding Metabase user by email: {}", email);
        MetabaseUserListResponse response = executeWithRetry(() -> buildAuthenticatedClient()
                .get()
                .uri("/api/user")
                .retrieve()
                .body(MetabaseUserListResponse.class));

        if (response == null || response.data() == null) {
            return Optional.empty();
        }

        return response.data().stream()
                .filter(user -> email.equalsIgnoreCase(user.email()))
                .findFirst();
    }

    public MetabaseGroupResponse createGroup(String name) {
        log.debug("Creating Metabase group: {}", name);
        Map<String, String> groupData = Map.of("name", name);

        MetabaseGroupResponse response = executeWithRetry(() -> buildAuthenticatedClient()
                .post()
                .uri("/api/permissions/group")
                .body(groupData)
                .retrieve()
                .body(MetabaseGroupResponse.class));

        log.info("Created Metabase group: {} (id={})", name, response.id());
        return response;
    }

    public List<MetabaseGroupResponse> getGroups() {
        log.debug("Fetching Metabase groups");
        List<MetabaseGroupResponse> response = executeWithRetry(() -> buildAuthenticatedClient()
                .get()
                .uri("/api/permissions/group")
                .retrieve()
                .body(new ParameterizedTypeReference<List<MetabaseGroupResponse>>() {}));

        log.debug("Fetched {} Metabase groups", response != null ? response.size() : 0);
        return response != null ? response : List.of();
    }

    public MetabaseMembershipResponse addUserToGroup(Long userId, Long groupId) {
        log.debug("Adding user {} to group {}", userId, groupId);
        Map<String, Long> membershipData = Map.of(
                "user_id", userId,
                "group_id", groupId);

        MetabaseMembershipResponse response = executeWithRetry(() -> buildAuthenticatedClient()
                .post()
                .uri("/api/permissions/membership")
                .body(membershipData)
                .retrieve()
                .body(MetabaseMembershipResponse.class));

        log.info("Added user {} to group {}", userId, groupId);
        return response;
    }

    public void removeUserFromGroup(Long membershipId) {
        log.debug("Removing membership: {}", membershipId);
        executeWithRetry(() -> {
            buildAuthenticatedClient()
                    .delete()
                    .uri("/api/permissions/membership/{id}", membershipId)
                    .retrieve()
                    .toBodilessEntity();
            return null;
        });
        log.info("Removed membership: {}", membershipId);
    }

    public MetabaseDatabaseResponse createDatabaseConnection(
            String name,
            String host,
            int port,
            String dbname,
            String user,
            String password,
            String schema) {
        log.debug("Creating database connection: {}", name);

        Map<String, Object> details = new HashMap<>();
        details.put("host", host);
        details.put("port", port);
        details.put("dbname", dbname);
        details.put("user", user);
        details.put("password", password);
        if (schema != null) {
            details.put("schema-filters-type", "inclusion");
            details.put("schema-filters-patterns", schema);
        }

        Map<String, Object> dbData = new HashMap<>();
        dbData.put("name", name);
        dbData.put("engine", "postgres");
        dbData.put("details", details);

        MetabaseDatabaseResponse response = executeWithRetry(() -> buildAuthenticatedClient()
                .post()
                .uri("/api/database")
                .body(dbData)
                .retrieve()
                .body(MetabaseDatabaseResponse.class));

        log.info("Created database connection: {} (id={})", name, response.id());
        return response;
    }

    public MetabaseDatabaseResponse updateDatabaseConnection(
            Long dbId,
            String name,
            String host,
            Integer port,
            String dbname,
            String user,
            String password,
            String schema) {
        log.debug("Updating database connection: {}", dbId);

        Map<String, Object> details = new HashMap<>();
        if (host != null) {
            details.put("host", host);
        }
        if (port != null) {
            details.put("port", port);
        }
        if (dbname != null) {
            details.put("dbname", dbname);
        }
        if (user != null) {
            details.put("user", user);
        }
        if (password != null) {
            details.put("password", password);
        }
        if (schema != null) {
            details.put("schema-filters-type", "inclusion");
            details.put("schema-filters-patterns", schema);
        }

        Map<String, Object> dbData = new HashMap<>();
        if (name != null) {
            dbData.put("name", name);
        }
        if (!details.isEmpty()) {
            dbData.put("details", details);
        }

        MetabaseDatabaseResponse response = executeWithRetry(() -> buildAuthenticatedClient()
                .put()
                .uri("/api/database/{id}", dbId)
                .body(dbData)
                .retrieve()
                .body(MetabaseDatabaseResponse.class));

        log.info("Updated database connection: {}", dbId);
        return response;
    }

    public List<MetabaseDatabaseResponse> getDatabases() {
        log.debug("Fetching Metabase databases");

        @SuppressWarnings("unchecked")
        Map<String, Object> response = executeWithRetry(() -> buildAuthenticatedClient()
                .get()
                .uri("/api/database")
                .retrieve()
                .body(Map.class));

        if (response == null || !response.containsKey("data")) {
            return List.of();
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> data = (List<Map<String, Object>>) response.get("data");
        List<MetabaseDatabaseResponse> databases = data.stream()
                .map(this::mapToDatabase)
                .toList();

        log.debug("Fetched {} Metabase databases", databases.size());
        return databases;
    }

    @SuppressWarnings("unchecked")
    private MetabaseDatabaseResponse mapToDatabase(Map<String, Object> map) {
        return new MetabaseDatabaseResponse(
                ((Number) map.get("id")).longValue(),
                (String) map.get("name"),
                (String) map.get("engine"),
                (Map<String, Object>) map.get("details"),
                (Boolean) map.get("is_sample"));
    }

    private RestClient buildAuthenticatedClient() {
        ensureAuthenticated();
        return restClient.mutate()
                .defaultHeader(getAuthHeader(), getAuthValue())
                .build();
    }

    private String getAuthHeader() {
        return apiKey != null && !apiKey.isBlank() ? "X-Api-Key" : "X-Metabase-Session";
    }

    private String getAuthValue() {
        return apiKey != null && !apiKey.isBlank() ? apiKey : sessionToken;
    }

    private void ensureAuthenticated() {
        if (apiKey != null && !apiKey.isBlank()) {
            return;
        }
        if (sessionToken == null) {
            authenticate();
        }
    }

    private <T> T executeWithRetry(java.util.function.Supplier<T> operation) {
        Exception lastException = null;
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try {
                return operation.get();
            } catch (RestClientResponseException e) {
                int statusCode = e.getStatusCode().value();
                if (statusCode == HttpStatus.UNAUTHORIZED.value() && attempt == 0) {
                    log.debug("Received 401, attempting re-authentication");
                    sessionToken = null;
                    authenticate();
                    continue;
                }
                if (!isRetryable(statusCode)) {
                    throw new MetabaseApiException(e.getMessage(), statusCode, e);
                }
                lastException = e;
            } catch (MetabaseApiException e) {
                if (!isRetryable(e.getStatusCode())) {
                    throw e;
                }
                lastException = e;
            } catch (Exception e) {
                lastException = e;
            }

            if (attempt < MAX_RETRIES - 1) {
                long delay = RETRY_DELAYS_MS[attempt];
                log.warn("Metabase API call failed (attempt {}), retrying in {}ms", attempt + 1, delay);
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new MetabaseApiException("Interrupted during retry", ie);
                }
            }
        }
        throw new MetabaseApiException("Metabase API call failed after " + MAX_RETRIES + " attempts", lastException);
    }

    private boolean isRetryable(int statusCode) {
        return statusCode == 0
                || statusCode == HttpStatus.TOO_MANY_REQUESTS.value()
                || statusCode == HttpStatus.SERVICE_UNAVAILABLE.value()
                || statusCode == HttpStatus.GATEWAY_TIMEOUT.value()
                || statusCode >= 500;
    }
}
