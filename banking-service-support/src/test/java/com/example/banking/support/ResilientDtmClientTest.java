package com.example.banking.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.banking.contracts.DecisionSource;
import com.example.banking.contracts.FlagKey;
import com.example.banking.contracts.SyntheticPersonas;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class ResilientDtmClientTest {
    @Test
    void returnsTypedServiceFallbackWhenDtmCannotBeReached() {
        RestClient unreachable = RestClient.builder().baseUrl("http://127.0.0.1:1").build();
        ResilientDtmClient client =
                new ResilientDtmClient(unreachable, CircuitBreaker.ofDefaults("test"));

        var response =
                client.evaluate(
                        FlagKey.PAYMENT_API_MIGRATION,
                        SyntheticPersonas.NARIN_GENERAL,
                        "test-correlation");

        assertThat(response.source()).isEqualTo(DecisionSource.SERVICE_FALLBACK);
        assertThat(response.value().asText()).isEqualTo("off");
        assertThat(response.usedFallback()).isTrue();
    }
}
