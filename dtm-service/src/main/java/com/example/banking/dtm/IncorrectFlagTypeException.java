package com.example.banking.dtm;

import com.example.banking.contracts.FlagKey;

public final class IncorrectFlagTypeException extends RuntimeException {
    public IncorrectFlagTypeException(FlagKey flag, String requestedType) {
        super("Flag %s requires type %s, not %s".formatted(flag.key(), flag.type(), requestedType));
    }
}
