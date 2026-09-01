package com.example.banking.gateway;

import com.example.banking.contracts.FlagKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Deterministic, synthetic observability data for the presenter monitoring view. */
public final class MonitoringData {
    private static final Instant GENERATED_AT = Instant.parse("2026-08-26T08:00:00Z");

    private MonitoringData() {}

    public static Map<String, Object> snapshot(String mode) {
        List<Map<String, Object>> flags = flags();
        List<Map<String, Object>> history = history();
        List<Map<String, Object>> releases = releases();
        List<Map<String, Object>> errorLogs = errorLogs();
        List<Map<String, Object>> traces = traces();
        List<Map<String, Object>> sessions = sessions();
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("mode", mode);
        snapshot.put("syntheticDataOnly", true);
        snapshot.put("dataSource", "deterministic mock telemetry");
        snapshot.put("generatedAt", GENERATED_AT);
        snapshot.put(
                "summary",
                Map.of(
                        "flagCount", flags.size(),
                        "releaseCount", releases.size(),
                        "errorLogCount", errorLogs.size(),
                        "traceCount", traces.size(),
                        "sessionCount", sessions.size(),
                        "errorRate", "0.7%"));
        snapshot.put("flags", flags);
        snapshot.put("history", history);
        snapshot.put("releases", releases);
        snapshot.put("errorLogs", errorLogs);
        snapshot.put("traces", traces);
        snapshot.put("sessions", sessions);
        return Map.copyOf(snapshot);
    }

    private static List<Map<String, Object>> flags() {
        return Arrays.stream(FlagKey.values()).map(MonitoringData::flag).toList();
    }

    private static Map<String, Object> flag(FlagKey flag) {
        return Map.of(
                "flagKey", flag.key(),
                "label", label(flag),
                "type", flag.type().name().toLowerCase(),
                "owner", owner(flag),
                "currentValue", displayValue(flag),
                "status", flag == FlagKey.MAINTENANCE_BANNER ? "attention" : "healthy",
                "description", description(flag));
    }

    private static List<Map<String, Object>> history() {
        return Arrays.stream(FlagKey.values())
                .map(
                        flag ->
                                Map.<String, Object>of(
                                        "flagKey",
                                        flag.key(),
                                        "timestamp",
                                        time(flag.ordinal(), 1),
                                        "change",
                                        historyChange(flag),
                                        "from",
                                        historyFrom(flag),
                                        "to",
                                        historyTo(flag),
                                        "actor",
                                        flag.ordinal() % 2 == 0 ? "platform-team" : "release-bot",
                                        "environment",
                                        "development"))
                .toList();
    }

    private static List<Map<String, Object>> releases() {
        return Arrays.stream(FlagKey.values())
                .map(
                        flag ->
                                Map.<String, Object>of(
                                        "release",
                                        "rel-2026-%02d".formatted(flag.ordinal() + 11),
                                        "flagKey",
                                        flag.key(),
                                        "environment",
                                        "development",
                                        "rollout",
                                        rollout(flag),
                                        "health",
                                        flag == FlagKey.PAYMENT_V2_ENABLED ? "watch" : "healthy",
                                        "deployedAt",
                                        time(flag.ordinal(), 3),
                                        "summary",
                                        releaseSummary(flag)))
                .toList();
    }

    private static List<Map<String, Object>> errorLogs() {
        return Arrays.stream(FlagKey.values())
                .map(
                        flag ->
                                Map.<String, Object>of(
                                        "timestamp",
                                        time(flag.ordinal(), 5),
                                        "level",
                                        logLevel(flag),
                                        "flagKey",
                                        flag.key(),
                                        "service",
                                        service(flag),
                                        "message",
                                        errorMessage(flag),
                                        "requestId",
                                        "req-synthetic-%02d".formatted(flag.ordinal() + 1)))
                .toList();
    }

    private static List<Map<String, Object>> traces() {
        return Arrays.stream(FlagKey.values())
                .map(
                        flag ->
                                Map.<String, Object>of(
                                        "traceId",
                                        "trace-synthetic-%02d".formatted(flag.ordinal() + 1),
                                        "timestamp",
                                        time(flag.ordinal(), 7),
                                        "flagKey",
                                        flag.key(),
                                        "service",
                                        service(flag),
                                        "durationMs",
                                        18 + (flag.ordinal() * 7),
                                        "status",
                                        flag == FlagKey.PAYMENT_V2_ENABLED ? "DEGRADED" : "OK",
                                        "spans",
                                        4 + flag.ordinal()))
                .toList();
    }

