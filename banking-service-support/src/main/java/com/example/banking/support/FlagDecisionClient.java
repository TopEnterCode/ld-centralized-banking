package com.example.banking.support;

import com.example.banking.contracts.FlagEvaluationResponse;
import com.example.banking.contracts.FlagKey;
import com.example.banking.contracts.SyntheticContext;

public interface FlagDecisionClient {
    FlagEvaluationResponse evaluate(FlagKey flag, SyntheticContext context, String correlationId);
}
