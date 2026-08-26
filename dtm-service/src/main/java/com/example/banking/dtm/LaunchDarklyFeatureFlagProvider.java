package com.example.banking.dtm;

import com.example.banking.contracts.DecisionSource;
import com.example.banking.contracts.FlagKey;
import com.example.banking.contracts.SyntheticContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.launchdarkly.sdk.ContextKind;
import com.launchdarkly.sdk.EvaluationDetail;
import com.launchdarkly.sdk.LDContext;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.server.LDClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("launchdarkly")
public final class LaunchDarklyFeatureFlagProvider implements FeatureFlagProvider {
    private final LDClient client;
    private final ObjectMapper objectMapper;
    private final boolean credentialConfigured;

    public LaunchDarklyFeatureFlagProvider(
            LDClient client,
            ObjectMapper objectMapper,
            @Value("${launchdarkly.sdk-key:}") String sdkKey) {
        this.client = client;
        this.objectMapper = objectMapper;
        this.credentialConfigured = sdkKey != null && !sdkKey.isBlank();
    }

    @Override
    public ProviderDecision evaluate(FlagKey flag, SyntheticContext context) {
        LDContext ldContext = toLaunchDarklyContext(context);
        return switch (flag.type()) {
            case BOOLEAN -> booleanDecision(flag, ldContext);
            case STRING -> stringDecision(flag, ldContext);
            case JSON -> jsonDecision(flag, ldContext);
        };
    }

    private ProviderDecision booleanDecision(FlagKey flag, LDContext context) {
        EvaluationDetail<Boolean> detail =
                client.boolVariationDetail(flag.key(), context, flag.safeFallback().booleanValue());
        return fromDetail(BooleanNode.valueOf(detail.getValue()), detail);
    }

    private ProviderDecision stringDecision(FlagKey flag, LDContext context) {
        EvaluationDetail<String> detail =
                client.stringVariationDetail(flag.key(), context, flag.safeFallback().asText());
        return fromDetail(TextNode.valueOf(detail.getValue()), detail);
    }

    private ProviderDecision jsonDecision(FlagKey flag, LDContext context) {
        LDValue defaultValue = LDValue.parse(flag.safeFallback().asText());
        EvaluationDetail<LDValue> detail =
                client.jsonValueVariationDetail(flag.key(), context, defaultValue);
        try {
            return fromDetail(objectMapper.readTree(detail.getValue().toJsonString()), detail);
        } catch (JsonProcessingException exception) {
            throw new ProviderUnavailableException("LaunchDarkly returned invalid JSON");
        }
    }

    private ProviderDecision fromDetail(JsonNode value, EvaluationDetail<?> detail) {
        boolean fallback = detail.isDefaultValue() || !client.isInitialized();
        return new ProviderDecision(
                value,
                detail.getReason().toString(),
                fallback ? DecisionSource.SDK_DEFAULT : DecisionSource.LAUNCHDARKLY,
                fallback);
    }

    private LDContext toLaunchDarklyContext(SyntheticContext context) {
        LDContext user =
                LDContext.builder(ContextKind.of("user"), context.key())
                        .set("employee", context.employee())
                        .set("cohort", context.cohort())
                        .set("tier", context.tier())
                        .set("region", context.region())
                        .set("channel", context.channel())
                        .build();
        LDContext device =
                LDContext.builder(ContextKind.of("device"), context.deviceKey())
                        .set("platform", context.platform())
                        .set("appVersion", context.appVersion())
                        .build();
        return LDContext.multiBuilder().add(user).add(device).build();
    }

    @Override
    public String mode() {
        return "launchdarkly";
    }

    @Override
    public String status() {
        if (!credentialConfigured) {
            return "missing-credentials";
        }
        return client.isInitialized() ? "connected" : "initializing-or-unavailable";
    }

    @Override
    public boolean degraded() {
        return !credentialConfigured || !client.isInitialized();
    }
}
