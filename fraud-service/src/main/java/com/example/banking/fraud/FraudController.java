package com.example.banking.fraud;

import com.example.banking.contracts.DecisionMetadata;
import com.example.banking.contracts.FlagKey;
import com.example.banking.contracts.FraudContracts;
import com.example.banking.support.FlagDecisionClient;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/fraud")
public class FraudController {
    private final FlagDecisionClient decisionClient;

    public FraudController(FlagDecisionClient decisionClient) {
        this.decisionClient = decisionClient;
    }

    @PostMapping("/assess")
    FraudContracts.Response assess(@Valid @RequestBody FraudContracts.Request request) {
        var decision =
                decisionClient.evaluate(
                        FlagKey.FRAUD_ENGINE_VERSION, request.context(), request.correlationId());
        String engine = "v2".equals(decision.value().asText()) ? "v2" : "v1";
        int base = request.amount().intValue() > 20000 ? 42 : 12;
        int score = "v2".equals(engine) ? Math.max(1, base - 4) : base;
        return new FraudContracts.Response(
                engine,
                score >= 40 ? "review" : "low",
                score,
                DecisionMetadata.from("fraud-service", decision));
    }
}
