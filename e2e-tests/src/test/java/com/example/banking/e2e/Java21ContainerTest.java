package com.example.banking.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.testcontainers.containers.GenericContainer;

@EnabledIfSystemProperty(named = "testcontainers", matches = "true")
class Java21ContainerTest {
    @Test
    void productionRuntimeImageProvidesJava21() {
        try (GenericContainer<?> container =
                new GenericContainer<>("eclipse-temurin:21-jre-alpine")
                        .withCommand("sh", "-c", "java -version && sleep 2")) {
            container.start();
            assertThat(container.getLogs()).contains("version \"21");
        }
    }
}
