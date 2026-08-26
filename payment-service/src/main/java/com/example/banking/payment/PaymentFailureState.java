package com.example.banking.payment;

import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Component;

@Component
public final class PaymentFailureState {
    private final AtomicBoolean v2Failure = new AtomicBoolean(false);

    public boolean v2Failure() {
        return v2Failure.get();
    }

    public void v2Failure(boolean value) {
        v2Failure.set(value);
    }
}
