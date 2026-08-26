package com.example.banking.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.example.banking.contracts.DecisionSource;
import com.example.banking.contracts.FlagEvaluationResponse;
import com.example.banking.contracts.PaymentContracts;
import com.example.banking.contracts.SyntheticPersonas;
import com.example.banking.support.FlagDecisionClient;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentProcessingServiceTest {
    @Mock FlagDecisionClient client;

    @Test
    void migrationStagesChooseCorrectApi() {
        for (String stage : new String[] {"off", "shadow", "live", "complete"}) {
            mockDecisions(stage, true);
            PaymentProcessingService service =
                    new PaymentProcessingService(client, new PaymentFailureState());

            var response = service.pay(request());

            assertThat(response.migrationStage()).isEqualTo(stage);
            assertThat(response.authoritativeVersion())
                    .isEqualTo(stage.equals("live") || stage.equals("complete") ? "v2" : "v1");
            assertThat(response.calledVersions())
                    .containsExactlyElementsOf(
                            switch (stage) {
                                case "off" -> java.util.List.of("v1");
                                case "complete" -> java.util.List.of("v2");
                                default -> java.util.List.of("v1", "v2");
                            });
        }
    }

    @Test
    void killSwitchAlwaysMakesV1Authoritative() {
        mockDecisions("live", false);
        var response =
                new PaymentProcessingService(client, new PaymentFailureState()).pay(request());
        assertThat(response.authoritativeVersion()).isEqualTo("v1");
        assertThat(response.comparisonResult()).isEqualTo("v2-disabled-by-kill-switch");
        assertThat(response.usedFallback()).isTrue();
    }

    @Test
    void v2FailureFallsBackToV1() {
        mockDecisions("complete", true);
        PaymentFailureState state = new PaymentFailureState();
        state.v2Failure(true);
        var response = new PaymentProcessingService(client, state).pay(request());
        assertThat(response.authoritativeVersion()).isEqualTo("v1");
        assertThat(response.comparisonResult()).isEqualTo("v2-failed-safe-fallback");
    }

    private void mockDecisions(String stage, boolean enabled) {
        when(client.evaluate(
                        org.mockito.ArgumentMatchers.eq(
                                com.example.banking.contracts.FlagKey.PAYMENT_API_MIGRATION),
                        any(),
                        any()))
                .thenReturn(decision("payment-api-migration", TextNode.valueOf(stage)));
        when(client.evaluate(
                        org.mockito.ArgumentMatchers.eq(
                                com.example.banking.contracts.FlagKey.PAYMENT_V2_ENABLED),
                        any(),
                        any()))
                .thenReturn(decision("payment-v2-enabled", BooleanNode.valueOf(enabled)));
    }

    private FlagEvaluationResponse decision(
            String key, com.fasterxml.jackson.databind.JsonNode value) {
        return new FlagEvaluationResponse(
                key, value, "test", DecisionSource.MOCK, false, Instant.now(), "test");
    }

    private PaymentContracts.Request request() {
        return new PaymentContracts.Request(
                SyntheticPersonas.NARIN_GENERAL,
                "Synthetic Merchant",
                new BigDecimal("1250.00"),
                "test-correlation");
    }
}
