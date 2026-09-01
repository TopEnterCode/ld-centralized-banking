package com.example.banking.contracts;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class MaintenanceContractsTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void parsesSharedMaintenanceConfiguration() throws Exception {
        var configuration =
                MaintenanceContracts.Configuration.from(
                        objectMapper.readTree(
                                """
                                {"enabled":true,"mode":"read-only","title":"Planned upgrade","message":"Transfers pause briefly.","eta":"Back at 02:00 UTC"}
                                """));

        assertThat(configuration.enabled()).isTrue();
        assertThat(configuration.title()).isEqualTo("Planned upgrade");
        assertThat(configuration.message()).isEqualTo("Transfers pause briefly.");
        assertThat(configuration.eta()).isEqualTo("Back at 02:00 UTC");
    }

    @Test
    void invalidConfigurationFallsBackToSafeAvailability() {
        var configuration = MaintenanceContracts.Configuration.from(null);

        assertThat(configuration.enabled()).isFalse();
        assertThat(configuration.mode()).isEqualTo("read-only");
    }
}
