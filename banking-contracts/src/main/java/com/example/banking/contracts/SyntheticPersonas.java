package com.example.banking.contracts;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

public final class SyntheticPersonas {
    public static final SyntheticContext SOMCHAI_EMPLOYEE =
            new SyntheticContext(
                    "somchai-employee",
                    true,
                    "employee",
                    "preferred",
                    "bangkok",
                    "mobile-web-simulator",
                    "android",
                    "2.5.0",
                    "demo-device-somchai");

    public static final SyntheticContext MALI_PILOT =
            new SyntheticContext(
                    "mali-pilot",
                    false,
                    "pilot",
                    "standard",
                    "bangkok",
                    "mobile-web-simulator",
                    "ios",
                    "2.4.0",
                    "demo-device-mali");

    public static final SyntheticContext NARIN_GENERAL =
            new SyntheticContext(
                    "narin-general",
                    false,
                    "general",
                    "standard",
                    "chiang-mai",
                    "mobile-web-simulator",
                    "android",
                    "2.3.0",
                    "demo-device-narin");

    private SyntheticPersonas() {}

    public static List<SyntheticContext> named() {
        return List.of(SOMCHAI_EMPLOYEE, MALI_PILOT, NARIN_GENERAL);
    }

    public static List<SyntheticContext> rolloutUsers() {
        return IntStream.rangeClosed(1, 100)
                .mapToObj(
                        index -> {
                            String key = "demo-user-%03d".formatted(index);
                            return new SyntheticContext(
                                    key,
                                    false,
                                    "general",
                                    "standard",
                                    "synthetic-region",
                                    "mobile-web-simulator",
                                    index % 2 == 0 ? "ios" : "android",
                                    "2.5.0",
                                    "device-" + key);
                        })
                .toList();
    }

    public static Optional<SyntheticContext> find(String key) {
        return named().stream().filter(context -> context.key().equals(key)).findFirst();
    }
}
