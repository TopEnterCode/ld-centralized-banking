package com.example.banking.payment;

import com.example.banking.contracts.PaymentContracts;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {
    private final PaymentProcessingService service;
    private final PaymentFailureState failureState;

    public PaymentController(PaymentProcessingService service, PaymentFailureState failureState) {
        this.service = service;
        this.failureState = failureState;
    }

    @PostMapping
    PaymentContracts.Response pay(@Valid @RequestBody PaymentContracts.Request request) {
        return service.pay(request);
    }

    @PostMapping("/demo/v2-failure")
    Map<String, Boolean> v2Failure(@RequestBody Map<String, Boolean> request) {
        failureState.v2Failure(Boolean.TRUE.equals(request.get("enabled")));
        return Map.of("v2Failure", failureState.v2Failure());
    }
}
