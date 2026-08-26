package com.example.banking.gateway;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "services")
public record ServiceEndpointsProperties(
        String dtm,
        String payment,
        String profile,
        String fraud,
        String notification,
        int timeoutMs) {
    public ServiceEndpointsProperties {
        dtm = defaultValue(dtm, "http://dtm-service:8081");
        payment = defaultValue(payment, "http://payment-service:8082");
        profile = defaultValue(profile, "http://customer-profile-service:8083");
        fraud = defaultValue(fraud, "http://fraud-service:8084");
        notification = defaultValue(notification, "http://notification-service:8085");
        timeoutMs = timeoutMs < 100 ? 1500 : timeoutMs;
    }

    private static String defaultValue(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
