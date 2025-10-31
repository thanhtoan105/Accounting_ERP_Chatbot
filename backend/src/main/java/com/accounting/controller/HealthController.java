package com.accounting.controller;

import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    @Value("${info.app.name:accounting-backend}")
    private String appName;

    @Value("${info.app.version:0.0.1-SNAPSHOT}")
    private String appVersion;

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> payload = new HashMap<>();
        Map<String, Object> build = new HashMap<>();
        build.put("name", appName);
        build.put("version", appVersion);
        payload.put("status", "OK");
        payload.put("build", build);
        return ResponseEntity.ok(payload);
    }
}
