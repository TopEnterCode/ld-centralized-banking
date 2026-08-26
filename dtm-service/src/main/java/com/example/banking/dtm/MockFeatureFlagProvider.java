package com.example.banking.dtm;

import com.example.banking.contracts.DecisionSource;
import com.example.banking.contracts.FlagKey;
import com.example.banking.contracts.SyntheticContext;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile({"mock", "test"})
public final class MockFeatureFlagProvider implements FeatureFlagProvider {
    private final MockControlState state;
    private final DeterministicBucketer bucketer = new DeterministicBucketer();

    public MockFeatureFlagProvider(MockControlState state) {
        this.state = state;
    }

    @Override
    public ProviderDecision evaluate(FlagKey flag, SyntheticContext context) {
        if (state.providerUnavailable()) {
            throw new ProviderUnavailableException(
                    "Mock provider failure was requested by presenter");
        }
        if (flag == FlagKey.PAYMENT_API_MIGRATION) {
            return decision(
                    TextNode.valueOf(state.migrationStage()), "presenter migration control");
        }
        if (flag == FlagKey.PAYMENT_V2_ENABLED) {
            return decision(
                    BooleanNode.valueOf(!state.killSwitch()),
                    state.killSwitch()
                            ? "presenter kill switch is active"
                            : "presenter kill switch is inactive");
        }
        if (flag == FlagKey.MAINTENANCE_BANNER) {
            return new ProviderDecision(
                    flag.safeFallback(), "mock maintenance default", DecisionSource.MOCK, false);
        }

        AudienceMatch audience = matchAudience(flag, context);
        return switch (flag) {
            case CLIENT_NEW_PAYMENT_UI, PROFILE_RESPONSE_V2 ->
                    decision(BooleanNode.valueOf(audience.enabled()), audience.reason());
            case FRAUD_ENGINE_VERSION ->
                    decision(TextNode.valueOf(audience.enabled() ? "v2" : "v1"), audience.reason());
            case NOTIFICATION_PROVIDER ->
                    decision(
                            TextNode.valueOf(audience.enabled() ? "provider-b" : "provider-a"),
                            audience.reason());
            default -> throw new IllegalStateException("Unhandled mock flag " + flag.key());
        };
    }

    private AudienceMatch matchAudience(FlagKey flag, SyntheticContext context) {
        if (!state.individualTarget().isBlank() && state.individualTarget().equals(context.key())) {
            return new AudienceMatch(true, "individual target matched context key");
        }
        if (state.employeeSegment() && context.employee()) {
            return new AudienceMatch(true, "bank-employees segment matched employee=true");
        }
        if (state.pilotSegment() && "pilot".equals(context.cohort())) {
            return new AudienceMatch(true, "pilot-customers segment matched cohort=pilot");
        }
        int bucket = bucketer.bucket(flag.key(), context.key());
        if (bucket < state.rolloutPercentage()) {
            return new AudienceMatch(
                    true,
                    "percentage rollout matched bucket %d of %d%%"
                            .formatted(bucket, state.rolloutPercentage()));
        }
        return new AudienceMatch(
                false,
                "safe mock default; bucket %d outside %d%% rollout"
                        .formatted(bucket, state.rolloutPercentage()));
    }

    private ProviderDecision decision(
            com.fasterxml.jackson.databind.JsonNode value, String reason) {
        return new ProviderDecision(value, reason, DecisionSource.MOCK, false);
    }

    @Override
    public String mode() {
        return "mock";
    }

    @Override
    public String status() {
        return state.providerUnavailable() ? "simulated-unavailable" : "ready";
    }

    @Override
    public boolean degraded() {
        return state.providerUnavailable();
    }

    private record AudienceMatch(boolean enabled, String reason) {}
}
