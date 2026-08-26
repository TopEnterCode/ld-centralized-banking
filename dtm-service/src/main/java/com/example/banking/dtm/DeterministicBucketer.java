package com.example.banking.dtm;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class DeterministicBucketer {
    public int bucket(String flagKey, String contextKey) {
        try {
            byte[] digest =
                    MessageDigest.getInstance("SHA-256")
                            .digest((flagKey + ":" + contextKey).getBytes(StandardCharsets.UTF_8));
            long prefix = ByteBuffer.wrap(digest, 0, Long.BYTES).getLong();
            return (int) Long.remainderUnsigned(prefix, 100L);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 must be available in every Java runtime", exception);
        }
    }
}
