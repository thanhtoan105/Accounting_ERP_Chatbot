package com.accounting.service.analytics;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.accounting.entity.User;
import com.accounting.integration.metabase.MetabaseApiClient;
import com.accounting.integration.metabase.MetabaseApiException;
import com.accounting.integration.metabase.dto.MetabaseDatabaseResponse;
import com.accounting.integration.metabase.dto.MetabaseGroupResponse;
import com.accounting.integration.metabase.dto.MetabaseUserResponse;

@Service
public class MetabaseProvisioningServiceImpl implements MetabaseProvisioningService {

    private static final Logger log = LoggerFactory.getLogger(MetabaseProvisioningServiceImpl.class);

    private static final String TENANT_DB_PREFIX = "Company ";
    private static final String TENANT_GROUP_PREFIX = "company_";
    private static final String REDIS_KEY_PREFIX = "metabase:tenant:";

    private static final Map<String, String> ROLE_TO_METABASE_GROUP = Map.of(
            "admin", "Analytics Admins",
            "chief_accountant", "Analytics Power Users",
            "cfo", "Analytics Viewers",
            "accountant", "Analytics Basic"
    );

    private final MetabaseApiClient metabaseApiClient;
    private final TenantAnalyticsProvisioningService tenantDbService;
    private final StringRedisTemplate redisTemplate;

    private final Map<Long, Long> tenantGroupCache = new ConcurrentHashMap<>();
    private final Map<String, Long> roleGroupCache = new ConcurrentHashMap<>();

    @Value("${spring.datasource.url}")
    private String datasourceUrl;

    @Value("${metabase.db.host:postgres}")
    private String dbHost;

    @Value("${metabase.db.port:5432}")
    private int dbPort;

    @Value("${metabase.db.name:accounting_dev}")
    private String dbName;

