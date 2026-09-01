package com.example.banking.gateway;

import com.example.banking.contracts.FlagEvaluationResponse;
import com.example.banking.contracts.FlagKey;
import com.example.banking.contracts.JourneyContracts;
import com.example.banking.contracts.MaintenanceContracts;
import com.example.banking.contracts.SyntheticContext;
import com.example.banking.contracts.SyntheticPersonas;
import com.example.banking.support.FlagDecisionClient;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class DemoGatewayController {
    private final JourneyOrchestrator orchestrator;
    private final FlagDecisionClient flagDecisionClient;
    private final String mode;
    private final String clientSideId;
    private final boolean adminControlsEnabled;
    private final String environmentKey;

    public DemoGatewayController(
            JourneyOrchestrator orchestrator,
            FlagDecisionClient flagDecisionClient,
            @Value("${poc.mode:mock}") String mode,
            @Value("${launchdarkly.client-side-id:}") String clientSideId,
            @Value("${launchdarkly.admin-controls-enabled:false}") boolean adminControlsEnabled,
            @Value("${launchdarkly.environment-key:devolopment}") String environmentKey) {
        this.orchestrator = orchestrator;
        this.flagDecisionClient = flagDecisionClient;
        this.mode = mode;
        this.clientSideId = clientSideId;
        this.adminControlsEnabled = adminControlsEnabled;
        this.environmentKey = environmentKey;
    }

    @GetMapping("/runtime")
    Map<String, Object> runtime() {
        boolean live = "launchdarkly".equals(mode);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("mode", live ? "launchdarkly" : "mock");
        response.put("modeLabel", live ? "LAUNCHDARKLY LIVE" : "MOCK MODE");
        response.put("clientSideId", live ? clientSideId : "");
        response.put(
                "clientFlagKeys",
                List.of(
                        FlagKey.CLIENT_NEW_PAYMENT_UI.key(),
                        FlagKey.CLIENT_NEW_HOME_EXPERIENCE.key()));
        response.put("clientSdkConfigured", live && !clientSideId.isBlank());
        response.put(
                "adminControlsAvailable",
                live
                        && adminControlsEnabled
                        && !environmentKey.equalsIgnoreCase("prod")
                        && !environmentKey.equalsIgnoreCase("production"));
        response.put("syntheticDataOnly", true);
        response.put("timestamp", Instant.now());
        return response;
    }

    @GetMapping("/personas")
    List<SyntheticContext> personas() {
        return SyntheticPersonas.named();
    }

    @GetMapping("/accounts/{personaKey}")
    Map<String, Object> account(@PathVariable String personaKey) {
        SyntheticContext context = context(personaKey);
        return Map.of(
                "owner",
                context.key(),
                "accountAlias",
                "Synthetic Everyday Account",
                "maskedReference",
                "DEMO-••••-2048",
                "availableBalance",
                new BigDecimal("245680.50"),
                "currency",
                "THB",
                "asOf",
                Instant.now());
    }

    @PostMapping("/journey")
    JourneyContracts.Response journey(@Valid @RequestBody JourneyContracts.Request request) {
        return orchestrator.run(request);
    }

    @GetMapping("/maintenance/{personaKey}")
    MaintenanceContracts.Status maintenance(@PathVariable String personaKey) {
        SyntheticContext context = context(personaKey);
        var evaluation =
                flagDecisionClient.evaluate(
                        FlagKey.MAINTENANCE_BANNER, context, UUID.randomUUID().toString());
        return new MaintenanceContracts.Status(
                MaintenanceContracts.Configuration.from(evaluation.value()),
                com.example.banking.contracts.DecisionMetadata.from("web-gateway", evaluation));
    }

    @PostMapping("/browser/evaluate")
    FlagEvaluationResponse browserEvaluate(@Valid @RequestBody BrowserEvaluation request) {
        FlagKey flag =
                FlagKey.fromKey(request.flagKey())
                        .filter(FlagKey::clientSide)
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.FORBIDDEN,
                                                "Only registered client-side flags may be requested"));
        if (flag.type() != com.example.banking.contracts.FlagValueType.BOOLEAN) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Only registered client-side flags may be requested");
        }
        return flagDecisionClient.evaluate(
                flag,
                request.context(),
                Optional.ofNullable(request.correlationId())
                        .filter(value -> !value.isBlank())
                        .orElseGet(() -> UUID.randomUUID().toString()));
    }

    @GetMapping("/demo/rollout")
    Map<String, Object> rollout() {
        List<Map<String, Object>> assignments =
                SyntheticPersonas.rolloutUsers().stream()
                        .map(
                                context -> {
                                    FlagEvaluationResponse response =
                                            flagDecisionClient.evaluate(
                                                    FlagKey.CLIENT_NEW_PAYMENT_UI,
                                                    context,
                                                    "rollout-" + context.key());
                                    return Map.<String, Object>of(
                                            "key",
                                            context.key(),
                                            "enabled",
                                            response.value().asBoolean(false),
                                            "reason",
                                            response.reason(),
                                            "source",
                                            response.source().value());
                                })
                        .toList();
        long enabled =
                assignments.stream().filter(row -> Boolean.TRUE.equals(row.get("enabled"))).count();
        return Map.of(
                "assignments", assignments,
                "enabledCount", enabled,
                "disabledCount", assignments.size() - enabled);
    }

    @GetMapping("/monitoring")
    Map<String, Object> monitoring() {
        return MonitoringData.snapshot("launchdarkly".equals(mode) ? "launchdarkly" : "mock");
    }

    private SyntheticContext context(String key) {
        return SyntheticPersonas.find(key)
                .orElseThrow(
                        () ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND, "Unknown synthetic persona"));
    }

    public record BrowserEvaluation(
            String flagKey, @Valid SyntheticContext context, String correlationId) {}
}
