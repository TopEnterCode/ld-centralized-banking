package com.example.banking.gateway;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.banking.contracts.JourneyContracts;
import com.example.banking.contracts.SyntheticPersonas;
import com.github.tomakehurst.wiremock.WireMockServer;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

class JourneyOrchestratorWireMockTest {
    private WireMockServer profile;
    private WireMockServer fraud;
    private WireMockServer payment;
    private WireMockServer notification;

    @BeforeEach
    void startServers() {
        profile = server();
        fraud = server();
        payment = server();
        notification = server();
    }

    @AfterEach
    void stopServers() {
        profile.stop();
        fraud.stop();
        payment.stop();
        notification.stop();
    }

    @Test
    void composesFourTypedServiceContractsIntoOneJourney() {
        stub(
                profile,
                "/api/v1/profiles/view",
                """
                {"responseVersion":"v2","displayName":"Synthetic","tier":"preferred","preferences":{},"decision":%s}
                """
                        .formatted(
                                decision(
                                        "customer-profile-service",
                                        "profile-response-v2",
                                        "true")));
        stub(
                fraud,
                "/api/v1/fraud/assess",
                """
                {"engineVersion":"v2","riskLevel":"low","score":8,"decision":%s}
                """
                        .formatted(decision("fraud-service", "fraud-engine-version", "\"v2\"")));
        stub(
                payment,
                "/api/v1/payments",
                """
                {"success":true,"paymentReference":"SYN-V2-TEST","calledVersions":["v1","v2"],"authoritativeVersion":"v2","comparisonResult":"matched","migrationStage":"live","evaluationReason":"test","usedFallback":false,"decisions":[%s,%s]}
                """
                        .formatted(
                                decision("payment-service", "payment-api-migration", "\"live\""),
                                decision("payment-service", "payment-v2-enabled", "true")));
        stub(
                notification,
                "/api/v1/notifications",
                """
                {"status":"synthetic-notification-sent","provider":"provider-b","decision":%s}
                """
                        .formatted(
                                decision(
                                        "notification-service",
                                        "notification-provider",
                                        "\"provider-b\"")));

        JourneyOrchestrator orchestrator =
                new JourneyOrchestrator(
                        Map.of(
                                "profile", client(profile),
                                "fraud", client(fraud),
                                "payment", client(payment),
                                "notification", client(notification)));

        var response =
                orchestrator.run(
                        new JourneyContracts.Request(
                                SyntheticPersonas.SOMCHAI_EMPLOYEE,
                                "Synthetic Merchant",
                                new BigDecimal("1250.00")));

        assertThat(response.success()).isTrue();
        assertThat(response.degraded()).isFalse();
        assertThat(response.timeline()).hasSize(6);
        assertThat(response.payment().authoritativeVersion()).isEqualTo("v2");
        profile.verify(1, postRequestedFor(urlEqualTo("/api/v1/profiles/view")));
        fraud.verify(1, postRequestedFor(urlEqualTo("/api/v1/fraud/assess")));
        payment.verify(1, postRequestedFor(urlEqualTo("/api/v1/payments")));
        notification.verify(1, postRequestedFor(urlEqualTo("/api/v1/notifications")));
    }

    private WireMockServer server() {
        WireMockServer server = new WireMockServer(wireMockConfig().dynamicPort());
        server.start();
        return server;
    }

    private void stub(WireMockServer server, String path, String body) {
        server.stubFor(
                post(urlEqualTo(path))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "application/json")
                                        .withBody(body)));
    }

    private RestClient client(WireMockServer server) {
        return RestClient.builder()
                .baseUrl(server.baseUrl())
                .requestFactory(new SimpleClientHttpRequestFactory())
                .build();
    }

    private String decision(String service, String flag, String value) {
        return """
               {"service":"%s","flagKey":"%s","value":%s,"reason":"wiremock contract","source":"mock","usedFallback":false,"timestamp":"2026-08-23T12:00:00Z","correlationId":"wiremock"}
               """
                .formatted(service, flag, value)
                .trim();
    }
}
