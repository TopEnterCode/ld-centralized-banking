package com.example.banking.dtm;

import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/demo")
public class DemoControlController {
    private static final Set<String> MIGRATION_STAGES = Set.of("off", "shadow", "live", "complete");
    private final MockControlState state;
    private final FeatureFlagProvider provider;

    public DemoControlController(MockControlState state, FeatureFlagProvider provider) {
        this.state = state;
        this.provider = provider;
    }

    @GetMapping("/state")
    Map<String, Object> state() {
        return Map.of("mode", provider.mode(), "controls", state.snapshot());
    }

    @PostMapping("/control")
    Map<String, Object> control(@RequestBody DemoControlRequest request) {
        if (!"mock".equals(provider.mode())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Mock controls are disabled in LaunchDarkly mode; use guarded admin controls");
        }
        switch (request.action()) {
            case "reset", "restore-all" -> state.reset();
            case "target-individual" -> state.individualTarget(required(request.value()));
            case "target-employee" -> state.employeeSegment(true);
            case "target-pilot" -> state.pilotSegment(true);
            case "rollout" -> state.rolloutPercentage(parsePercentage(request.value()));
            case "migration" -> state.migrationStage(parseMigration(request.value()));
            case "kill-switch" -> state.killSwitch(Boolean.parseBoolean(request.value()));
            case "provider-failure" -> state.providerUnavailable(true);
            case "restore-provider" -> state.providerUnavailable(false);
            case "dtm-failure" -> state.dtmUnavailable(true);
            case "restore-dtm" -> state.dtmUnavailable(false);
            case "dtm-timeout" -> state.delayMs(parseDelay(request.value()));
            default ->
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST, "Unknown control action");
        }
        return Map.of("accepted", true, "controls", state.snapshot());
    }

    private String required(String value) {
        if (value == null || !value.matches("[a-z0-9-]{1,80}")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Invalid synthetic context key");
        }
        return value;
    }

    private int parsePercentage(String value) {
        try {
            int percentage = Integer.parseInt(value);
            if (!Set.of(0, 10, 50, 100).contains(percentage)) {
                throw new NumberFormatException();
            }
            return percentage;
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Rollout must be 0, 10, 50, or 100");
        }
    }

    private String parseMigration(String value) {
        if (!MIGRATION_STAGES.contains(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid migration stage");
        }
        return value;
    }

    private int parseDelay(String value) {
        try {
            return Math.max(0, Math.min(5000, Integer.parseInt(value)));
        } catch (NumberFormatException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Delay must be milliseconds");
        }
    }

    public record DemoControlRequest(String action, String value) {}
}
