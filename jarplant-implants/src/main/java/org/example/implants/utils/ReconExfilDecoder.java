package org.example.implants.utils;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class ReconExfilDecoder {
    private final static String[] FIELD_NAMES = {"HOSTNAME", "USERNAME", "OS_INFO", "JVM_INFO"};

    public static Optional<Map<String, String>> decode(String fqdn, String topDomain) {
        Map<String, String> fields = new HashMap<>();

        if (!fqdn.endsWith(topDomain)) {
            return Optional.empty();
        }

        String[] subdomains = fqdn
                .substring(0, fqdn.lastIndexOf(topDomain))
                .split("\\.");
        if (subdomains.length <= 1) {
            return Optional.empty();
        }

        try {
            int uniqueId = Integer.parseInt(subdomains[subdomains.length - 1]);
            fields.put("UNIQUE_ID", "" + uniqueId);
        } catch (NumberFormatException e) {
            return Optional.empty();
        }

        for (int i = 0; i < subdomains.length - 1 && i < FIELD_NAMES.length; i++) {
            String fieldName = FIELD_NAMES[i];
            Optional<String> decoded = decodeHex(subdomains[i]);
            decoded.ifPresent(fieldValue -> fields.put(fieldName, fieldValue));
        }

        return Optional.of(fields);
    }

    // Creds: ChatGPT
    private static Optional<String> decodeHex(String hex) {
        if (hex.length() % 2 != 0) {
            return Optional.empty();
        }

        byte[] bytes = new byte[hex.length() / 2];
        for (int i = 0; i < hex.length(); i += 2) {
            int firstDigit = Character.digit(hex.charAt(i), 16);
            int secondDigit = Character.digit(hex.charAt(i + 1), 16);
            if (firstDigit == -1 || secondDigit == -1) {
                return Optional.empty();
            }
            bytes[i / 2] = (byte) ((firstDigit << 4) + secondDigit);
        }

        return Optional.of(new String(bytes, StandardCharsets.UTF_8));
    }
}
