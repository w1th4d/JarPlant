package org.example.implants.utils;

import org.junit.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.*;

public class ReconExfilDecoderTests {
    @Test
    public void testDecode_allFields_allValues() {
        // Arrange
        String fqdn = "746573742d686f73742d3031.7365727669636575736572.546573744f532076312e322e332d756c747261.4a564d20496e666f20312e322e332d6f6d67.1827203862.test.local";

        // Act
        Optional<Map<String, String>> output = ReconExfilDecoder.decode(fqdn, "test.local");

        // Assert
        assertTrue("Succeeded.", output.isPresent());
        assertEquals("Got unique ID right.", output.get().get("UNIQUE_ID"), "1827203862");
        assertEquals("Got hostname right.", output.get().get("HOSTNAME"), "test-host-01");
        assertEquals("Got username right.", output.get().get("USERNAME"), "serviceuser");
        assertEquals("Got OS info right.", output.get().get("OS_INFO"), "TestOS v1.2.3-ultra");
        assertEquals("Got JVM info right.", output.get().get("JVM_INFO"), "JVM Info 1.2.3-omg");
    }

    @Test
    public void testDecode_missingOneField_onlyAvailableValues() {
        // Arrange
        String fqdn = "746573742d686f73742d3031.7365727669636575736572.546573744f532076312e322e332d756c747261.1827203862.test.local";

        // Act
        Optional<Map<String, String>> output = ReconExfilDecoder.decode(fqdn, "test.local");

        // Assert
        assertTrue("Succeeded.", output.isPresent());
        assertEquals("Got unique ID right.", output.get().get("UNIQUE_ID"), "1827203862");
        assertEquals("Got hostname right.", output.get().get("HOSTNAME"), "test-host-01");
        assertEquals("Got username right.", output.get().get("USERNAME"), "serviceuser");
        assertEquals("Got OS info right.", output.get().get("OS_INFO"), "TestOS v1.2.3-ultra");
        assertNull("Did not get any JVM info.", output.get().get("JVM_INFO"));
    }

    @Test
    public void testDecode_missingAllFields_invalid() {
        // Arrange
        String fqdn = "1292736490.test.local";

        // Act
        Optional<Map<String, String>> output = ReconExfilDecoder.decode(fqdn, "test.local");

        // Assert
        assertTrue("Failed.", output.isEmpty());
    }

    @Test
    public void testDecode_missingId_invalid() {
        // Arrange
        String fqdn = "746573742d686f73742d3031.7365727669636575736572.546573744f532076312e322e332d756c747261.4a564d20496e666f20312e322e332d6f6d67.test.local";

        // Act
        Optional<Map<String, String>> output = ReconExfilDecoder.decode(fqdn, "test.local");

        // Assert
        assertTrue("Failed.", output.isEmpty());
    }

    @Test
    public void testDecode_tooManyFields_excessValuesIgnored() {
        // Arrange
        String fqdn = "746573742d686f73742d3031.7365727669636575736572.546573744f532076312e322e332d756c747261.4a564d20496e666f20312e322e332d6f6d67.6578636573732076616c7565.1827203862.test.local";

        // Act
        Optional<Map<String, String>> output = ReconExfilDecoder.decode(fqdn, "test.local");

        // Assert
        assertTrue("Succeeded.", output.isPresent());
        assertEquals("Got unique ID right.", output.get().get("UNIQUE_ID"), "1827203862");
        assertEquals("Got hostname right.", output.get().get("HOSTNAME"), "test-host-01");
        assertEquals("Got username right.", output.get().get("USERNAME"), "serviceuser");
        assertEquals("Got OS info right.", output.get().get("OS_INFO"), "TestOS v1.2.3-ultra");
        assertEquals("Got JVM info right.", output.get().get("JVM_INFO"), "JVM Info 1.2.3-omg");
    }

    @Test
    public void testDecode_wrongTopDomain_invalid() {
        // Arrange
        String fqdn = "746573742d686f73742d3031.7365727669636575736572.546573744f532076312e322e332d756c747261.4a564d20496e666f20312e322e332d6f6d67.1827203862.something-else.local";

        // Act
        Optional<Map<String, String>> output = ReconExfilDecoder.decode(fqdn, "test.local");

        // Assert
        assertTrue("Failed.", output.isEmpty());
    }
}
