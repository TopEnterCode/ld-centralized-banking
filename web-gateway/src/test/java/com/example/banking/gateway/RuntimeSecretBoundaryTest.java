package com.example.banking.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.banking.support.FlagDecisionClient;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class RuntimeSecretBoundaryTest {
    @Test
    void runtimeExposesOnlyClientSideCredential() {
        DemoGatewayController controller =
                new DemoGatewayController(
                        Mockito.mock(JourneyOrchestrator.class),
                        Mockito.mock(FlagDecisionClient.class),
                        "launchdarkly",
                        "client-side-123",
                        false,
                        "poc");

        String response = controller.runtime().toString();
        assertThat(response).contains("client-side-123", "client-new-payment-ui");
        assertThat(response).doesNotContain("sdkKey", "apiAccessToken", "LD_SDK_KEY");
    }
}
