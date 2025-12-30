package com.accounting.service.analytics;

import com.accounting.entity.User;

public interface MetabaseEmbedService {

    MetabaseEmbedConfig generateEmbedConfig(String dashboardKey, User user, Long companyId);

    MetabaseEmbedConfig refreshEmbedConfig(User user, Long companyId);

    String generateDashboardEmbedUrl(Long dashboardId);

    String generateQuestionEmbedUrl(Long questionId);

    record MetabaseEmbedConfig(
            String metabaseInstanceUrl,
            AuthConfig authConfig) {
    }

    record AuthConfig(
            boolean enabled,
            AuthProvider authProviderUri,
            AuthType authType) {
    }

    record AuthProvider(
            String uri,
            boolean fetchRequestToken) {
    }

    enum AuthType {
        JWT
    }

    record JwtPayload(
            String email,
            String firstName,
            String lastName,
            String[] groups,
            long exp) {
    }
}
