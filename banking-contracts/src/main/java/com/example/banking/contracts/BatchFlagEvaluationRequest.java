package com.example.banking.contracts;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record BatchFlagEvaluationRequest(
        @NotEmpty @Size(max = 20) List<@Valid FlagEvaluationRequest> evaluations) {}
