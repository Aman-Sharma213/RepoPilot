package com.repopilot.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class Hashing {

    private Hashing() {
    }

    public static String sha256(Path file) throws IOException {
        MessageDigest digest = newDigest();
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buf = new byte[16 * 1024];
            int read;
            while ((read = in.read(buf)) > 0) {
                digest.update(buf, 0, read);
            }
        }
        return hex(digest.digest());
    }

    public static String sha256(String text) {
        return hex(newDigest().digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    private static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
