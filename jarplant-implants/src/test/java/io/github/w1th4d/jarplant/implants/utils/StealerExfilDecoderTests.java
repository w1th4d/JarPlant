package io.github.w1th4d.jarplant.implants.utils;

import org.junit.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;

public class StealerExfilDecoderTests {
    @Test
    public void testParseResponse_RequestDigOutput_Fqdn() throws IOException {
        // Arrange
        String copyPasteFromInteractsh = ";; opcode: QUERY, status: NOERROR, id: 62053\n" +
                ";; flags:; QUERY: 1, ANSWER: 0, AUTHORITY: 0, ADDITIONAL: 1\n" +
                "\n" +
                ";; OPT PSEUDOSECTION:\n" +
                "; EDNS: version 0; flags: do; udp: 1232\n" +
                "\n" +
                ";; QUESTION SECTION:\n" +
                ";some-data-field.1379621077.abc123.oast.fun.\tIN\t AAAA\n";

        // Act
        StealerExfilDecoder decoder = StealerExfilDecoder.create("abc123.oast.fun");
        Set<String> fqdns = decoder.findFqdns(copyPasteFromInteractsh);

        // Assert
        assertFalse(fqdns.isEmpty());
        assertTrue(fqdns.contains("some-data-field.1379621077.abc123.oast.fun"));
    }

    @Test
    public void testDecodeRequests_OneRequest_DecodedData() throws Exception {
        // Arrange
        String baseDomain = "something.example.com";
        // Only 'host' and 'user':
        String request = "h80ylft7wlwk3gn5d1uggdgucea6j40v54zkg4vzwg9sj1b.860116749-0.something.example.com";

        // Act
        StealerExfilDecoder decoder = StealerExfilDecoder.create(baseDomain);
        Map<String, Map<String, String>> decodedData = decoder.decodeRequests(List.of(request));

        // Assert
        assertEquals(1, decodedData.size());
        assertTrue("Got ID right", decodedData.containsKey("860116749"));
        Map<String, String> fields = decodedData.get("860116749");
        assertEquals("Got hostname right", "test-host-01", fields.get("host"));
        assertEquals("Got username right", "service-user", fields.get("user"));
    }
}