    private static List<Map<String, Object>> sessions() {
        return Arrays.stream(FlagKey.values())
                .map(
                        flag ->
                                Map.<String, Object>of(
                                        "sessionId",
                                        "session-synthetic-%02d".formatted(flag.ordinal() + 1),
                                        "persona",
                                        persona(flag),
                                        "flagKey",
                                        flag.key(),
                                        "variation",
                                        displayValue(flag),
                                        "platform",
                                        platform(flag),
                                        "events",
                                        8 + (flag.ordinal() * 3),
                                        "lastSeen",
                                        time(flag.ordinal(), 2)))
                .toList();
    }

    private static String time(int ordinal, int hourOffset) {
        return GENERATED_AT.minus(hourOffset + ordinal, ChronoUnit.HOURS).toString();
    }

    private static String displayValue(FlagKey flag) {
        return flag == FlagKey.MAINTENANCE_BANNER
                ? "enabled=false · read-only"
                : flag.safeFallback().asText();
    }

    private static String label(FlagKey flag) {
        return switch (flag) {
            case CLIENT_NEW_PAYMENT_UI -> "Client payment UI";
            case CLIENT_NEW_HOME_EXPERIENCE -> "Client home experience";
            case PROFILE_RESPONSE_V2 -> "Profile response v2";
            case PAYMENT_API_MIGRATION -> "Payment API migration";
            case PAYMENT_V2_ENABLED -> "Payment v2 enabled";
            case FRAUD_ENGINE_VERSION -> "Fraud engine version";
            case NOTIFICATION_PROVIDER -> "Notification provider";
            case MAINTENANCE_BANNER -> "Maintenance banner";
        };
    }

    private static String owner(FlagKey flag) {
        return switch (flag) {
            case CLIENT_NEW_PAYMENT_UI -> "mobile-experience";
            case CLIENT_NEW_HOME_EXPERIENCE -> "mobile-experience";
            case PROFILE_RESPONSE_V2 -> "customer-profile";
            case PAYMENT_API_MIGRATION, PAYMENT_V2_ENABLED -> "payments";
            case FRAUD_ENGINE_VERSION -> "risk-platform";
            case NOTIFICATION_PROVIDER -> "customer-comms";
            case MAINTENANCE_BANNER -> "platform-operations";
        };
    }

    private static String description(FlagKey flag) {
        return switch (flag) {
            case CLIENT_NEW_PAYMENT_UI -> "Browser presentation experiment";
            case CLIENT_NEW_HOME_EXPERIENCE -> "Home experience A/B test";
            case PROFILE_RESPONSE_V2 -> "Typed profile response migration";
            case PAYMENT_API_MIGRATION -> "Payment authority migration stage";
            case PAYMENT_V2_ENABLED -> "Payment v2 safety gate";
            case FRAUD_ENGINE_VERSION -> "Fraud decision engine routing";
            case NOTIFICATION_PROVIDER -> "Notification provider routing";
            case MAINTENANCE_BANNER -> "Synthetic service status banner";
        };
    }

    private static String historyChange(FlagKey flag) {
        return switch (flag) {
            case CLIENT_NEW_PAYMENT_UI -> "Targeting rule updated";
            case CLIENT_NEW_HOME_EXPERIENCE -> "Home experience targeting updated";
            case PROFILE_RESPONSE_V2 -> "Pilot segment enabled";
            case PAYMENT_API_MIGRATION -> "Migration promoted";
            case PAYMENT_V2_ENABLED -> "Safety gate reviewed";
            case FRAUD_ENGINE_VERSION -> "Version targeting updated";
            case NOTIFICATION_PROVIDER -> "Provider rollout updated";
            case MAINTENANCE_BANNER -> "Default reviewed";
        };
    }

