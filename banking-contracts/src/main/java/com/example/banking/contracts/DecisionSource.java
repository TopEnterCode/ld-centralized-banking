package com.example.banking.contracts;

import com.fasterxml.jackson.annotation.JsonValue;

public enum DecisionSource {
    LAUNCHDARKLY("launchdarkly"),
    MOCK("mock"),
    SDK_DEFAULT("sdk-default"),
    SERVICE_FALLBACK("service-fallback");

    private final String value;

    DecisionSource(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }
}
