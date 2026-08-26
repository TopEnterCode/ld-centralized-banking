package com.example.banking.support;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DtmClientProperties.class)
public class DtmClientConfiguration {
    @Bean
    FlagDecisionClient flagDecisionClient(DtmClientProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.timeoutMs());
        requestFactory.setReadTimeout(properties.timeoutMs());
        RestClient restClient =
                RestClient.builder()
                        .baseUrl(properties.baseUrl())
                        .requestFactory(requestFactory)
                        .build();
        CircuitBreakerConfig circuitBreakerConfig =
                CircuitBreakerConfig.custom()
                        .failureRateThreshold(50)
                        .minimumNumberOfCalls(4)
                        .slidingWindowSize(8)
                        .waitDurationInOpenState(Duration.ofSeconds(3))
                        .permittedNumberOfCallsInHalfOpenState(2)
                        .build();
        return new ResilientDtmClient(restClient, CircuitBreaker.of("dtm", circuitBreakerConfig));
    }
}
