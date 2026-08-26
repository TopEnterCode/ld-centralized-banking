package com.example.banking.gateway;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PresenterControlService {
    private final Map<String, RestClient> clients;
    private final String mode;
    private final boolean adminControlsEnabled;
    private final String adminPin;
    private final String environmentKey;

    public PresenterControlService(
            Map<String, RestClient> backendClients,
            @Value("${poc.mode:mock}") String mode,
            @Value("${launchdarkly.admin-controls-enabled:false}") boolean adminControlsEnabled,
            @Value("${launchdarkly.admin-pin:}") String adminPin,
            @Value("${launchdarkly.environment-key:devolopment}") String environmentKey) {
        this.clients = backendClients;
        this.mode = mode;
        this.adminControlsEnabled = adminControlsEnabled;
        this.adminPin = adminPin;
        this.environmentKey = environmentKey;
    }

    public Map<String, Object> apply(ControlRequest request) {
        if ("launchdarkly".equals(mode)) {
            validateLiveAdmin(request.pin());
            throw new ResponseStatusException(
                    HttpStatus.NOT_IMPLEMENTED,
                    "Live account mutations are intentionally performed by the guarded bootstrap tool; this runtime does not write externally");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        switch (request.action()) {
            case "payment-v2-failure" ->
                    result.put(
                            "payment",
                            post(
                                    "payment",
                                    "/api/v1/payments/demo/v2-failure",
                                    Map.of("enabled", true)));
            case "notification-b-failure" ->
                    result.put(
                            "notification",
                            post(
                                    "notification",
                                    "/api/v1/notifications/demo/provider-b-failure",
                                    Map.of("enabled", true)));
            case "restore-all", "reset" -> {
                result.put(
                        "dtm",
                        post(
                                "dtm",
                                "/api/v1/demo/control",
                                Map.of("action", request.action(), "value", "")));
                result.put(
                        "payment",
                        post(
                                "payment",
                                "/api/v1/payments/demo/v2-failure",
                                Map.of("enabled", false)));
                result.put(
                        "notification",
                        post(
                                "notification",
                                "/api/v1/notifications/demo/provider-b-failure",
                                Map.of("enabled", false)));
            }
            default ->
                    result.put(
                            "dtm",
                            post(
                                    "dtm",
                                    "/api/v1/demo/control",
                                    Map.of(
                                            "action",
                                            request.action(),
                                            "value",
                                            request.value() == null ? "" : request.value())));
        }
        result.put("accepted", true);
        return result;
    }

    public Map<String, Object> state() {
        Object response =
                clients.get("dtm").get().uri("/api/v1/demo/state").retrieve().body(Object.class);
        if (!(response instanceof Map<?, ?> values)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY, "DTM returned an invalid control state");
        }
        Map<String, Object> typed = new LinkedHashMap<>();
        values.forEach(
                (key, value) -> {
                    if (key instanceof String textKey) {
                        typed.put(textKey, value);
                    }
                });
        return Map.copyOf(typed);
    }

    private Object post(String client, String path, Object body) {
        return clients.get(client).post().uri(path).body(body).retrieve().body(Object.class);
    }

    private void validateLiveAdmin(String suppliedPin) {
        boolean production =
                environmentKey.equalsIgnoreCase("prod")
                        || environmentKey.equalsIgnoreCase("production");
        if (!adminControlsEnabled
                || adminPin.isBlank()
                || !adminPin.equals(suppliedPin)
                || production) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Live admin controls require enablement, token, non-production environment, and valid PIN");
        }
    }

    public record ControlRequest(String action, String value, String pin) {}
}
