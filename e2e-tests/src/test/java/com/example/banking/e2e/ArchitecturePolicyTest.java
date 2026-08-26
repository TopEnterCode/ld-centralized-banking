package com.example.banking.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class ArchitecturePolicyTest {
    @Test
    void onlyDtmModuleDependsOnLaunchDarklyServerSdk() throws IOException {
        Path root = locateRoot();
        try (var paths = Files.walk(root)) {
            List<Path> violations =
                    paths.filter(path -> path.getFileName().toString().equals("pom.xml"))
                            .filter(path -> !path.equals(root.resolve("pom.xml")))
                            .filter(path -> !path.equals(root.resolve("dtm-service/pom.xml")))
                            .filter(
                                    path -> {
                                        try {
                                            return Files.readString(path)
                                                    .contains("launchdarkly-java-server-sdk");
                                        } catch (IOException exception) {
                                            throw new IllegalStateException(exception);
                                        }
                                    })
                            .toList();
            assertThat(violations).isEmpty();
        }
    }

    @Test
    void browserSourceAndBuiltAssetsContainNoServerSecrets() throws IOException {
        Path frontend = locateRoot().resolve("web-gateway/frontend");
        try (var paths = Files.walk(frontend)) {
            List<Path> violations =
                    paths.filter(Files::isRegularFile)
                            .filter(path -> !path.toString().contains("node_modules"))
                            .filter(
                                    path -> {
                                        try {
                                            String text = Files.readString(path);
                                            return text.contains("LD_SDK_KEY")
                                                    || text.contains("LD_API_ACCESS_TOKEN");
                                        } catch (IOException exception) {
                                            return false;
                                        }
                                    })
                            .toList();
            assertThat(violations).isEmpty();
        }
    }

    private Path locateRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null && !Files.exists(current.resolve("banking-contracts"))) {
            current = current.getParent();
        }
        if (current == null) throw new IllegalStateException("Project root not found");
        return current;
    }
}
