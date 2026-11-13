package com.sis.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/${app.api.version:v1}")
public class HealthController {

    @Value("${spring.profiles.active:dev}")
    private String activeProfile;

    @Value("${app.api.version:v1}")
    private String apiVersion;

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("status", "ok");
        status.put("service", "sis");
        status.put("profile", activeProfile);
        status.put("apiVersion", apiVersion);
        status.put("timestamp", java.time.Instant.now().toString());
        return status;
    }

    @GetMapping("/openapi")
    public Map<String, Object> openApiMetadata() {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("title", "Makerere Institute of Technology Student Information Portal");
        meta.put("version", apiVersion);
        meta.put("profile", activeProfile);
        meta.put("basePath", "/api/" + apiVersion);
        return meta;
    }
}
