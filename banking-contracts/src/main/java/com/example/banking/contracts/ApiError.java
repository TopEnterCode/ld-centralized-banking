package com.example.banking.contracts;

import java.time.Instant;
import java.util.List;

public record ApiError(
        String type,
        String title,
        int status,
        String detail,
        String instance,
        String correlationId,
        Instant timestamp,
        List<String> violations) {}
