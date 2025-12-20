package com.accounting.service.impl;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.accounting.service.StorageService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

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
                    .header("Content-Type",
                            file.getContentType() == null ? "application/octet-stream" : file.getContentType())
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
                String signed = node.has("signedUrl") ? node.get("signedUrl").asText()
                        : (node.has("signedURL") ? node.get("signedURL").asText() : null);
                if (signed == null || signed.isBlank()) {
                    throw new RuntimeException("Supabase sign response missing signedUrl");
                }
                // The API may return a relative path; prefix project URL if needed
                if (signed.startsWith("http"))
                    return signed;
                return base + signed;
            }
            throw new RuntimeException("Supabase sign failed: HTTP " + res.statusCode());
        } catch (Exception e) {
            throw new RuntimeException("Supabase sign error", e);
        }
    }

    @Override
    public String uploadVoucherAttachment(UUID voucherId, MultipartFile file) {
        if (voucherId == null || file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Missing voucherId or file");
        }
        try {
            // Generate randomized path: vouchers/{voucherId}/{uuid}-{filename}
            String uuid = UUID.randomUUID().toString();
            String originalFilename = file.getOriginalFilename();
            String filename = originalFilename != null ? originalFilename : "file";
            // Sanitize filename (remove path separators and special chars)
            filename = filename.replaceAll("[^a-zA-Z0-9._-]", "_");
            String objectPath = "vouchers/" + voucherId + "/" + uuid + "-" + filename;

            String url = supabaseUrl.replaceAll("/+$", "") +
                    "/storage/v1/object/" + bucket + "/" + objectPath;

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(60)) // Longer timeout for large files
                    .header("Authorization", "Bearer " + supabaseServiceKey)
                    .header("Content-Type",
                            file.getContentType() == null ? "application/octet-stream" : file.getContentType())
                    .PUT(HttpRequest.BodyPublishers.ofByteArray(file.getBytes()))
                    .build();

            HttpResponse<Void> res = httpClient.send(req, HttpResponse.BodyHandlers.discarding());
            if (res.statusCode() >= 200 && res.statusCode() < 300) {
                return objectPath; // Return storage path, not URL
            }
            throw new RuntimeException("Supabase upload failed: HTTP " + res.statusCode());
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Supabase upload error", e);
        }
    }

    @Override
    public void deleteVoucherAttachment(String storagePath) {
        if (storagePath == null || storagePath.isBlank()) {
            throw new IllegalArgumentException("Missing storagePath");
        }
        try {
            String url = supabaseUrl.replaceAll("/+$", "") +
                    "/storage/v1/object/" + bucket + "/" + storagePath;

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Bearer " + supabaseServiceKey)
                    .DELETE()
                    .build();

            HttpResponse<Void> res = httpClient.send(req, HttpResponse.BodyHandlers.discarding());
            if (res.statusCode() < 200 || res.statusCode() >= 300) {
                // 404 is acceptable (file already deleted)
                if (res.statusCode() != 404) {
                    throw new RuntimeException("Supabase delete failed: HTTP " + res.statusCode());
                }
            }
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Supabase delete error", e);
        }
    }

    @Override
    public String generateSignedUrl(String storagePath, int expiresInSeconds) {
        if (storagePath == null || storagePath.isBlank()) {
            throw new IllegalArgumentException("Missing storagePath");
        }
        return signObjectUrl(storagePath, expiresInSeconds);
    }

    @Override
    public String uploadPurchaseBillAttachment(UUID purchaseBillId, MultipartFile file) {
        if (purchaseBillId == null || file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Missing purchaseBillId or file");
        }
        try {
            // Generate randomized path: purchase-bills/{purchaseBillId}/{uuid}-{filename}
            String uuid = UUID.randomUUID().toString();
            String originalFilename = file.getOriginalFilename();
            String filename = originalFilename != null ? originalFilename : "file";
            // Sanitize filename (remove path separators and special chars)
            filename = filename.replaceAll("[^a-zA-Z0-9._-]", "_");
            String objectPath = "purchase-bills/" + purchaseBillId + "/" + uuid + "-" + filename;

            String url = supabaseUrl.replaceAll("/+$", "") +
                    "/storage/v1/object/" + bucket + "/" + objectPath;

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(60)) // Longer timeout for large files
                    .header("Authorization", "Bearer " + supabaseServiceKey)
                    .header("Content-Type",
                            file.getContentType() == null ? "application/octet-stream" : file.getContentType())
                    .PUT(HttpRequest.BodyPublishers.ofByteArray(file.getBytes()))
                    .build();

            HttpResponse<Void> res = httpClient.send(req, HttpResponse.BodyHandlers.discarding());
            if (res.statusCode() >= 200 && res.statusCode() < 300) {
                return objectPath; // Return storage path, not URL
            }
            throw new RuntimeException("Supabase upload failed: HTTP " + res.statusCode());
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Supabase upload error", e);
        }
    }

    @Override
    public void deletePurchaseBillAttachment(String storagePath) {
        if (storagePath == null || storagePath.isBlank()) {
            throw new IllegalArgumentException("Missing storagePath");
        }
        try {
            String url = supabaseUrl.replaceAll("/+$", "") +
                    "/storage/v1/object/" + bucket + "/" + storagePath;

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Bearer " + supabaseServiceKey)
                    .DELETE()
                    .build();

            HttpResponse<Void> res = httpClient.send(req, HttpResponse.BodyHandlers.discarding());
            if (res.statusCode() < 200 || res.statusCode() >= 300) {
                // 404 is acceptable (file already deleted)
                if (res.statusCode() != 404) {
                    throw new RuntimeException("Supabase delete failed: HTTP " + res.statusCode());
                }
            }
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Supabase delete error", e);
        }
    }

    @Override
    public String uploadSalesInvoiceAttachment(UUID salesInvoiceId, MultipartFile file) {
        if (salesInvoiceId == null || file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Missing salesInvoiceId or file");
        }
        try {
            // Generate randomized path: sales-invoices/{salesInvoiceId}/{uuid}-{filename}
            String uuid = UUID.randomUUID().toString();
            String originalFilename = file.getOriginalFilename();
            String filename = originalFilename != null ? originalFilename : "file";
            // Sanitize filename (remove path separators and special chars)
            filename = filename.replaceAll("[^a-zA-Z0-9._-]", "_");
            String objectPath = "sales-invoices/" + salesInvoiceId + "/" + uuid + "-" + filename;

            String url = supabaseUrl.replaceAll("/+$", "") +
                    "/storage/v1/object/" + bucket + "/" + objectPath;

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(60)) // Longer timeout for large files
                    .header("Authorization", "Bearer " + supabaseServiceKey)
                    .header("Content-Type",
                            file.getContentType() == null ? "application/octet-stream" : file.getContentType())
                    .PUT(HttpRequest.BodyPublishers.ofByteArray(file.getBytes()))
                    .build();

            HttpResponse<Void> res = httpClient.send(req, HttpResponse.BodyHandlers.discarding());
            if (res.statusCode() >= 200 && res.statusCode() < 300) {
                return objectPath; // Return storage path, not URL
            }
            throw new RuntimeException("Supabase upload failed: HTTP " + res.statusCode());
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Supabase upload error", e);
        }
    }

    @Override
    public void deleteSalesInvoiceAttachment(String storagePath) {
        if (storagePath == null || storagePath.isBlank()) {
            throw new IllegalArgumentException("Missing storagePath");
        }
        try {
            String url = supabaseUrl.replaceAll("/+$", "") +
                    "/storage/v1/object/" + bucket + "/" + storagePath;

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Bearer " + supabaseServiceKey)
                    .DELETE()
                    .build();

            HttpResponse<Void> res = httpClient.send(req, HttpResponse.BodyHandlers.discarding());
            if (res.statusCode() < 200 || res.statusCode() >= 300) {
                // 404 is acceptable (file already deleted)
                if (res.statusCode() != 404) {
                    throw new RuntimeException("Supabase delete failed: HTTP " + res.statusCode());
                }
            }
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Supabase delete error", e);
        }
    }

    @Override
    public String uploadReceiptAttachment(UUID receiptId, MultipartFile file) {
        if (receiptId == null || file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Missing receiptId or file");
        }
        try {
            // Generate randomized path: receipts/{receiptId}/{uuid}-{filename}
            String uuid = UUID.randomUUID().toString();
            String originalFilename = file.getOriginalFilename();
            String filename = originalFilename != null ? originalFilename : "file";
            // Sanitize filename (remove path separators and special chars)
            filename = filename.replaceAll("[^a-zA-Z0-9._-]", "_");
            String objectPath = "receipts/" + receiptId + "/" + uuid + "-" + filename;

            String url = supabaseUrl.replaceAll("/+$", "") +
                    "/storage/v1/object/" + bucket + "/" + objectPath;

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(60)) // Longer timeout for large files
                    .header("Authorization", "Bearer " + supabaseServiceKey)
                    .header("Content-Type",
                            file.getContentType() == null ? "application/octet-stream" : file.getContentType())
                    .PUT(HttpRequest.BodyPublishers.ofByteArray(file.getBytes()))
                    .build();

            HttpResponse<Void> res = httpClient.send(req, HttpResponse.BodyHandlers.discarding());
            if (res.statusCode() >= 200 && res.statusCode() < 300) {
                return objectPath; // Return storage path, not URL
            }
            throw new RuntimeException("Supabase upload failed: HTTP " + res.statusCode());
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Supabase upload error", e);
        }
    }

    @Override
    public void deleteReceiptAttachment(String storagePath) {
        if (storagePath == null || storagePath.isBlank()) {
            throw new IllegalArgumentException("Missing storagePath");
        }
        try {
            String url = supabaseUrl.replaceAll("/+$", "") +
                    "/storage/v1/object/" + bucket + "/" + storagePath;

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Bearer " + supabaseServiceKey)
                    .DELETE()
                    .build();

            HttpResponse<Void> res = httpClient.send(req, HttpResponse.BodyHandlers.discarding());
            if (res.statusCode() < 200 || res.statusCode() >= 300) {
                // 404 is acceptable (file already deleted)
                if (res.statusCode() != 404) {
                    throw new RuntimeException("Supabase delete failed: HTTP " + res.statusCode());
                }
            }
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Supabase delete error", e);
        }
    }

    private String detectExtension(String contentType) {
        if (contentType == null)
            return ".bin";
        String ct = contentType.toLowerCase(Locale.ROOT);
        if (ct.contains("png"))
            return ".png";
        if (ct.contains("jpeg") || ct.contains("jpg"))
            return ".jpg";
        return ".bin";
    }
}
