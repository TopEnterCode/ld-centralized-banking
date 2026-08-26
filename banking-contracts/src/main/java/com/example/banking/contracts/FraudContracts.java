package com.example.banking.contracts;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public final class FraudContracts {
    private FraudContracts() {}

    public record Request(
            @NotNull @Valid SyntheticContext context,
            @NotNull @DecimalMin("1.00") @DecimalMax("50000.00") BigDecimal amount,
            String correlationId) {}

    public record Response(
            String engineVersion, String riskLevel, int score, DecisionMetadata decision) {}
}
