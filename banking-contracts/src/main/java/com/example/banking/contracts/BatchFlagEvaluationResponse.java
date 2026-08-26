package com.example.banking.contracts;

import java.util.List;

public record BatchFlagEvaluationResponse(List<FlagEvaluationResponse> evaluations) {}
