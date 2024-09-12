package io.github.w1th4d.jarplant.implants.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.*;

public class DnsBeaconImplantTests {
    @Test
    public void testDecode_allFields_allValues() {
        // Arrange
        String fqdn = "746573742d686f73742d3031.7365727669636575736572.546573744f532076312e322e332d756c747261.4a564d20496e666f20312e322e332d6f6d67.3939083327.1756721185.test.local";

        // Act
        Optional<Map<String, String>> output = DnsBeaconDecoder.decode(fqdn);

        // Assert
        assertTrue("Succeeded.", output.isPresent());
        assertEquals("Got hostname right.", output.get().get("HOSTNAME"), "test-host-01");
        assertEquals("Got username right.", output.get().get("USERNAME"), "serviceuser");
        assertEquals("Got OS info right.", output.get().get("OS_INFO"), "TestOS v1.2.3-ultra");
        assertEquals("Got JVM info right.", output.get().get("JVM_INFO"), "JVM Info 1.2.3-omg");
    }

    @Test
    public void testDecode_missingOneField_onlyAvailableValues() {
        // Arrange
        String fqdn = "746573742d686f73742d3031.7365727669636575736572.546573744f532076312e322e332d756c747261.3742447023.1756721185.test.local";

        // Act
        Optional<Map<String, String>> output = DnsBeaconDecoder.decode(fqdn);

        // Assert
        assertTrue("Succeeded.", output.isPresent());
        assertEquals("Got hostname right.", output.get().get("HOSTNAME"), "test-host-01");
        assertEquals("Got username right.", output.get().get("USERNAME"), "serviceuser");
        assertEquals("Got OS info right.", output.get().get("OS_INFO"), "TestOS v1.2.3-ultra");
        assertNull("Did not get any JVM info.", output.get().get("JVM_INFO"));
    }

    @Test
    public void testDecode_corruptField_onlyValidValues() {
        // Arrange
        String fqdn = "746573742d686f73742d3031.7365727669zZzZzZzZ636575736572.546573744f532076312e322e332d756c747261.4a564d20496e666f20312e322e332d6f6d67.3683540284.1756721185.test.local";

        // Act
        Optional<Map<String, String>> output = DnsBeaconDecoder.decode(fqdn);

        // Assert
        assertTrue("Succeeded.", output.isPresent());
        assertEquals("Got hostname right.", "test-host-01", output.get().get("HOSTNAME"));
        assertNull("Did not get any username.", output.get().get("USERNAME"));
        assertEquals("Got OS info right.", "TestOS v1.2.3-ultra", output.get().get("OS_INFO"));
        assertEquals("Got JVM info right.", "JVM Info 1.2.3-omg", output.get().get("JVM_INFO"));
    }

    @Test
    public void testDecode_missingAllFields_fine() {
        // Arrange
        String fqdn = "0.test.local";

        // Act
        Optional<Map<String, String>> output = DnsBeaconDecoder.decode(fqdn);

        // Assert
        assertTrue("Fine.", output.isPresent());
    }

    @Test
    public void testDecode_missingOrInvalidChecksum_invalid() {
        // Arrange
        String fqdn = "746573742d686f73742d3031.7365727669636575736572.546573744f532076312e322e332d756c747261.4a564d20496e666f20312e322e332d6f6d67.1756721185.test.local";

        // Act
        Optional<Map<String, String>> output = DnsBeaconDecoder.decode(fqdn);

        // Assert
        assertTrue("Failed.", output.isEmpty());
    }

    @Test
    public void testDecode_tooManyFields_excessValuesIgnored() {
        // Arrange
        String fqdn = "746573742d686f73742d3031.7365727669636575736572.546573744f532076312e322e332d756c747261.4a564d20496e666f20312e322e332d6f6d67.546573744f532076312e322e332d756c747261.883519573.1756721185.test.local";

        // Act
        Optional<Map<String, String>> output = DnsBeaconDecoder.decode(fqdn);

        // Assert
        assertTrue("Succeeded.", output.isPresent());
        assertEquals("Got hostname right.", "test-host-01", output.get().get("HOSTNAME"));
        assertEquals("Got username right.", "serviceuser", output.get().get("USERNAME"));
        assertEquals("Got OS info right.", "TestOS v1.2.3-ultra", output.get().get("OS_INFO"));
        assertEquals("Got JVM info right.", "JVM Info 1.2.3-omg", output.get().get("JVM_INFO"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testToJson_normalData_success() throws JsonProcessingException {
        // Arrange
        Map<String, String> input = new HashMap<>();
        input.put("UNIQUE_ID", "12345");
        input.put("HOSTNAME", "some-host");
        input.put("USERNAME", "user");

        // Act
        String json = DnsBeaconDecoder.toJson(input);

        // Assert
        ObjectMapper jsonParser = new ObjectMapper();
        Map<String, String> accordingToJackson = jsonParser.readValue(json, HashMap.class);
        assertEquals("Properly formatted JSON.", input, accordingToJackson);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testToJson_dataWithJsonChars_success() throws JsonProcessingException {
        // Arrange
        Map<String, String> input = new HashMap<>();
        input.put("OS_INFO", "TestOS \"quoted\", comma: colon; } curly");

        // Act
        String json = DnsBeaconDecoder.toJson(input);

        // Assert
        ObjectMapper jsonParser = new ObjectMapper();
        Map<String, String> accordingToJackson = jsonParser.readValue(json, HashMap.class);
        assertEquals("Properly formatted JSON.", input, accordingToJackson);
    }

    @Test
    public void testDecodeHex_something_correct() {
        // Act
        Optional<String> decoded = DnsBeaconDecoder.decodeHex("54657374202d5f2b");

        // Assert
        assertTrue("Success.", decoded.isPresent());
        assertEquals("Got right output.", "Test -_+", decoded.get());
    }

    @Test
    public void testDecodeHex_mixedCase_success() {
        // Act
        Optional<String> decoded = DnsBeaconDecoder.decodeHex("5f5F");

        // Assert
        assertTrue("Success.", decoded.isPresent());
        assertEquals("Got right output.", "__", decoded.get());
    }
}
