package org.example.implants.utils;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.zip.CRC32;
import java.util.zip.Checksum;

public class DnsBeaconDecoder {
    // These field names are just implicitly assumed based on the order of the subdomains/fields
    private final static String[] FIELD_NAMES = {"HOSTNAME", "USERNAME", "OS_INFO", "JVM_INFO"};

    public static Optional<Map<String, String>> decode(String fqdn) {
        Map<String, String> fields = new HashMap<>();

        String[] subdomains = fqdn.split("\\.");
        if (subdomains.length <= 1) {
            return Optional.empty();
        }

        Checksum checksumSoFar = new CRC32();

        boolean gotValidChecksum = false;
        for (int i = 0; i < subdomains.length; i++) {
            String currentlyProcessingSubdomain = subdomains[i];
            if (looksLikeChecksumField(currentlyProcessingSubdomain, checksumSoFar.getValue())) {
                gotValidChecksum = true;
                break;
            }

            String fieldName;
            if (i < FIELD_NAMES.length) {
                fieldName = FIELD_NAMES[i];
            } else {
                fieldName = "FIELD_" + i;
            }

            Optional<String> decoded = decodeHex(currentlyProcessingSubdomain);
            if (decoded.isPresent()) {
                String decodedFieldValue = decoded.get();
                fields.put(fieldName, decodedFieldValue);
                checksumSoFar.update(decodedFieldValue.getBytes(StandardCharsets.UTF_8));
            }
        }

        if (!gotValidChecksum) {
            return Optional.empty();
        }

        return Optional.of(fields);
    }

    private static boolean looksLikeChecksumField(String field, long matchAgainst) {
        long longValue;
        try {
            longValue = Long.parseLong(field);
        } catch (NumberFormatException e) {
            return false;
        }

        return longValue == matchAgainst;
    }

    // Creds: ChatGPT
    static Optional<String> decodeHex(String hex) {
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

    // Keep this module simple and (compile) dependency-free for now
    public static String toJson(Map<String, String> kv) {
        StringBuilder json = new StringBuilder("{");
        boolean isFirstEntry = true;
        for (Map.Entry<String, String> entry : kv.entrySet()) {
            if (!isFirstEntry) {
                json.append(", ");
            } else {
                isFirstEntry = false;
            }

            json.append("\"");
            json.append(entry.getKey().replace("\"", "\\\""));
            json.append("\": \"");
            json.append(entry.getValue().replace("\"", "\\\""));
            json.append("\"");
        }
        json.append("}");

        return json.toString();
    }
}
