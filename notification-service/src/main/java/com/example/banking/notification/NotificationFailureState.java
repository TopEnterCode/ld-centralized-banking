package com.example.banking.notification;

import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Component;

@Component
public final class NotificationFailureState {
    private final AtomicBoolean providerBFailure = new AtomicBoolean(false);

    public boolean providerBFailure() {
        return providerBFailure.get();
    }

    public void providerBFailure(boolean value) {
        providerBFailure.set(value);
    }
}
