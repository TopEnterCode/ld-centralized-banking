package com.example.banking.support;

import com.example.banking.contracts.DecisionSource;
import com.example.banking.contracts.FlagEvaluationRequest;
import com.example.banking.contracts.FlagEvaluationResponse;
import com.example.banking.contracts.FlagKey;
import com.example.banking.contracts.SyntheticContext;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

public final class ResilientDtmClient implements FlagDecisionClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(ResilientDtmClient.class);

    private final RestClient restClient;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;

    public ResilientDtmClient(RestClient restClient, CircuitBreaker circuitBreaker) {
        this.restClient = restClient;
        this.circuitBreaker = circuitBreaker;
        this.retry =
                Retry.of(
                        "dtm-retry",
                        RetryConfig.custom()
                                .maxAttempts(2)
                                .waitDuration(Duration.ofMillis(40))
                                .retryExceptions(RuntimeException.class)
                                .build());
    }

    @Override
    public FlagEvaluationResponse evaluate(
            FlagKey flag, SyntheticContext context, String correlationId) {
        FlagEvaluationRequest request =
                new FlagEvaluationRequest(flag.key(), flag.type(), context, correlationId);
        Supplier<FlagEvaluationResponse> remoteCall =
                () ->
                        restClient
                                .post()
                                .uri("/api/v1/flags/evaluate")
                                .header("X-Correlation-ID", correlationId)
                                .body(request)
                                .retrieve()
                                .body(FlagEvaluationResponse.class);
        try {
            FlagEvaluationResponse response =
                    circuitBreaker.executeSupplier(Retry.decorateSupplier(retry, remoteCall));
            if (response == null) {
                throw new IllegalStateException("DTM returned an empty response");
            }
            return response;
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "DTM decision failed; using local fallback flag={} correlationId={} cause={}",
                    flag.key(),
                    correlationId,
                    exception.getClass().getSimpleName());
            return new FlagEvaluationResponse(
                    flag.key(),
                    flag.safeFallback(),
                    "DTM unavailable; service applied typed local fallback",
                    DecisionSource.SERVICE_FALLBACK,
                    true,
                    Instant.now(),
                    correlationId);
        }
    }
}
