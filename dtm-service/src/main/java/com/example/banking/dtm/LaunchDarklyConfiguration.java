package com.example.banking.dtm;

import com.launchdarkly.sdk.server.LDClient;
import com.launchdarkly.sdk.server.LDConfig;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile("launchdarkly")
public class LaunchDarklyConfiguration {
    @Bean(destroyMethod = "close")
    LDClient launchDarklyClient(
            @Value("${launchdarkly.sdk-key:}") String sdkKey,
            @Value("${launchdarkly.initialization-timeout-ms:3000}") long timeoutMs) {
        boolean missing = sdkKey == null || sdkKey.isBlank();
        LDConfig config =
                new LDConfig.Builder()
                        .startWait(Duration.ofMillis(Math.max(100, Math.min(timeoutMs, 10000))))
                        .offline(missing)
                        .build();
        return new LDClient(missing ? "missing-sdk-key" : sdkKey, config);
    }
}
