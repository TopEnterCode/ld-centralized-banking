package com.example.banking.dtm;

import com.example.banking.contracts.DecisionSource;
import com.fasterxml.jackson.databind.JsonNode;

public record ProviderDecision(
        JsonNode value, String reason, DecisionSource source, boolean usedFallback) {}
