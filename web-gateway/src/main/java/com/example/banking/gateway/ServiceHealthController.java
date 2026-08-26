package com.example.banking.gateway;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

@RestController
@RequestMapping("/api/health")
public class ServiceHealthController {
    private final Map<String, RestClient> clients;

    public ServiceHealthController(Map<String, RestClient> backendClients) {
        this.clients = backendClients;
    }

    @GetMapping("/services")
    Map<String, Object> services() {
        Map<String, Object> statuses = new LinkedHashMap<>();
        clients.forEach((name, client) -> statuses.put(name, health(client)));
        return Map.of("services", statuses, "timestamp", Instant.now());
    }

    private String health(RestClient client) {
        try {
            Map<?, ?> response = client.get().uri("/actuator/health").retrieve().body(Map.class);
            return response != null && "UP".equals(response.get("status")) ? "healthy" : "degraded";
        } catch (RuntimeException exception) {
            return "unavailable";
        }
    }
}
