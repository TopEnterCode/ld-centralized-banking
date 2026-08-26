package com.example.banking.contracts;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record FlagEvaluationRequest(
        @NotBlank @Size(max = 100) String flagKey,
        @NotNull FlagValueType requestedType,
        @NotNull @Valid SyntheticContext context,
        @Size(max = 100) String correlationId) {}
