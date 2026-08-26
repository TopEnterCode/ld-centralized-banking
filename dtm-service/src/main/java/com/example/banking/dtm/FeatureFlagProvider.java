package com.example.banking.dtm;

import com.example.banking.contracts.FlagKey;
import com.example.banking.contracts.SyntheticContext;

public interface FeatureFlagProvider {
    ProviderDecision evaluate(FlagKey flag, SyntheticContext context);

    String mode();

    String status();

    boolean degraded();
}
