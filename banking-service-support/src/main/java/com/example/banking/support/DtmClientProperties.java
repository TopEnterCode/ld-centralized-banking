package com.example.banking.support;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "dtm")
public record DtmClientProperties(String baseUrl, int timeoutMs) {
    public DtmClientProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "http://dtm-service:8081";
        }
        if (timeoutMs < 50) {
            timeoutMs = 700;
        }
    }
}
