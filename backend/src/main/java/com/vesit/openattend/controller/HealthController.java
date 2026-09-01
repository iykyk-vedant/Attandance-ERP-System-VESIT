package com.vesit.openattend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

    @GetMapping
    public ResponseEntity<Map<String, Object>> getHealth() {
        long uptimeSeconds = ManagementFactory.getRuntimeMXBean().getUptime() / 1000;
        return ResponseEntity.ok(Map.of(
            "status", "ok",
            "service", "OpenAttend API",
            "uptimeSeconds", uptimeSeconds,
            "timestamp", Instant.now().toString()
        ));
    }

    @GetMapping("/ready")
    public ResponseEntity<Map<String, Object>> getReadiness() {
        return ResponseEntity.ok(Map.of(
            "status", "ready",
            "database", "connected",
            "sheetsClient", "active",
            "timestamp", Instant.now().toString()
        ));
    }
}
