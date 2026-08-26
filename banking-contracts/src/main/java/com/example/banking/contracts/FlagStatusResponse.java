package com.example.banking.contracts;

import java.time.Instant;
import java.util.List;

public record FlagStatusResponse(
        String mode,
        String providerStatus,
        boolean degraded,
        List<String> registeredFlags,
        Instant timestamp) {}
