package com.example.banking.contracts;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class NotificationContracts {
    private NotificationContracts() {}

    public record Request(
            @NotNull @Valid SyntheticContext context,
            @NotBlank @Size(max = 80) String paymentReference,
            String correlationId) {}

    public record Response(String status, String provider, DecisionMetadata decision) {}
}
