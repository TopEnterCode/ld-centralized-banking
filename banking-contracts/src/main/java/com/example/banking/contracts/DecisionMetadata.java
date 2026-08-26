package com.example.banking.contracts;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

public record DecisionMetadata(
        String service,
        String flagKey,
        JsonNode value,
        String reason,
        DecisionSource source,
        boolean usedFallback,
        Instant timestamp,
        String correlationId) {
    public static DecisionMetadata from(String service, FlagEvaluationResponse response) {
        return new DecisionMetadata(
                service,
                response.flagKey(),
                response.value(),
                response.reason(),
                response.source(),
                response.usedFallback(),
                response.timestamp(),
                response.correlationId());
    }
}
