package io.github.w1th4d.jarplant.implants;

import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static org.junit.Assert.*;

public class DnsBeaconImplantTests {
    private Map<String, String> envVars;
    private Properties javaProps;
    private static final Map<String, String> emptyEnvVars = new HashMap<>();
    private static final Properties emptyJavaProps = new Properties();

    @Before
    public void setImplantConfig() {
        DnsBeaconImplant.CONF_DOMAIN = "abc123.test.local";
        DnsBeaconImplant.CONF_SUBDOMAIN_MAX_LEN = 63;
        DnsBeaconImplant.CONF_FQDN_MAX_LEN = 253;
    }

    @Before
    public void getEnvVars() {
        envVars = new HashMap<>();
        envVars.put("USERNAME", "user_from_env");
    }

    @Before
    public void getJavaProps() {
        javaProps = new Properties();
        javaProps.put("user.name", "user_from_props");
        javaProps.put("os.name", "TestOS");
        javaProps.put("os.version", "1.2.3-alpha4");
        javaProps.put("java.vm.version", "3.2.1-custom42");
    }

    @Test
    public void testGetUsername_validProps_gotUsernameFromProps() {
        // Act
        String username = DnsBeaconImplant.getUsername(envVars, javaProps);

        // Assert
        assertEquals("Got username from props.", "user_from_props", username);
    }

    @Test
    public void testGetUsername_noProps_gotUsernameFromEnv() {
        // Act
        String username = DnsBeaconImplant.getUsername(envVars, emptyJavaProps);

        // Assert
        assertEquals("Got username from env.", "user_from_env", username);
    }

    @Test
    public void testGetUsername_nothing_unknown() {
        // Act
        String username = DnsBeaconImplant.getUsername(emptyEnvVars, emptyJavaProps);

        // Assert
        assertEquals("Got unknown username.", "unknown", username);
    }

    @Test
    public void testGetOsInfo_validProps_success() {
        // Act
        String osInfo = DnsBeaconImplant.getOsInfo(javaProps);

        // Assert
        assertEquals("Got OS info.", "TestOS 1.2.3-alpha4", osInfo);
    }

    @Test
    public void testGetOsInfo_noProp_unknown() {
        // Act
        String osInfo = DnsBeaconImplant.getOsInfo(emptyJavaProps);

        // Assert
        assertEquals("Got unknown OS info.", "unknown unknown", osInfo);
    }

    @Test
    public void testGetRuntimeInfo_validProps_success() {
        // Act
        String runtimeInfo = DnsBeaconImplant.getRuntimeInfo(javaProps);

        // Assert
        assertEquals("Got runtime info.", "3.2.1-custom42", runtimeInfo);
    }

    @Test
    public void testGetRuntimeInfo_noProp_unknown() {
        // Act
        String runtimeInfo = DnsBeaconImplant.getRuntimeInfo(emptyJavaProps);

        // Assert
        assertEquals("Got unknown runtime info.", "unknown", runtimeInfo);
    }

    @Test
    public void testGenerateEncodedDomainName_any_usesExfilDomain() {
        // Act
        DnsBeaconImplant subject = new DnsBeaconImplant();
        String result = subject.generateEncodedDomainName("whatever", "whatever");

        // Assert
        assertTrue("Uses exfil domain.", result.endsWith("abc123.test.local"));
    }

    @Test
    public void testGenerateCacheBusterValue_consecutiveCalling_differentValues() {
        // Act
        String id1 = DnsBeaconImplant.generateCacheBusterValue();
        String id2 = DnsBeaconImplant.generateCacheBusterValue();
        String id3 = DnsBeaconImplant.generateCacheBusterValue();

        // Assert
        assertNotEquals("Different IDs.", id1, id2);
        assertNotEquals("Different IDs.", id2, id3);
        assertNotEquals("Different IDs.", id3, id1);
    }

    @Test
    public void testGenerateEncodedDomainName_validProps_encodedDomainName() {
        // Act
        DnsBeaconImplant subject = new DnsBeaconImplant();
        String exfilDomain = subject.generateEncodedDomainName("test-host-01", "serviceuser", "TestOS 1.2.3-alpha4", "3.2.1-custom42");

        // Assert
        String[] components = exfilDomain.split("\\.");
        assertEquals("Amount of subdomains.", 9, components.length);
        assertNotNull("Got some hostname.", decodeHex(components[0]));
        assertEquals("Got username right.", decodeHex(components[1]), "serviceuser");
        assertEquals("Got OS info right.", decodeHex(components[2]), "TestOS 1.2.3-alpha4");
        assertEquals("Got runtime info right.", decodeHex(components[3]), "3.2.1-custom42");
    }

    @Test
    public void testGenerateEncodedDomainName_tooLong_skippedValue() {
        // Arrange
        final int uniqueIdLen = ("" + Integer.MAX_VALUE).length();
        DnsBeaconImplant.CONF_FQDN_MAX_LEN = 30 + uniqueIdLen + "abd123.test.local".length();

        // Act
        DnsBeaconImplant subject = new DnsBeaconImplant();
        String exfilDomain = subject.generateEncodedDomainName("test-host-01", "some-quite-long-value-that-will-be-skipped");

        // Assert
        String[] parts = exfilDomain.split("\\.");
        assertTrue("Some values were skipped.", parts.length < 8);
    }

    @Test
    public void testGenerateEncodedDomainName_veryShortDomainMaxLen_noValues() {
        // Arrange
        DnsBeaconImplant.CONF_FQDN_MAX_LEN = 0;

        // Act
        DnsBeaconImplant subject = new DnsBeaconImplant();
        String exfilDomain = subject.generateEncodedDomainName("test-host-01");

        // Assert
        String[] parts = exfilDomain.split("\\.");
        assertEquals("All values were skipped.", 5, parts.length);
    }

    @Test
    public void testEncode_tooLongEvenMaxLen_truncated() {
        // Arrange
        String input = "abcdefghIJKL";
        DnsBeaconImplant.CONF_SUBDOMAIN_MAX_LEN = 8;

        // Act
        String encoded = DnsBeaconImplant.encode(input);

        // Assert
        assertEquals("Output is truncated.", encoded.length(), 8);
        assertEquals("Output is correct.", decodeHex(encoded), "abcd");
    }

    @Test
    public void testEncode_tooLongOddMaxLen_truncated() {
        // Arrange
        String input = "abcdEFGH";
        DnsBeaconImplant.CONF_SUBDOMAIN_MAX_LEN = 9;

        // Act
        String encoded = DnsBeaconImplant.encode(input);

        // Assert
        assertEquals("Output is truncated.", encoded.length(), 8);
        assertEquals("Output is correct.", decodeHex(encoded), "abcd");
    }

    @Test
    public void testEncode_exactlyMaxLen_fine() {
        // Arrange
        String input = "abcd";
        DnsBeaconImplant.CONF_SUBDOMAIN_MAX_LEN = 8;

        // Act
        String encoded = DnsBeaconImplant.encode(input);

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
