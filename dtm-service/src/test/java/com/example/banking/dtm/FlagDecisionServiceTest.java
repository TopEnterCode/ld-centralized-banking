package com.example.banking.dtm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.banking.contracts.DecisionSource;
import com.example.banking.contracts.FlagEvaluationRequest;
import com.example.banking.contracts.FlagValueType;
import com.example.banking.contracts.SyntheticPersonas;
import org.junit.jupiter.api.Test;

class FlagDecisionServiceTest {
    @Test
    void rejectsUnknownFlagAndWrongType() {
        FlagDecisionService service =
                new FlagDecisionService(new MockFeatureFlagProvider(new MockControlState()));

        assertThatThrownBy(
                        () ->
                                service.evaluate(
                                        new FlagEvaluationRequest(
                                                "unknown",
                                                FlagValueType.BOOLEAN,
                                                SyntheticPersonas.NARIN_GENERAL,
                                                "c1")))
                .isInstanceOf(UnknownFlagException.class);
        assertThatThrownBy(
                        () ->
                                service.evaluate(
                                        new FlagEvaluationRequest(
                                                "fraud-engine-version",
                                                FlagValueType.BOOLEAN,
                                                SyntheticPersonas.NARIN_GENERAL,
                                                "c2")))
                .isInstanceOf(IncorrectFlagTypeException.class);
    }

    @Test
    void providerFailureReturnsHonestSdkDefault() {
        MockControlState state = new MockControlState();
        state.providerUnavailable(true);
        FlagDecisionService service = new FlagDecisionService(new MockFeatureFlagProvider(state));

        var response =
                service.evaluate(
                        new FlagEvaluationRequest(
                                "client-new-payment-ui",
                                FlagValueType.BOOLEAN,
                                SyntheticPersonas.NARIN_GENERAL,
                                "c3"));

        assertThat(response.source()).isEqualTo(DecisionSource.SDK_DEFAULT);
        assertThat(response.value().asBoolean()).isFalse();
        assertThat(response.usedFallback()).isTrue();
    }
}
