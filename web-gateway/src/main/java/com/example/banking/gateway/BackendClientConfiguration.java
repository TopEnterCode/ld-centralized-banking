package com.example.banking.gateway;

import java.util.Map;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ServiceEndpointsProperties.class)
public class BackendClientConfiguration {
    @Bean
    Map<String, RestClient> backendClients(ServiceEndpointsProperties properties) {
        return Map.of(
                "dtm", client(properties.dtm(), properties.timeoutMs()),
                "payment", client(properties.payment(), properties.timeoutMs()),
                "profile", client(properties.profile(), properties.timeoutMs()),
                "fraud", client(properties.fraud(), properties.timeoutMs()),
                "notification", client(properties.notification(), properties.timeoutMs()));
    }

    private RestClient client(String baseUrl, int timeoutMs) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeoutMs);
        requestFactory.setReadTimeout(timeoutMs);
        return RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
    }
}
