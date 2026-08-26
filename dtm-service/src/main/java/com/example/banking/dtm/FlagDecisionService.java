package com.example.banking.dtm;

import com.example.banking.contracts.BatchFlagEvaluationRequest;
import com.example.banking.contracts.BatchFlagEvaluationResponse;
import com.example.banking.contracts.DecisionSource;
import com.example.banking.contracts.FlagEvaluationRequest;
import com.example.banking.contracts.FlagEvaluationResponse;
import com.example.banking.contracts.FlagKey;
import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class FlagDecisionService {
    private final FeatureFlagProvider provider;

    public FlagDecisionService(FeatureFlagProvider provider) {
        this.provider = provider;
    }

    public FlagEvaluationResponse evaluate(FlagEvaluationRequest request) {
        FlagKey flag =
                FlagKey.fromKey(request.flagKey())
                        .orElseThrow(() -> new UnknownFlagException(request.flagKey()));
        if (flag.type() != request.requestedType()) {
            throw new IncorrectFlagTypeException(flag, request.requestedType().name());
        }
        try {
            ProviderDecision decision = provider.evaluate(flag, request.context());
            return new FlagEvaluationResponse(
                    flag.key(),
                    decision.value(),
                    decision.reason(),
                    decision.source(),
                    decision.usedFallback(),
                    Instant.now(),
                    request.correlationId());
        } catch (ProviderUnavailableException exception) {
            return new FlagEvaluationResponse(
                    flag.key(),
                    flag.safeFallback(),
                    "Provider unavailable; DTM used typed SDK default",
                    DecisionSource.SDK_DEFAULT,
                    true,
                    Instant.now(),
                    request.correlationId());
        }
    }

    public BatchFlagEvaluationResponse evaluate(BatchFlagEvaluationRequest request) {
        return new BatchFlagEvaluationResponse(
                request.evaluations().stream().map(this::evaluate).toList());
    }
}
