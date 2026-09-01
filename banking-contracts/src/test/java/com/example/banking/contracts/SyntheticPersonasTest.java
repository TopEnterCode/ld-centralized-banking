package com.example.banking.contracts;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

class SyntheticPersonasTest {
    @Test
    void createsExactlyOneHundredStableSyntheticUsers() {
        assertThat(SyntheticPersonas.rolloutUsers())
                .hasSize(100)
                .extracting(SyntheticContext::key)
                .startsWith("demo-user-001")
                .endsWith("demo-user-100")
                .doesNotHaveDuplicates();
    }

    @Test
    void onlyRequiredFlagIsBrowserVisible() {
        assertThat(java.util.Arrays.stream(FlagKey.values()).filter(FlagKey::clientSide))
                .extracting(FlagKey::key)
                .containsExactly("client-new-payment-ui", "client-new-home-experience");
    }

    @Test
    void rejectsInvalidSyntheticContext() {
        SyntheticContext invalid =
                new SyntheticContext(
                        "REAL USER!",
                        false,
                        "general",
                        "standard",
                        "region",
                        "mobile-web-simulator",
                        "desktop",
                        "not-a-version",
                        "device");

        try (var validatorFactory = Validation.buildDefaultValidatorFactory()) {
            assertThat(validatorFactory.getValidator().validate(invalid))
                    .extracting(violation -> violation.getPropertyPath().toString())
                    .contains("key", "platform", "appVersion");
        }
    }
}
