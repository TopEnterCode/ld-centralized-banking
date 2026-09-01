package com.example.banking.dtm;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

@Component
public final class MockControlState {
    private final AtomicReference<String> individualTarget = new AtomicReference<>("");
    private final AtomicBoolean employeeSegment = new AtomicBoolean(false);
    private final AtomicBoolean pilotSegment = new AtomicBoolean(false);
    private final AtomicInteger rolloutPercentage = new AtomicInteger(0);
    private final AtomicReference<String> migrationStage = new AtomicReference<>("off");
    private final AtomicBoolean killSwitch = new AtomicBoolean(false);
    private final AtomicBoolean maintenanceEnabled = new AtomicBoolean(false);
    private final AtomicBoolean providerUnavailable = new AtomicBoolean(false);
    private final AtomicBoolean dtmUnavailable = new AtomicBoolean(false);
    private final AtomicInteger delayMs = new AtomicInteger(0);

    public void reset() {
        individualTarget.set("");
        employeeSegment.set(false);
        pilotSegment.set(false);
        rolloutPercentage.set(0);
        migrationStage.set("off");
        killSwitch.set(false);
        maintenanceEnabled.set(false);
        providerUnavailable.set(false);
        dtmUnavailable.set(false);
        delayMs.set(0);
    }

    public String individualTarget() {
        return individualTarget.get();
    }

    public void individualTarget(String value) {
        individualTarget.set(value == null ? "" : value);
    }

    public boolean employeeSegment() {
        return employeeSegment.get();
    }

    public void employeeSegment(boolean value) {
        employeeSegment.set(value);
    }

    public boolean pilotSegment() {
        return pilotSegment.get();
    }

    public void pilotSegment(boolean value) {
        pilotSegment.set(value);
    }

    public int rolloutPercentage() {
        return rolloutPercentage.get();
    }

    public void rolloutPercentage(int value) {
        rolloutPercentage.set(Math.max(0, Math.min(100, value)));
    }

    public String migrationStage() {
        return migrationStage.get();
    }

    public void migrationStage(String value) {
        migrationStage.set(value);
    }

    public boolean killSwitch() {
        return killSwitch.get();
    }

    public void killSwitch(boolean value) {
        killSwitch.set(value);
    }

    public boolean maintenanceEnabled() {
        return maintenanceEnabled.get();
    }

    public void maintenanceEnabled(boolean value) {
        maintenanceEnabled.set(value);
    }

    public boolean providerUnavailable() {
        return providerUnavailable.get();
    }

    public void providerUnavailable(boolean value) {
        providerUnavailable.set(value);
    }

    public boolean dtmUnavailable() {
        return dtmUnavailable.get();
    }

    public void dtmUnavailable(boolean value) {
        dtmUnavailable.set(value);
    }

    public int delayMs() {
        return delayMs.get();
    }

    public void delayMs(int value) {
        delayMs.set(Math.max(0, Math.min(5000, value)));
    }

    public Map<String, Object> snapshot() {
        return Map.of(
                "individualTarget", individualTarget(),
                "employeeSegment", employeeSegment(),
                "pilotSegment", pilotSegment(),
                "rolloutPercentage", rolloutPercentage(),
                "migrationStage", migrationStage(),
                "killSwitch", killSwitch(),
                "maintenanceEnabled", maintenanceEnabled(),
                "providerUnavailable", providerUnavailable(),
                "dtmUnavailable", dtmUnavailable(),
                "delayMs", delayMs());
    }
}
