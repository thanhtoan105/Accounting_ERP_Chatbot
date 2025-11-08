package com.accounting.service.impl;

import com.accounting.service.StorageService;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@ConditionalOnProperty(name = "SUPABASE_URL")
public class SupabaseStorageService implements StorageService {

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Value("${SUPABASE_URL}")
    private String supabaseUrl;

    @Value("${SUPABASE_SERVICE_ROLE_KEY}")
    private String supabaseServiceKey;

    @Value("${SUPABASE_BUCKET:public}")
    private String bucket;

    @Value("${SUPABASE_PUBLIC_BUCKET:true}")
    private boolean publicBucket;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String uploadCompanyLogo(Long companyId, MultipartFile file) {
        if (companyId == null || file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Missing companyId or file");
        }
        try {
            String ext = detectExtension(file.getContentType());
            String objectPath = "logos/company-" + companyId + ext;
            String url = supabaseUrl.replaceAll("/+$", "") + 
                "/storage/v1/object/" + bucket + "/" + objectPath;

            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + supabaseServiceKey)
                .header("Content-Type", file.getContentType() == null ? "application/octet-stream" : file.getContentType())
                .header("x-upsert", "true")
                .PUT(HttpRequest.BodyPublishers.ofByteArray(file.getBytes()))
                .build();

            HttpResponse<Void> res = httpClient.send(req, HttpResponse.BodyHandlers.discarding());
            if (res.statusCode() >= 200 && res.statusCode() < 300) {
                String base = supabaseUrl.replaceAll("/+$", "");
                if (publicBucket) {
                    return base + "/storage/v1/object/public/" + bucket + "/" + objectPath;
                }
                // Private bucket → return signed URL (default 7 days)
                return signObjectUrl(objectPath, 7 * 24 * 60 * 60);
            }
            throw new RuntimeException("Supabase upload failed: HTTP " + res.statusCode());
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Supabase upload error", e);
        }
    }

    private String signObjectUrl(String objectPath, int expiresInSeconds) {
        try {
            String base = supabaseUrl.replaceAll("/+$", "");
            String url = base + "/storage/v1/object/sign/" + bucket + "/" + objectPath;
            String json = "{\"expiresIn\":" + expiresInSeconds + "}";
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", "Bearer " + supabaseServiceKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();
            HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() >= 200 && res.statusCode() < 300) {
                JsonNode node = objectMapper.readTree(res.body());
                // Supabase returns { "signedUrl": "..." } or { "signedURL": "..." }
                String signed = node.has("signedUrl") ? node.get("signedUrl").asText() :
                                (node.has("signedURL") ? node.get("signedURL").asText() : null);
                if (signed == null || signed.isBlank()) {
                    throw new RuntimeException("Supabase sign response missing signedUrl");
                }
                // The API may return a relative path; prefix project URL if needed
                if (signed.startsWith("http")) return signed;
                return base + signed;
            }
            throw new RuntimeException("Supabase sign failed: HTTP " + res.statusCode());
        } catch (Exception e) {
            throw new RuntimeException("Supabase sign error", e);
        }
    }

    private String detectExtension(String contentType) {
        if (contentType == null) return ".bin";
        String ct = contentType.toLowerCase(Locale.ROOT);
        if (ct.contains("png")) return ".png";
        if (ct.contains("jpeg") || ct.contains("jpg")) return ".jpg";
        return ".bin";
    }
}