    public MetabaseProvisioningServiceImpl(
            MetabaseApiClient metabaseApiClient,
            TenantAnalyticsProvisioningService tenantDbService,
            StringRedisTemplate redisTemplate) {
        this.metabaseApiClient = metabaseApiClient;
        this.tenantDbService = tenantDbService;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public ProvisioningResult provisionTenant(Long companyId) {
        log.info("Provisioning Metabase tenant for company {}", companyId);

        try {
            metabaseApiClient.authenticate();

            var dbResult = tenantDbService.provisionTenant(companyId);
            if (!dbResult.success()) {
                return ProvisioningResult.failure("Database provisioning failed: " + dbResult.message());
            }

            var credentials = tenantDbService.getTenantCredentials(companyId);
            if (credentials.isEmpty()) {
                return ProvisioningResult.failure("Failed to get tenant credentials");
            }

            var creds = credentials.get();

            String dbConnectionName = TENANT_DB_PREFIX + companyId;
            Long databaseId = findOrCreateDatabase(dbConnectionName, creds.roleName(), 
                    creds.password(), creds.schemaName());

            String groupName = TENANT_GROUP_PREFIX + companyId;
            Long groupId = findOrCreateGroup(groupName);
            tenantGroupCache.put(companyId, groupId);

            storeTenantMetadata(companyId, databaseId, groupId);

            log.info("Successfully provisioned Metabase tenant for company {}: db={}, group={}", 
                    companyId, databaseId, groupId);

            return ProvisioningResult.success(databaseId, groupId, null);

        } catch (MetabaseApiException e) {
            log.error("Metabase API error provisioning tenant {}: {}", companyId, e.getMessage(), e);
            return ProvisioningResult.failure("Metabase API error: " + e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error provisioning tenant {}: {}", companyId, e.getMessage(), e);
            return ProvisioningResult.failure("Unexpected error: " + e.getMessage());
        }
    }

    @Override
    public UserProvisioningResult provisionUser(User user, Long companyId) {
        log.info("Provisioning Metabase user: {} for company {}", user.getEmail(), companyId);

        try {
            metabaseApiClient.authenticate();

            Optional<MetabaseUserResponse> existingUser = metabaseApiClient.findUserByEmail(user.getEmail());

            MetabaseUserResponse mbUser;
            if (existingUser.isPresent()) {
                mbUser = existingUser.get();
                log.debug("User {} already exists in Metabase with id {}", user.getEmail(), mbUser.id());
            } else {
                String[] names = splitFullName(user.getFullName());
                mbUser = metabaseApiClient.createUser(user.getEmail(), names[0], names[1]);
                log.info("Created new Metabase user: {} (id={})", user.getEmail(), mbUser.id());
            }

            List<Long> assignedGroups = assignUserToGroups(mbUser.id(), companyId, 
                    Set.of(user.getRole()));

            return UserProvisioningResult.success(mbUser.id(), assignedGroups);

        } catch (MetabaseApiException e) {
            log.error("Metabase API error provisioning user {}: {}", user.getEmail(), e.getMessage(), e);
            return UserProvisioningResult.failure("Metabase API error: " + e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error provisioning user {}: {}", user.getEmail(), e.getMessage(), e);
            return UserProvisioningResult.failure("Unexpected error: " + e.getMessage());
        }
    }

    @Override
    public UserProvisioningResult syncUser(User user, Long companyId) {
        log.info("Syncing Metabase user: {} for company {}", user.getEmail(), companyId);

        try {
            metabaseApiClient.authenticate();

            Optional<MetabaseUserResponse> existingUser = metabaseApiClient.findUserByEmail(user.getEmail());

            if (existingUser.isEmpty()) {
                return provisionUser(user, companyId);
            }

            MetabaseUserResponse mbUser = existingUser.get();
            String[] names = splitFullName(user.getFullName());

            metabaseApiClient.updateUser(mbUser.id(), user.getEmail(), names[0], names[1]);

            List<Long> assignedGroups = assignUserToGroups(mbUser.id(), companyId, 
                    Set.of(user.getRole()));

            return UserProvisioningResult.updated(mbUser.id(), assignedGroups);

        } catch (MetabaseApiException e) {
            log.error("Metabase API error syncing user {}: {}", user.getEmail(), e.getMessage(), e);
            return UserProvisioningResult.failure("Metabase API error: " + e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error syncing user {}: {}", user.getEmail(), e.getMessage(), e);
            return UserProvisioningResult.failure("Unexpected error: " + e.getMessage());
        }
    }

    @Override
    public void deactivateUser(User user) {
        log.info("Deactivating Metabase user: {}", user.getEmail());

        try {
            metabaseApiClient.authenticate();

            Optional<MetabaseUserResponse> existingUser = metabaseApiClient.findUserByEmail(user.getEmail());

            if (existingUser.isPresent()) {
                metabaseApiClient.deactivateUser(existingUser.get().id());
                log.info("Deactivated Metabase user: {}", user.getEmail());
            } else {
                log.debug("User {} not found in Metabase, nothing to deactivate", user.getEmail());
            }

        } catch (MetabaseApiException e) {
            log.error("Metabase API error deactivating user {}: {}", user.getEmail(), e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error deactivating user {}: {}", user.getEmail(), e.getMessage(), e);
        }
    }

    @Override
    public void assignUserGroups(User user, Long companyId, Set<String> roles) {
        log.info("Assigning Metabase groups for user {} with roles {}", user.getEmail(), roles);

        try {
            metabaseApiClient.authenticate();

            Optional<MetabaseUserResponse> existingUser = metabaseApiClient.findUserByEmail(user.getEmail());

            if (existingUser.isEmpty()) {
                log.warn("User {} not found in Metabase, cannot assign groups", user.getEmail());
                return;
            }

            assignUserToGroups(existingUser.get().id(), companyId, roles);

        } catch (Exception e) {
            log.error("Error assigning groups for user {}: {}", user.getEmail(), e.getMessage(), e);
        }
    }

    @Override
    public boolean isTenantProvisioned(Long companyId) {
        String key = REDIS_KEY_PREFIX + companyId + ":database_id";
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    @Override
    public Optional<MetabaseUserResponse> findMetabaseUser(String email) {
        try {
            metabaseApiClient.authenticate();
            return metabaseApiClient.findUserByEmail(email);
        } catch (Exception e) {
            log.error("Error finding Metabase user {}: {}", email, e.getMessage());
            return Optional.empty();
        }
    }

    private Long findOrCreateDatabase(String name, String user, String password, String schema) {
        List<MetabaseDatabaseResponse> databases = metabaseApiClient.getDatabases();

        Optional<MetabaseDatabaseResponse> existing = databases.stream()
                .filter(db -> name.equals(db.name()))
                .findFirst();

        if (existing.isPresent()) {
            log.debug("Database connection '{}' already exists with id {}", name, existing.get().id());
            metabaseApiClient.updateDatabaseConnection(
                    existing.get().id(), null, null, null, null, user, password, schema);
            return existing.get().id();
        }

        MetabaseDatabaseResponse newDb = metabaseApiClient.createDatabaseConnection(
                name, dbHost, dbPort, dbName, user, password, schema);

        return newDb.id();
    }

    private Long findOrCreateGroup(String name) {
        List<MetabaseGroupResponse> groups = metabaseApiClient.getGroups();

        Optional<MetabaseGroupResponse> existing = groups.stream()
                .filter(g -> name.equals(g.name()))
                .findFirst();

        if (existing.isPresent()) {
            log.debug("Group '{}' already exists with id {}", name, existing.get().id());
            return existing.get().id();
        }

        MetabaseGroupResponse newGroup = metabaseApiClient.createGroup(name);
        return newGroup.id();
    }

    private List<Long> assignUserToGroups(Long userId, Long companyId, Set<String> roles) {
        List<Long> assignedGroups = new ArrayList<>();

        Long tenantGroupId = getTenantGroupId(companyId);
        if (tenantGroupId != null) {
            try {
                metabaseApiClient.addUserToGroup(userId, tenantGroupId);
                assignedGroups.add(tenantGroupId);
            } catch (MetabaseApiException e) {
                if (!e.getMessage().contains("already a member")) {
                    log.warn("Failed to add user {} to tenant group {}: {}", userId, tenantGroupId, e.getMessage());
                }
            }
        }

        for (String role : roles) {
            String normalizedRole = role.toLowerCase();
            String metabaseGroupName = ROLE_TO_METABASE_GROUP.get(normalizedRole);

            if (metabaseGroupName != null) {
                Long roleGroupId = getRoleGroupId(metabaseGroupName);
                if (roleGroupId != null) {
                    try {
                        metabaseApiClient.addUserToGroup(userId, roleGroupId);
                        assignedGroups.add(roleGroupId);
                    } catch (MetabaseApiException e) {
                        if (!e.getMessage().contains("already a member")) {
                            log.warn("Failed to add user {} to role group {}: {}", 
                                    userId, metabaseGroupName, e.getMessage());
                        }
                    }
                }
            }
        }

        return assignedGroups;
    }

    private Long getTenantGroupId(Long companyId) {
        if (tenantGroupCache.containsKey(companyId)) {
            return tenantGroupCache.get(companyId);
        }

        String key = REDIS_KEY_PREFIX + companyId + ":group_id";
        String groupIdStr = redisTemplate.opsForValue().get(key);

        if (groupIdStr != null) {
            Long groupId = Long.parseLong(groupIdStr);
            tenantGroupCache.put(companyId, groupId);
            return groupId;
        }

        String groupName = TENANT_GROUP_PREFIX + companyId;
        List<MetabaseGroupResponse> groups = metabaseApiClient.getGroups();

        Optional<MetabaseGroupResponse> existing = groups.stream()
                .filter(g -> groupName.equals(g.name()))
                .findFirst();

        if (existing.isPresent()) {
            Long groupId = existing.get().id();
            tenantGroupCache.put(companyId, groupId);
            redisTemplate.opsForValue().set(key, groupId.toString());
            return groupId;
        }

        return null;
    }

    private Long getRoleGroupId(String groupName) {
        if (roleGroupCache.containsKey(groupName)) {
            return roleGroupCache.get(groupName);
        }

        List<MetabaseGroupResponse> groups = metabaseApiClient.getGroups();

        Optional<MetabaseGroupResponse> existing = groups.stream()
                .filter(g -> groupName.equals(g.name()))
                .findFirst();

        if (existing.isPresent()) {
            roleGroupCache.put(groupName, existing.get().id());
            return existing.get().id();
        }

        MetabaseGroupResponse newGroup = metabaseApiClient.createGroup(groupName);
        roleGroupCache.put(groupName, newGroup.id());
        return newGroup.id();
    }

    private void storeTenantMetadata(Long companyId, Long databaseId, Long groupId) {
        String dbKey = REDIS_KEY_PREFIX + companyId + ":database_id";
        String groupKey = REDIS_KEY_PREFIX + companyId + ":group_id";

        redisTemplate.opsForValue().set(dbKey, databaseId.toString());
        redisTemplate.opsForValue().set(groupKey, groupId.toString());
    }

    private String[] splitFullName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return new String[]{"User", ""};
        }

        String[] parts = fullName.trim().split("\\s+", 2);
        if (parts.length == 1) {
            return new String[]{parts[0], ""};
        }
        return parts;
    }
}
