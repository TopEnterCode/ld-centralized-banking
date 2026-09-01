package com.example.banking.contracts;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class JourneyContracts {
    private JourneyContracts() {}

    public record Request(
            @NotNull @Valid SyntheticContext context,
            @NotBlank @Size(max = 60) String recipientAlias,
            @NotNull @DecimalMin("1.00") @DecimalMax("50000.00") BigDecimal amount) {}

    public record TimelineEvent(
            String service,
            String title,
            String detail,
            String source,
            boolean degraded,
            Instant timestamp) {}

    public record Response(
            String correlationId,
            boolean success,
            boolean degraded,
            ProfileContracts.Response profile,
            FraudContracts.Response fraud,
            PaymentContracts.Response payment,
            NotificationContracts.Response notification,
            List<TimelineEvent> timeline,
            MaintenanceContracts.Status maintenance) {}
}
