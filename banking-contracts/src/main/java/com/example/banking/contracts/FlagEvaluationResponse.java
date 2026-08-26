package com.example.banking.contracts;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

public record FlagEvaluationResponse(
        String flagKey,
        JsonNode value,
        String reason,
        DecisionSource source,
        boolean usedFallback,
        Instant timestamp,
        String correlationId) {}
