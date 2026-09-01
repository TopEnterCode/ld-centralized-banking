package com.example.banking.gateway;

import com.example.banking.contracts.DecisionMetadata;
import com.example.banking.contracts.DecisionSource;
import com.example.banking.contracts.FlagKey;
import com.example.banking.contracts.FraudContracts;
import com.example.banking.contracts.JourneyContracts;
import com.example.banking.contracts.MaintenanceContracts;
import com.example.banking.contracts.NotificationContracts;
import com.example.banking.contracts.PaymentContracts;
import com.example.banking.contracts.ProfileContracts;
import com.example.banking.support.FlagDecisionClient;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class JourneyOrchestrator {
    private static final Logger LOGGER = LoggerFactory.getLogger(JourneyOrchestrator.class);
    private final Map<String, RestClient> clients;
    private final FlagDecisionClient flagDecisionClient;

    public JourneyOrchestrator(Map<String, RestClient> backendClients) {
        this(backendClients, null);
    }

    @Autowired
    public JourneyOrchestrator(
            Map<String, RestClient> backendClients, FlagDecisionClient flagDecisionClient) {
        this.clients = backendClients;
        this.flagDecisionClient = flagDecisionClient;
    }

    public JourneyContracts.Response run(JourneyContracts.Request request) {
        String correlationId = UUID.randomUUID().toString();
        MaintenanceContracts.Status maintenance = maintenance(request, correlationId);
        if (maintenance.enabled()) {
            return maintenanceResponse(request, correlationId, maintenance);
        }
        ProfileContracts.Response profile = profile(request, correlationId);
        FraudContracts.Response fraud = fraud(request, correlationId);
        PaymentContracts.Response payment = payment(request, correlationId);
        NotificationContracts.Response notification =
                notification(request, payment.paymentReference(), correlationId);
        List<JourneyContracts.TimelineEvent> timeline = new ArrayList<>();
        timeline.add(
                event(profile.decision(), "Customer profile resolved", profile.responseVersion()));
        timeline.add(event(fraud.decision(), "Fraud engine selected", fraud.engineVersion()));
        payment.decisions()
                .forEach(
                        decision ->
                                timeline.add(
                                        event(
                                                decision,
                                                decision.flagKey().equals("payment-api-migration")
                                                        ? "Payment migration evaluated"
                                                        : "Payment kill switch checked",
                                                decision.value().asText())));
        timeline.add(
                new JourneyContracts.TimelineEvent(
                        "payment-service",
                        "Payment route completed",
                        "%s authoritative; called %s; %s"
                                .formatted(
                                        payment.authoritativeVersion(),
                                        String.join(" + ", payment.calledVersions()),
                                        payment.comparisonResult()),
                        payment.usedFallback() ? "service-fallback" : "workflow",
                        payment.usedFallback(),
                        Instant.now()));
        timeline.add(
                event(
                        notification.decision(),
                        "Notification provider selected",
                        "%s · %s".formatted(notification.provider(), notification.status())));
        boolean degraded =
                timeline.stream().anyMatch(JourneyContracts.TimelineEvent::degraded)
                        || notification.status().contains("failure");
        return new JourneyContracts.Response(
                correlationId,
                payment.success(),
                degraded,
                profile,
                fraud,
                payment,
                notification,
                List.copyOf(timeline),
                maintenance);
    }

    private MaintenanceContracts.Status maintenance(
            JourneyContracts.Request request, String correlationId) {
        if (flagDecisionClient == null) {
            return MaintenanceContracts.Status.safeDefault(correlationId);
        }
        var evaluation =
                flagDecisionClient.evaluate(
                        FlagKey.MAINTENANCE_BANNER, request.context(), correlationId);
        return new MaintenanceContracts.Status(
                MaintenanceContracts.Configuration.from(evaluation.value()),
                DecisionMetadata.from("web-gateway", evaluation));
    }

    private JourneyContracts.Response maintenanceResponse(
            JourneyContracts.Request request,
            String correlationId,
            MaintenanceContracts.Status maintenance) {
        DecisionMetadata decision = maintenance.decision();
        MaintenanceContracts.Configuration configuration = maintenance.configuration();
        ProfileContracts.Response profile =
                new ProfileContracts.Response(
                        "legacy",
                        "Synthetic Customer",
                        request.context().tier(),
                        Map.of("theme", "classic"),
                        decision);
        FraudContracts.Response fraud = new FraudContracts.Response("v1", "not-run", 0, decision);
        PaymentContracts.Response payment =
                new PaymentContracts.Response(
                        false,
                        "SYN-MAINTENANCE",
                        List.of(),
                        "none",
                        "not-run",
                        "maintenance",
                        configuration.message(),
                        false,
                        List.of(decision));
        NotificationContracts.Response notification =
                new NotificationContracts.Response("not-sent-maintenance", "provider-a", decision);
        JourneyContracts.TimelineEvent event =
                new JourneyContracts.TimelineEvent(
                        "web-gateway",
                        "Maintenance guard enforced",
                        "%s · %s · no payment services called"
                                .formatted(configuration.title(), configuration.message()),
                        decision.source().value(),
                        false,
                        Instant.now());
        return new JourneyContracts.Response(
                correlationId,
                false,
                false,
                profile,
                fraud,
                payment,
                notification,
                List.of(event),
                maintenance);
    }

    private ProfileContracts.Response profile(
            JourneyContracts.Request request, String correlationId) {
        try {
            return clients.get("profile")
                    .post()
                    .uri("/api/v1/profiles/view")
                    .header("X-Correlation-ID", correlationId)
                    .body(new ProfileContracts.Request(request.context(), correlationId))
                    .retrieve()
                    .body(ProfileContracts.Response.class);
        } catch (RuntimeException exception) {
            logFallback("profile", correlationId, exception);
            return new ProfileContracts.Response(
                    "legacy",
                    "Synthetic Customer",
                    request.context().tier(),
                    Map.of("theme", "classic"),
                    fallback(
                            "customer-profile-service",
                            FlagKey.PROFILE_RESPONSE_V2,
                            correlationId));
        }
    }

    private FraudContracts.Response fraud(JourneyContracts.Request request, String correlationId) {
        try {
            return clients.get("fraud")
                    .post()
                    .uri("/api/v1/fraud/assess")
                    .header("X-Correlation-ID", correlationId)
                    .body(
                            new FraudContracts.Request(
                                    request.context(), request.amount(), correlationId))
                    .retrieve()
                    .body(FraudContracts.Response.class);
        } catch (RuntimeException exception) {
            logFallback("fraud", correlationId, exception);
            return new FraudContracts.Response(
                    "v1",
                    "review",
                    40,
                    fallback("fraud-service", FlagKey.FRAUD_ENGINE_VERSION, correlationId));
        }
    }

    private PaymentContracts.Response payment(
            JourneyContracts.Request request, String correlationId) {
        try {
            return clients.get("payment")
                    .post()
                    .uri("/api/v1/payments")
                    .header("X-Correlation-ID", correlationId)
                    .body(
                            new PaymentContracts.Request(
                                    request.context(),
                                    request.recipientAlias(),
                                    request.amount(),
                                    correlationId))
                    .retrieve()
                    .body(PaymentContracts.Response.class);
        } catch (RuntimeException exception) {
            logFallback("payment", correlationId, exception);
            return new PaymentContracts.Response(
                    true,
                    "SYN-FALLBACK-QUEUED",
                    List.of("v1"),
                    "v1",
                    "payment-service-unavailable",
                    "off",
                    "Gateway queued a synthetic v1 payment response",
                    true,
                    List.of(
                            fallback(
                                    "payment-service",
                                    FlagKey.PAYMENT_API_MIGRATION,
                                    correlationId),
                            fallback(
                                    "payment-service", FlagKey.PAYMENT_V2_ENABLED, correlationId)));
        }
    }

    private NotificationContracts.Response notification(
            JourneyContracts.Request request, String paymentReference, String correlationId) {
        try {
            return clients.get("notification")
                    .post()
                    .uri("/api/v1/notifications")
                    .header("X-Correlation-ID", correlationId)
                    .body(
                            new NotificationContracts.Request(
                                    request.context(), paymentReference, correlationId))
                    .retrieve()
                    .body(NotificationContracts.Response.class);
        } catch (RuntimeException exception) {
            logFallback("notification", correlationId, exception);
            return new NotificationContracts.Response(
                    "queued",
                    "provider-a",
                    fallback("notification-service", FlagKey.NOTIFICATION_PROVIDER, correlationId));
        }
    }

    private DecisionMetadata fallback(String service, FlagKey flag, String correlationId) {
        return new DecisionMetadata(
                service,
                flag.key(),
                flag.safeFallback(),
                "Backend service unavailable; gateway used synthetic safe fallback",
                DecisionSource.SERVICE_FALLBACK,
                true,
                Instant.now(),
                correlationId);
    }

    private JourneyContracts.TimelineEvent event(
            DecisionMetadata decision, String title, String result) {
        return new JourneyContracts.TimelineEvent(
                decision.service(),
                title,
                "%s → %s (%s)".formatted(decision.flagKey(), result, decision.reason()),
                decision.source().value(),
                decision.usedFallback(),
                decision.timestamp());
    }

    private void logFallback(String service, String correlationId, RuntimeException exception) {
        LOGGER.warn(
                "Backend call failed; using journey fallback service={} correlationId={} cause={}",
                service,
                correlationId,
                exception.getClass().getSimpleName(),
                exception);
    }
}