    private static String historyFrom(FlagKey flag) {
        return switch (flag) {
            case CLIENT_NEW_PAYMENT_UI,
                            CLIENT_NEW_HOME_EXPERIENCE,
                            PROFILE_RESPONSE_V2,
                            PAYMENT_V2_ENABLED ->
                    "false";
            case PAYMENT_API_MIGRATION -> "off";
            case FRAUD_ENGINE_VERSION -> "v1";
            case NOTIFICATION_PROVIDER -> "provider-a";
            case MAINTENANCE_BANNER -> "disabled";
        };
    }

    private static String historyTo(FlagKey flag) {
        return switch (flag) {
            case CLIENT_NEW_PAYMENT_UI, CLIENT_NEW_HOME_EXPERIENCE, PROFILE_RESPONSE_V2 ->
                    "true (pilot)";
            case PAYMENT_API_MIGRATION -> "shadow";
            case PAYMENT_V2_ENABLED -> "true";
            case FRAUD_ENGINE_VERSION -> "v2 (10%)";
            case NOTIFICATION_PROVIDER -> "provider-b (10%)";
            case MAINTENANCE_BANNER -> "disabled";
        };
    }

    private static String rollout(FlagKey flag) {
        return switch (flag) {
            case CLIENT_NEW_PAYMENT_UI -> "50%";
            case CLIENT_NEW_HOME_EXPERIENCE -> "50%";
            case PROFILE_RESPONSE_V2, FRAUD_ENGINE_VERSION, NOTIFICATION_PROVIDER -> "10%";
            case PAYMENT_API_MIGRATION, PAYMENT_V2_ENABLED, MAINTENANCE_BANNER -> "100%";
        };
    }

    private static String releaseSummary(FlagKey flag) {
        return switch (flag) {
            case CLIENT_NEW_PAYMENT_UI -> "New review flow exposure";
            case CLIENT_NEW_HOME_EXPERIENCE -> "Personalized home exposure";
            case PROFILE_RESPONSE_V2 -> "Profile payload compatibility";
            case PAYMENT_API_MIGRATION -> "Shadow comparison active";
            case PAYMENT_V2_ENABLED -> "Kill-switch protected route";
            case FRAUD_ENGINE_VERSION -> "Risk score parity check";
            case NOTIFICATION_PROVIDER -> "Provider B canary";
            case MAINTENANCE_BANNER -> "Safe default retained";
        };
    }

    private static String service(FlagKey flag) {
        return switch (flag) {
            case CLIENT_NEW_PAYMENT_UI -> "web-gateway";
            case CLIENT_NEW_HOME_EXPERIENCE -> "web-gateway";
            case PROFILE_RESPONSE_V2 -> "customer-profile-service";
            case PAYMENT_API_MIGRATION, PAYMENT_V2_ENABLED -> "payment-service";
            case FRAUD_ENGINE_VERSION -> "fraud-service";
            case NOTIFICATION_PROVIDER -> "notification-service";
            case MAINTENANCE_BANNER -> "dtm-service";
        };
    }

    private static String errorMessage(FlagKey flag) {
        return switch (flag) {
            case CLIENT_NEW_PAYMENT_UI -> "Client variation evaluated with synthetic context";
            case CLIENT_NEW_HOME_EXPERIENCE -> "Home variation evaluated with synthetic context";
            case PROFILE_RESPONSE_V2 -> "Legacy profile fallback remains available";
            case PAYMENT_API_MIGRATION -> "Shadow response comparison completed";
            case PAYMENT_V2_ENABLED -> "Safety gate trace recorded";
            case FRAUD_ENGINE_VERSION -> "Fraud v2 canary within expected range";
            case NOTIFICATION_PROVIDER -> "Provider A queue fallback is ready";
            case MAINTENANCE_BANNER -> "Maintenance flag uses safe default";
        };
    }

    private static String logLevel(FlagKey flag) {
        if (flag == FlagKey.PAYMENT_V2_ENABLED) {
            return "ERROR";
        }
        return flag.ordinal() % 2 == 0 ? "WARN" : "INFO";
    }

    private static String persona(FlagKey flag) {
        return switch (flag.ordinal() % 3) {
            case 0 -> "somchai-employee";
            case 1 -> "mali-pilot";
            default -> "narin-general";
        };
    }

    private static String platform(FlagKey flag) {
        return switch (flag.ordinal() % 3) {
            case 0 -> "android";
            case 1 -> "ios";
            default -> "web";
        };
    }
}
