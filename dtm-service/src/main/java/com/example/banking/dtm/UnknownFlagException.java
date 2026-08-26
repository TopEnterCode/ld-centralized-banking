package com.example.banking.dtm;

public final class UnknownFlagException extends RuntimeException {
    private final String flagKey;

    public UnknownFlagException(String flagKey) {
        super("Unknown flag: " + flagKey);
        this.flagKey = flagKey;
    }

    public String flagKey() {
        return flagKey;
    }
}
