package com.example.banking.contracts;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

public final class MaintenanceContracts {
    private MaintenanceContracts() {}

    public record Configuration(
            boolean enabled, String mode, String title, String message, String eta) {
        public static Configuration safeDefault() {
            return new Configuration(
                    false,
                    "read-only",
                    "Scheduled maintenance",
                    "Services are operating normally.",
                    "No maintenance scheduled");
        }

        public static Configuration from(JsonNode value) {
            if (value != null && value.isBoolean()) {
                Configuration defaults = safeDefault();
                boolean enabled = value.asBoolean();
                return new Configuration(
                        enabled,
                        defaults.mode(),
                        defaults.title(),
                        enabled
                                ? "Transfers are temporarily paused while we perform maintenance."
                                : defaults.message(),
                        enabled ? "Expected recovery: shortly" : defaults.eta());
            }
            if (value == null || !value.isObject()) {
                return safeDefault();
            }
            Configuration defaults = safeDefault();
            return new Configuration(
                    value.path("enabled").asBoolean(defaults.enabled()),
                    text(value, "mode", defaults.mode()),
                    text(value, "title", defaults.title()),
                    text(value, "message", defaults.message()),
                    text(value, "eta", defaults.eta()));
        }

        private static String text(JsonNode value, String field, String fallback) {
            String candidate = value.path(field).asText("").trim();
            return candidate.isEmpty() ? fallback : candidate;
        }
    }

    public record Status(Configuration configuration, DecisionMetadata decision) {
        public boolean enabled() {
            return configuration.enabled();
        }

        public static Status safeDefault(String correlationId) {
            Configuration configuration = Configuration.safeDefault();
            return new Status(
                    configuration,
                    new DecisionMetadata(
                            "web-gateway",
                            FlagKey.MAINTENANCE_BANNER.key(),
                            configurationNode(configuration),
                            "Maintenance flag unavailable; safe default applied",
                            DecisionSource.SERVICE_FALLBACK,
                            true,
                            Instant.now(),
                            correlationId));
        }

        private static JsonNode configurationNode(Configuration configuration) {
            return com.fasterxml.jackson.databind.node.JsonNodeFactory.instance
                    .objectNode()
                    .put("enabled", configuration.enabled())
                    .put("mode", configuration.mode())
                    .put("title", configuration.title())
                    .put("message", configuration.message())
                    .put("eta", configuration.eta());
        }
    }
}
