package com.example.banking.profile;

import com.example.banking.contracts.DecisionMetadata;
import com.example.banking.contracts.FlagKey;
import com.example.banking.contracts.ProfileContracts;
import com.example.banking.support.FlagDecisionClient;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/profiles")
public class ProfileController {
    private final FlagDecisionClient decisionClient;

    public ProfileController(FlagDecisionClient decisionClient) {
        this.decisionClient = decisionClient;
    }

    @PostMapping("/view")
    ProfileContracts.Response view(@Valid @RequestBody ProfileContracts.Request request) {
        var decision =
                decisionClient.evaluate(
                        FlagKey.PROFILE_RESPONSE_V2, request.context(), request.correlationId());
        boolean v2 = decision.value().asBoolean(false);
        String displayName =
                switch (request.context().key()) {
                    case "somchai-employee" -> "Somchai (Synthetic Employee)";
                    case "mali-pilot" -> "Mali (Synthetic Pilot)";
                    default -> "Narin (Synthetic Customer)";
                };
        Map<String, String> preferences =
                v2
                        ? Map.of("language", "th", "theme", "digital-blue", "insights", "enabled")
                        : Map.of("language", "th", "theme", "classic");
        return new ProfileContracts.Response(
                v2 ? "v2" : "legacy",
                displayName,
                request.context().tier(),
                preferences,
                DecisionMetadata.from("customer-profile-service", decision));
    }
}
