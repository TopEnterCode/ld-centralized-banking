package com.example.banking.notification;

import com.example.banking.contracts.DecisionMetadata;
import com.example.banking.contracts.DecisionSource;
import com.example.banking.contracts.FlagKey;
import com.example.banking.contracts.NotificationContracts;
import com.example.banking.support.FlagDecisionClient;
import com.fasterxml.jackson.databind.node.TextNode;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {
    private final FlagDecisionClient decisionClient;
    private final NotificationFailureState failureState;

    public NotificationController(
            FlagDecisionClient decisionClient, NotificationFailureState failureState) {
        this.decisionClient = decisionClient;
        this.failureState = failureState;
    }

    @PostMapping
    NotificationContracts.Response notify(
            @Valid @RequestBody NotificationContracts.Request request) {
        var decision =
                decisionClient.evaluate(
                        FlagKey.NOTIFICATION_PROVIDER, request.context(), request.correlationId());
        String requested = decision.value().asText("provider-a");
        if ("provider-b".equals(requested) && failureState.providerBFailure()) {
            return new NotificationContracts.Response(
                    "queued-after-provider-b-failure",
                    "provider-a",
                    new DecisionMetadata(
                            "notification-service",
                            decision.flagKey(),
                            TextNode.valueOf("provider-a"),
                            "provider B failed; queued with safe provider A fallback",
                            DecisionSource.SERVICE_FALLBACK,
                            true,
                            Instant.now(),
                            decision.correlationId()));
        }
        String provider = "provider-b".equals(requested) ? "provider-b" : "provider-a";
        return new NotificationContracts.Response(
                "synthetic-notification-sent",
                provider,
                DecisionMetadata.from("notification-service", decision));
    }

    @PostMapping("/demo/provider-b-failure")
    Map<String, Boolean> providerBFailure(@RequestBody Map<String, Boolean> request) {
        failureState.providerBFailure(Boolean.TRUE.equals(request.get("enabled")));
        return Map.of("providerBFailure", failureState.providerBFailure());
    }
}
