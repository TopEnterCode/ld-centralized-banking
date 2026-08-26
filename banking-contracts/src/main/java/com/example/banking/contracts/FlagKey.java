package com.example.banking.contracts;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.util.Arrays;
import java.util.Optional;

public enum FlagKey {
    CLIENT_NEW_PAYMENT_UI("client-new-payment-ui", FlagValueType.BOOLEAN, BooleanNode.FALSE, true),
    PROFILE_RESPONSE_V2("profile-response-v2", FlagValueType.BOOLEAN, BooleanNode.FALSE, false),
    PAYMENT_API_MIGRATION(
            "payment-api-migration", FlagValueType.STRING, TextNode.valueOf("off"), false),
    PAYMENT_V2_ENABLED("payment-v2-enabled", FlagValueType.BOOLEAN, BooleanNode.FALSE, false),
    FRAUD_ENGINE_VERSION(
            "fraud-engine-version", FlagValueType.STRING, TextNode.valueOf("v1"), false),
    NOTIFICATION_PROVIDER(
            "notification-provider", FlagValueType.STRING, TextNode.valueOf("provider-a"), false),
    MAINTENANCE_BANNER(
            "maintenance-banner",
            FlagValueType.JSON,
            TextNode.valueOf("{\"enabled\":false}"),
            false);

    private final String key;
    private final FlagValueType type;
    private final JsonNode safeFallback;
    private final boolean clientSide;

    FlagKey(String key, FlagValueType type, JsonNode safeFallback, boolean clientSide) {
        this.key = key;
        this.type = type;
        this.safeFallback = safeFallback;
        this.clientSide = clientSide;
    }

    public String key() {
        return key;
    }

    public FlagValueType type() {
        return type;
    }

    public JsonNode safeFallback() {
        return safeFallback.deepCopy();
    }

    public boolean clientSide() {
        return clientSide;
    }

    public static Optional<FlagKey> fromKey(String key) {
        return Arrays.stream(values()).filter(candidate -> candidate.key.equals(key)).findFirst();
    }
}
