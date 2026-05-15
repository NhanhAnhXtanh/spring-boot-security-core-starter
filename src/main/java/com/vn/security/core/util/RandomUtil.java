package com.vn.security.core.util;

import java.security.SecureRandom;
import java.util.Base64;

public final class RandomUtil {

    private static final int DEF_COUNT = 20;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    static {
        SECURE_RANDOM.nextBytes(new byte[64]);
    }

    private RandomUtil() {}

    private static String generateRandomAlphanumericString() {
        byte[] bytes = new byte[DEF_COUNT];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes).substring(0, DEF_COUNT);
    }

    public static String generateActivationKey() {
        return generateRandomAlphanumericString();
    }

    public static String generateResetKey() {
        return generateRandomAlphanumericString();
    }

    public static String generatePassword() {
        return generateRandomAlphanumericString();
    }
}
