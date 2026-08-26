package com.example.banking.dtm;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public final class DtmFaultGate {
    private final MockControlState state;

    public DtmFaultGate(MockControlState state) {
        this.state = state;
    }

    public void beforeEvaluation() {
        if (state.dtmUnavailable()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "Simulated DTM outage");
        }
        if (state.delayMs() > 0) {
            try {
                Thread.sleep(state.delayMs());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE, "DTM delay interrupted", exception);
            }
        }
    }
}
