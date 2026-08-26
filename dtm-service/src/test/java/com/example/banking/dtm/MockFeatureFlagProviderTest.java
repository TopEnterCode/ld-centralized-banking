package com.example.banking.dtm;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.banking.contracts.FlagKey;
import com.example.banking.contracts.SyntheticPersonas;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MockFeatureFlagProviderTest {
    private MockControlState state;
    private MockFeatureFlagProvider provider;

    @BeforeEach
    void setUp() {
        state = new MockControlState();
        provider = new MockFeatureFlagProvider(state);
    }

    @Test
    void targetsOneExactSyntheticUser() {
        state.individualTarget("mali-pilot");

        assertThat(
                        provider.evaluate(
                                        FlagKey.CLIENT_NEW_PAYMENT_UI, SyntheticPersonas.MALI_PILOT)
                                .value()
                                .asBoolean())
                .isTrue();
        assertThat(
                        provider.evaluate(
                                        FlagKey.CLIENT_NEW_PAYMENT_UI,
                                        SyntheticPersonas.NARIN_GENERAL)
                                .value()
                                .asBoolean())
                .isFalse();
    }

    @Test
    void targetsEmployeeAndPilotSegmentsIndependently() {
        state.employeeSegment(true);
        assertThat(
                        provider.evaluate(
                                        FlagKey.PROFILE_RESPONSE_V2,
                                        SyntheticPersonas.SOMCHAI_EMPLOYEE)
                                .value()
                                .asBoolean())
                .isTrue();
        assertThat(
                        provider.evaluate(FlagKey.PROFILE_RESPONSE_V2, SyntheticPersonas.MALI_PILOT)
                                .value()
                                .asBoolean())
                .isFalse();

        state.employeeSegment(false);
        state.pilotSegment(true);
        assertThat(
                        provider.evaluate(
                                        FlagKey.FRAUD_ENGINE_VERSION, SyntheticPersonas.MALI_PILOT)
                                .value()
                                .asText())
                .isEqualTo("v2");
    }

    @Test
    void assignmentsAreStableAtTenAndFiftyPercent() {
        DeterministicBucketer bucketer = new DeterministicBucketer();
        int first = bucketer.bucket("client-new-payment-ui", "demo-user-037");
        int second = bucketer.bucket("client-new-payment-ui", "demo-user-037");

        assertThat(first).isEqualTo(second).isBetween(0, 99);
        state.rolloutPercentage(10);
        long tenPercentCount = enabledCount();
        state.rolloutPercentage(50);
        long fiftyPercentCount = enabledCount();
        assertThat(tenPercentCount).isBetween(4L, 16L);
        assertThat(fiftyPercentCount).isBetween(35L, 65L).isGreaterThan(tenPercentCount);
    }

    @Test
    void killSwitchDisablesPaymentV2() {
        state.killSwitch(true);
        assertThat(
                        provider.evaluate(
                                        FlagKey.PAYMENT_V2_ENABLED, SyntheticPersonas.NARIN_GENERAL)
                                .value()
                                .asBoolean())
                .isFalse();
    }

    private long enabledCount() {
        return SyntheticPersonas.rolloutUsers().stream()
                .filter(
                        context ->
                                provider.evaluate(FlagKey.CLIENT_NEW_PAYMENT_UI, context)
                                        .value()
                                        .asBoolean())
                .count();
    }
}
