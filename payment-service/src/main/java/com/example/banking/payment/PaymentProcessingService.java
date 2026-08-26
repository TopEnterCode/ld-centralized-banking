package com.example.banking.payment;

import com.example.banking.contracts.DecisionMetadata;
import com.example.banking.contracts.FlagEvaluationResponse;
import com.example.banking.contracts.FlagKey;
import com.example.banking.contracts.PaymentContracts;
import com.example.banking.support.FlagDecisionClient;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class PaymentProcessingService {
    private static final Set<String> STAGES = Set.of("off", "shadow", "live", "complete");
    private final FlagDecisionClient decisionClient;
    private final PaymentFailureState failureState;

    public PaymentProcessingService(
            FlagDecisionClient decisionClient, PaymentFailureState failureState) {
        this.decisionClient = decisionClient;
        this.failureState = failureState;
    }

    public PaymentContracts.Response pay(PaymentContracts.Request request) {
        FlagEvaluationResponse migration =
                decisionClient.evaluate(
                        FlagKey.PAYMENT_API_MIGRATION, request.context(), request.correlationId());
        FlagEvaluationResponse v2Enabled =
                decisionClient.evaluate(
                        FlagKey.PAYMENT_V2_ENABLED, request.context(), request.correlationId());
        String requestedStage = migration.value().asText("off");
        String stage = STAGES.contains(requestedStage) ? requestedStage : "off";
        boolean allowV2 = v2Enabled.value().asBoolean(false);
        List<DecisionMetadata> decisions =
                List.of(
                        DecisionMetadata.from("payment-service", migration),
                        DecisionMetadata.from("payment-service", v2Enabled));
        List<String> called = new ArrayList<>();
        boolean fallback = migration.usedFallback() || v2Enabled.usedFallback();

        if ("off".equals(stage) || !allowV2) {
            VersionResult v1 = callV1(request, called);
            return response(
                    request,
                    v1,
                    called,
                    "v1",
                    allowV2 ? "not-compared" : "v2-disabled-by-kill-switch",
                    stage,
                    fallback || !allowV2,
                    decisions,
                    migration.reason());
        }

        if ("complete".equals(stage)) {
            try {
                VersionResult v2 = callV2(request, called);
                return response(
                        request,
                        v2,
                        called,
                        "v2",
                        "not-compared",
                        stage,
                        fallback,
                        decisions,
                        migration.reason());
            } catch (PaymentV2Exception exception) {
                VersionResult v1 = callV1(request, called);
                return response(
                        request,
                        v1,
                        called,
                        "v1",
                        "v2-failed-safe-fallback",
                        stage,
                        true,
                        decisions,
                        migration.reason());
            }
        }

        VersionResult v1 = callV1(request, called);
        try {
            VersionResult v2 = callV2(request, called);
            boolean shadow = "shadow".equals(stage);
            VersionResult authoritative = shadow ? v1 : v2;
            return response(
                    request,
                    authoritative,
                    called,
                    shadow ? "v1" : "v2",
                    compare(v1, v2),
                    stage,
                    fallback,
                    decisions,
                    migration.reason());
        } catch (PaymentV2Exception exception) {
            return response(
                    request,
                    v1,
                    called,
                    "v1",
                    "v2-failed-safe-fallback",
                    stage,
                    true,
                    decisions,
                    migration.reason());
        }
    }

    private VersionResult callV1(PaymentContracts.Request request, List<String> called) {
        called.add("v1");
        return new VersionResult(request.amount(), fee(request.amount()), "accepted");
    }

    private VersionResult callV2(PaymentContracts.Request request, List<String> called) {
        called.add("v2");
        if (failureState.v2Failure()) {
            throw new PaymentV2Exception();
        }
        return new VersionResult(request.amount(), fee(request.amount()), "accepted");
    }

    private BigDecimal fee(BigDecimal amount) {
        return amount.multiply(new BigDecimal("0.001")).setScale(2, RoundingMode.HALF_UP);
    }

    private String compare(VersionResult v1, VersionResult v2) {
        return v1.equals(v2) ? "matched" : "mismatch";
    }

    private PaymentContracts.Response response(
            PaymentContracts.Request request,
            VersionResult result,
            List<String> called,
            String authoritative,
            String comparison,
            String stage,
            boolean fallback,
            List<DecisionMetadata> decisions,
            String reason) {
        String reference =
                "SYN-%s-%s"
                        .formatted(
                                authoritative.toUpperCase(),
                                Integer.toUnsignedString(
                                                (request.correlationId() + result.amount())
                                                        .hashCode(),
                                                36)
                                        .toUpperCase());
        return new PaymentContracts.Response(
                true,
                reference,
                List.copyOf(called),
                authoritative,
                comparison,
                stage,
                reason,
                fallback,
                decisions);
    }

    private record VersionResult(BigDecimal amount, BigDecimal fee, String status) {}

    private static final class PaymentV2Exception extends RuntimeException {}
}
