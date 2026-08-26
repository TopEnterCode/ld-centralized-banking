package com.example.banking.contracts;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

public final class ProfileContracts {
    private ProfileContracts() {}

    public record Request(@NotNull @Valid SyntheticContext context, String correlationId) {}

    public record Response(
            String responseVersion,
            String displayName,
            String tier,
            Map<String, String> preferences,
            DecisionMetadata decision) {}
}
