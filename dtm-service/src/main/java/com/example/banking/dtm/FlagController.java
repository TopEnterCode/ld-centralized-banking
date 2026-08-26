package com.example.banking.dtm;

import com.example.banking.contracts.BatchFlagEvaluationRequest;
import com.example.banking.contracts.BatchFlagEvaluationResponse;
import com.example.banking.contracts.FlagEvaluationRequest;
import com.example.banking.contracts.FlagEvaluationResponse;
import com.example.banking.contracts.FlagKey;
import com.example.banking.contracts.FlagStatusResponse;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.Arrays;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/flags")
public class FlagController {
    private final FlagDecisionService service;
    private final FeatureFlagProvider provider;
    private final DtmFaultGate faultGate;

    public FlagController(
            FlagDecisionService service, FeatureFlagProvider provider, DtmFaultGate faultGate) {
        this.service = service;
        this.provider = provider;
        this.faultGate = faultGate;
    }

    @GetMapping("/status")
    FlagStatusResponse status() {
        return new FlagStatusResponse(
                provider.mode(),
                provider.status(),
                provider.degraded(),
                Arrays.stream(FlagKey.values()).map(FlagKey::key).toList(),
                Instant.now());
    }

    @PostMapping("/evaluate")
    FlagEvaluationResponse evaluate(@Valid @RequestBody FlagEvaluationRequest request) {
        faultGate.beforeEvaluation();
        return service.evaluate(request);
    }

    @PostMapping("/evaluate/batch")
    BatchFlagEvaluationResponse evaluateBatch(
            @Valid @RequestBody BatchFlagEvaluationRequest request) {
        faultGate.beforeEvaluation();
        return service.evaluate(request);
    }
}
