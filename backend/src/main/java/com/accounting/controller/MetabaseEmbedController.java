package com.accounting.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.accounting.service.analytics.MetabaseEmbedService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/metabase")
@RequiredArgsConstructor
public class MetabaseEmbedController {

    private final MetabaseEmbedService embedService;

    @GetMapping("/embed/dashboard/{dashboardId}")
    public ResponseEntity<EmbedUrlResponse> getDashboardEmbedUrl(@PathVariable Long dashboardId) {
        String embedUrl = embedService.generateDashboardEmbedUrl(dashboardId);
        return ResponseEntity.ok(new EmbedUrlResponse(embedUrl));
    }

    @GetMapping("/embed/question/{questionId}")
    public ResponseEntity<EmbedUrlResponse> getQuestionEmbedUrl(@PathVariable Long questionId) {
        String embedUrl = embedService.generateQuestionEmbedUrl(questionId);
        return ResponseEntity.ok(new EmbedUrlResponse(embedUrl));
    }

    public record EmbedUrlResponse(String embedUrl) {}
}
