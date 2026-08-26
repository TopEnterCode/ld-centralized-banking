package com.example.banking.contracts;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

public final class PaymentContracts {
    private PaymentContracts() {}

    public record Request(
            @NotNull @Valid SyntheticContext context,
            @NotBlank @Size(max = 60) String recipientAlias,
            @NotNull @DecimalMin("1.00") @DecimalMax("50000.00") BigDecimal amount,
            String correlationId) {}

    public record Response(
            boolean success,
            String paymentReference,
            List<String> calledVersions,
            String authoritativeVersion,
            String comparisonResult,
            String migrationStage,
            String evaluationReason,
            boolean usedFallback,
            List<DecisionMetadata> decisions) {}
}
