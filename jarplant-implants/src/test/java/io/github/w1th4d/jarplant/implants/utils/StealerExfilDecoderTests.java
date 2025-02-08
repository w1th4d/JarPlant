package io.github.w1th4d.jarplant.implants.utils;

import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

import static org.junit.Assert.*;

public class StealerExfilDecoderTests {
    @Test
    public void testCreate_ValidTopDomain_Instance() throws DecoderException {
        StealerExfilDecoder.create("abc123.oast.fun");
    }

    @Test(expected = DecoderException.class)
    public void testCreate_Regex1_Exception() throws DecoderException {
        StealerExfilDecoder.create("[ab].oast.fun");
    }

    @Test(expected = DecoderException.class)
    public void testCreate_Regex2_Exception() throws DecoderException {
        StealerExfilDecoder.create("\\.");
    }

    @Test(expected = DecoderException.class)
    public void testCreate_Regex3_Exception() throws DecoderException {
        StealerExfilDecoder.create("\\b\\d+E");
    }

    @Test(expected = DecoderException.class)
    public void testCreate_Glob_Exception() throws DecoderException {
        StealerExfilDecoder.create("*.oast.fun");
    }

    @Test
    public void testExtractFqdns_InteractshRequestOutput_Fqdn() throws DecoderException {
        // Arrange
        String copyPasteFromInteractsh = ";; opcode: QUERY, status: NOERROR, id: 62053\n" +
                ";; flags:; QUERY: 1, ANSWER: 0, AUTHORITY: 0, ADDITIONAL: 1\n" +
                "\n" +
                ";; OPT PSEUDOSECTION:\n" +
                "; EDNS: version 0; flags: do; udp: 1232\n" +
                "\n" +
                ";; QUESTION SECTION:\n" +
                ";sOmE-DaTa-fIEld.1379621077.aBC123.OAst.fUn.\tIN\t AAAA\n";

        // Act
        StealerExfilDecoder decoder = StealerExfilDecoder.create("abc123.oast.fun");
        Set<String> fqdns = decoder.extractFqdns(copyPasteFromInteractsh);

        // Assert
        assertFalse(fqdns.isEmpty());
        assertTrue(fqdns.contains("some-data-field.1379621077.abc123.oast.fun"));
    }

    @Test
    public void testExtractFqdns_SeveralInteractshOutputs_Fqdn() throws DecoderException {
        // Arrange
        String copyPasteFromInteractsh = ";; opcode: QUERY, status: NOERROR, id: 62053\n" +
                ";; flags:; QUERY: 1, ANSWER: 0, AUTHORITY: 0, ADDITIONAL: 1\n" +
                "\n" +
                ";; OPT PSEUDOSECTION:\n" +
                "; EDNS: version 0; flags: do; udp: 1232\n" +
                "\n" +
                ";; QUESTION SECTION:\n" +
                ";sOmE-DaTa-fIEld.1379621077.aBC123.OAst.fUn.\tIN\t AAAA\n" +
                // Another one:
                ";; opcode: QUERY, status: NOERROR, id: 62053\n" +
                ";; flags:; QUERY: 1, ANSWER: 0, AUTHORITY: 0, ADDITIONAL: 1\n" +
                "\n" +
                ";; OPT PSEUDOSECTION:\n" +
                "; EDNS: version 0; flags: do; udp: 1232\n" +
                "\n" +
                ";; QUESTION SECTION:\n" +
                ";ANoThEr-DAta-fiELD.7701269731.ABc123.oaST.FuN.\tIN\t AAAA\n";

        // Act
        StealerExfilDecoder decoder = StealerExfilDecoder.create("abc123.oast.fun");
        Set<String> fqdns = decoder.extractFqdns(copyPasteFromInteractsh);

        // Assert
        assertFalse(fqdns.isEmpty());
        assertTrue(fqdns.contains("some-data-field.1379621077.abc123.oast.fun"));
        assertTrue(fqdns.contains("another-data-field.7701269731.abc123.oast.fun"));
    }

    @Test
    public void testExtractFqdns_InteractshResponseOutput_Fqdn() throws DecoderException {
        // Arrange
        String copyPasteFromInteractsh = ";; opcode: QUERY, status: NOERROR, id: 62053\n" +
                ";; flags: qr aa; QUERY: 1, ANSWER: 1, AUTHORITY: 2, ADDITIONAL: 2\n" +
                "\n" +
                ";; QUESTION SECTION:\n" +
                ";sOmE-DaTa-fIEld.1379621077.aBC123.OAst.fUn.\tIN\t AAAA\n" +
                "\n" +
                ";; ANSWER SECTION:\n" +
                "sOmE-DaTa-fIEld.1379621077.aBC123.OAst.fUn.\t3600\tIN\tA\t10.1.2.3\n" +
                "\n" +
                ";; AUTHORITY SECTION:\n" +
                "sOmE-DaTa-fIEld.1379621077.aBC123.OAst.fUn.\t3600\tIN\tNS\tns1.oast.fun.\n" +
                "sOmE-DaTa-fIEld.1379621077.aBC123.OAst.fUn.\t3600\tIN\tNS\tns2.oast.fun.\n" +
                "\n" +
                ";; ADDITIONAL SECTION:\n" +
                "ns1.oast.fun.\t3600\tIN\tA\t10.1.2.3\n" +
                "ns2.oast.fun.\t3600\tIN\tA\t10.1.2.3\n";

        // Act
        StealerExfilDecoder decoder = StealerExfilDecoder.create("abc123.oast.fun");
        Set<String> fqdns = decoder.extractFqdns(copyPasteFromInteractsh);

        // Assert
        assertFalse(fqdns.isEmpty());
        assertTrue(fqdns.contains("some-data-field.1379621077.abc123.oast.fun"));
    }

    @Test
    public void testExtractFqdns_SeveralInteractshResponseOutputs_Fqdn() throws DecoderException {
        // Arrange
        String copyPasteFromInteractsh = ";; opcode: QUERY, status: NOERROR, id: 62053\n" +
                ";; flags: qr aa; QUERY: 1, ANSWER: 1, AUTHORITY: 2, ADDITIONAL: 2\n" +
                "\n" +
                ";; QUESTION SECTION:\n" +
                ";sOmE-DaTa-fIEld.1379621077.aBC123.OAst.fUn.\tIN\t AAAA\n" +
                "\n" +
                ";; ANSWER SECTION:\n" +
                "sOmE-DaTa-fIEld.1379621077.aBC123.OAst.fUn.\t3600\tIN\tA\t10.1.2.3\n" +
                "\n" +
                ";; AUTHORITY SECTION:\n" +
                "sOmE-DaTa-fIEld.1379621077.aBC123.OAst.fUn.\t3600\tIN\tNS\tns1.oast.fun.\n" +
                "sOmE-DaTa-fIEld.1379621077.aBC123.OAst.fUn.\t3600\tIN\tNS\tns2.oast.fun.\n" +
                "\n" +
                ";; ADDITIONAL SECTION:\n" +
                "ns1.oast.fun.\t3600\tIN\tA\t10.1.2.3\n" +
                "ns2.oast.fun.\t3600\tIN\tA\t10.1.2.3\n" +
                // Another one
                ";; opcode: QUERY, status: NOERROR, id: 62053\n" +
                ";; flags: qr aa; QUERY: 1, ANSWER: 1, AUTHORITY: 2, ADDITIONAL: 2\n" +
                "\n" +
                ";; QUESTION SECTION:\n" +
                ";ANoThEr-DAta-fiELD.7701269731.ABc123.oaST.FuN.\tIN\t AAAA\n" +
                "\n" +
                ";; ANSWER SECTION:\n" +
                "ANoThEr-DAta-fiELD.7701269731.ABc123.oaST.FuN.\t3600\tIN\tA\t10.1.2.3\n" +
                "\n" +
                ";; AUTHORITY SECTION:\n" +
                "ANoThEr-DAta-fiELD.7701269731.ABc123.oaST.FuN.\t3600\tIN\tNS\tns1.oast.fun.\n" +
                "ANoThEr-DAta-fiELD.7701269731.ABc123.oaST.FuN.\t3600\tIN\tNS\tns2.oast.fun.\n" +
                "\n" +
                ";; ADDITIONAL SECTION:\n" +
                "ns1.oast.fun.\t3600\tIN\tA\t10.1.2.3\n" +
                "ns2.oast.fun.\t3600\tIN\tA\t10.1.2.3\n";

        // Act
        StealerExfilDecoder decoder = StealerExfilDecoder.create("abc123.oast.fun");
        Set<String> fqdns = decoder.extractFqdns(copyPasteFromInteractsh);

        // Assert
        assertFalse(fqdns.isEmpty());
        assertTrue(fqdns.contains("some-data-field.1379621077.abc123.oast.fun"));
        assertTrue(fqdns.contains("another-data-field.7701269731.abc123.oast.fun"));
    }

    // Some DNS servers mash up the case of each letter and issues the query in different cases
    // It's still the same FQDN, just different casing. DNS ignores case.
    @Test
    public void testExtractFqdns_SeveralInteractshResponseOutputsSameFqdnDifferentCase_Fqdn() throws DecoderException {
        // Arrange
        String copyPasteFromInteractsh = ";; opcode: QUERY, status: NOERROR, id: 62053\n" +
                ";; flags: qr aa; QUERY: 1, ANSWER: 1, AUTHORITY: 2, ADDITIONAL: 2\n" +
                "\n" +
                ";; QUESTION SECTION:\n" +
                ";sOmE-DaTa-fIEld.1379621077.aBC123.OAst.fUn.\tIN\t AAAA\n" +
                "\n" +
                ";; ANSWER SECTION:\n" +
                "sOmE-DaTa-fIEld.1379621077.aBC123.OAst.fUn.\t3600\tIN\tA\t10.1.2.3\n" +
                "\n" +
                ";; AUTHORITY SECTION:\n" +
                "sOmE-DaTa-fIEld.1379621077.aBC123.OAst.fUn.\t3600\tIN\tNS\tns1.oast.fun.\n" +
                "sOmE-DaTa-fIEld.1379621077.aBC123.OAst.fUn.\t3600\tIN\tNS\tns2.oast.fun.\n" +
                "\n" +
                ";; ADDITIONAL SECTION:\n" +
                "ns1.oast.fun.\t3600\tIN\tA\t10.1.2.3\n" +
                "ns2.oast.fun.\t3600\tIN\tA\t10.1.2.3\n" +
                // Same query but on a different case
                ";; opcode: QUERY, status: NOERROR, id: 62053\n" +
                ";; flags: qr aa; QUERY: 1, ANSWER: 1, AUTHORITY: 2, ADDITIONAL: 2\n" +
                "\n" +
                ";; QUESTION SECTION:\n" +
                ";SOme-dAtA-FIelD.1379621077.ABc123.aAST.FuN.\tIN\t AAAA\n" +
                "\n" +
                ";; ANSWER SECTION:\n" +
                "SOme-dAtA-FIelD.1379621077.ABc123.aAST.FuN.\t3600\tIN\tA\t10.1.2.3\n" +
                "\n" +
                ";; AUTHORITY SECTION:\n" +
                "SOme-dAtA-FIelD.1379621077.ABc123.aAST.FuN.\t3600\tIN\tNS\tns1.oast.fun.\n" +
                "SOme-dAtA-FIelD.1379621077.ABc123.aAST.FuN.\t3600\tIN\tNS\tns2.oast.fun.\n" +
                "\n" +
                ";; ADDITIONAL SECTION:\n" +
                "ns1.oast.fun.\t3600\tIN\tA\t10.1.2.3\n" +
                "ns2.oast.fun.\t3600\tIN\tA\t10.1.2.3\n";

        // Act
        StealerExfilDecoder decoder = StealerExfilDecoder.create("abc123.oast.fun");
        Set<String> fqdns = decoder.extractFqdns(copyPasteFromInteractsh);

        // Assert
        assertFalse(fqdns.isEmpty());
        assertTrue(fqdns.contains("some-data-field.1379621077.abc123.oast.fun"));
    }

    @Test
    public void testParseInteractshExport_InteractshFileExport_Fqdns() throws IOException, DecoderException {
        // Arrange
        String expectedDomainName = "abc123.oast.fun";
        String testFileName = "interactsh_export_example.json";
        byte[] jsonBytes;
        try (InputStream resourceStream = getClass().getClassLoader().getResourceAsStream(testFileName)) {
            if (resourceStream == null) {
                throw new IOException("Cannot read resource file '" + testFileName + "'.");
            }
            jsonBytes = resourceStream.readAllBytes();
        }

        // Act
        StealerExfilDecoder decoder = StealerExfilDecoder.create(expectedDomainName);
        Set<String> foundFqdns = decoder.parseInteractshExport(jsonBytes);
        //System.out.println(foundFqdns.toString());
        // Assert
        assertFalse("Found something", foundFqdns.isEmpty());
        assertEquals("Found exact number of queries", 63, foundFqdns.size());
    }

    @Test
    public void testParseInteractshExport_InteractshFileCli_Fqdns() throws IOException, DecoderException {
        // Arrange
        String expectedDomainName = "abc123.oast.fun";
        String testFileName = "interactsh_cli_example.json";
        byte[] jsonBytes;
        try (InputStream resourceStream = getClass().getClassLoader().getResourceAsStream(testFileName)) {
            if (resourceStream == null) {
                throw new IOException("Cannot read resource file '" + testFileName + "'.");
            }
            jsonBytes = resourceStream.readAllBytes();
        }

        // Act
        StealerExfilDecoder decoder = StealerExfilDecoder.create(expectedDomainName);
        Set<String> foundFqdns = decoder.parseInteractshExport(jsonBytes);
        System.out.println(foundFqdns.toString());
        // Assert
        assertFalse("Found something", foundFqdns.isEmpty());
        assertEquals("Found exact number of queries", 58, foundFqdns.size());
    }

    @Test
    public void testFindLongestFqdns_OnlyOneQuery_Result() {
        // Arrange
        SortedSet<String> query = new TreeSet<>();
        query.add("b.c.12345-0.something.example.com");
        query.add("a.b.c.12345-0.something.example.com");
        query.add("c.12345-0.something.example.com");

        // Act
        Set<String> relevantQuery = StealerExfilDecoder.findLongestFqdns(query, "something.example.com");

        // Assert
        assertEquals(1, relevantQuery.size());
        String fqdn = relevantQuery.iterator().next();
        assertEquals("a.b.c.12345-0.something.example.com", fqdn);
    }

    @Test
    public void testFindLongestFqdns_SeveralQueries_Result() {
        // Arrange
        SortedSet<String> queries = new TreeSet<>();
        // First query
        queries.add("b.c.12345-0.something.example.com");
        queries.add("a.b.c.12345-0.something.example.com");
        queries.add("c.12345-0.something.example.com");
        // Second query
        queries.add("b.c.12345-1.something.example.com");
        queries.add("c.12345-1.something.example.com");
        queries.add("a.b.c.12345-1.something.example.com");
        // Third query
        queries.add("one.two.three.6789-0.something.example.com");
        queries.add("three.6789-0.something.example.com");
        queries.add("two.three.6789-0.something.example.com");

        // Act
        Set<String> relevantQueries = StealerExfilDecoder.findLongestFqdns(queries, "something.example.com");

        // Assert
        assertEquals(3, relevantQueries.size());
        Iterator<String> iterator = relevantQueries.iterator();
        String first = iterator.next();
        String second = iterator.next();
        String third = iterator.next();
        assertEquals("a.b.c.12345-0.something.example.com", first);
        assertEquals("a.b.c.12345-1.something.example.com", second);
        assertEquals("one.two.three.6789-0.something.example.com", third);
    }

    @Test
    public void testFindLongestFqdns_Nothing_Nothing() {
        // Arrange
        SortedSet<String> nothing = Collections.emptySortedSet();

        // Act
        Set<String> relevantQuery = StealerExfilDecoder.findLongestFqdns(nothing, "something.example.com");

        // Assert
        assertEquals(0, relevantQuery.size());
    }

    @Test
    public void testFindLongestFqdns_WrongExpectedDomain_Nothing() {
        // Arrange
        SortedSet<String> someOtherQuery = new TreeSet<>();
        someOtherQuery.add("a.b.c.12345-0.something-else.example.com");

        // Act
        Set<String> relevantQuery = StealerExfilDecoder.findLongestFqdns(someOtherQuery, "something.example.com");

        // Assert
        assertEquals(0, relevantQuery.size());
    }

    @Test(expected = DecoderRuntimeException.class)
    public void testFindLongestFqdns_NotSubqueries_Exception() {
        // Arrange
        SortedSet<String> notSubqueries = new TreeSet<>();
        notSubqueries.add("a.b.c.12345-0.something.example.com");
        notSubqueries.add("d.e.f.12345-0.something.example.com");

        // Act
        StealerExfilDecoder.findLongestFqdns(notSubqueries, "something.example.com");
    }

    @Test
    public void testFindLongestFqdns_TopDomainOnly_Nothing() {
        // Arrange
        SortedSet<String> onlyTopDomain = new TreeSet<>();
        onlyTopDomain.add("something.example.com");

        // Act
        Set<String> relevantQuery = StealerExfilDecoder.findLongestFqdns(onlyTopDomain, "something.example.com");

        // Assert
        assertTrue(relevantQuery.isEmpty());
    }

    @Test
    public void testFindLongestFqdns_MetadataPartOnly_Nothing() {
        // Arrange
        SortedSet<String> onlyTopDomain = new TreeSet<>();
        onlyTopDomain.add("12345-0.something.example.com");

        // Act
        Set<String> relevantQuery = StealerExfilDecoder.findLongestFqdns(onlyTopDomain, "something.example.com");

        // Assert
        assertTrue(relevantQuery.isEmpty());
    }

    @Test
    public void testDecodeFqdn_OneRequest_DecodedData() throws DecoderException {
        // Arrange
        String baseDomain = "something.example.com";
        // Only 'host' and 'user':
        String request = "h80ylft7wlwk3gn5d1uggdgucea6j40v54zkg4vzwg9sj1b.860116749-0.something.example.com";

        // Act
        StealerExfilDecoder decoder = StealerExfilDecoder.create(baseDomain);
        Map<String, Map<String, String>> decodedData = decoder.decodeFqdn(List.of(request));

        // Assert
        assertEquals(1, decodedData.size());
        assertTrue("Got ID right", decodedData.containsKey("860116749"));
        Map<String, String> fields = decodedData.get("860116749");
        assertEquals("Got hostname right", "test-host-01", fields.get("host"));
        assertEquals("Got username right", "service-user", fields.get("user"));
    }

    @Test
    public void testDecodeFqdn_SeveralFqdn_DecodedData() throws DecoderException {
        // Arrange
        String baseDomain = "something.example.com";
        List<String> requests = new ArrayList<>(4);
        requests.add("8se1aj857sjdyg5khofr.4jrq1mzr3ba0qezdc3cf.axy5h1e2movi97indkzn.l23gi4rn.1803173851-0.something.example.com");
        requests.add("541ahoy2jt7o0fkq24c5.z2lyw46ss75up0s0940i.eegrm2keoz6hqyaaacd1.1803173851-1.something.example.com");
        requests.add("k7at41cawtdro28xbdrz.eulez6hjfzvmmlxo5x10.nbuuqexd0s7d65by7nlu.1803173851-2.something.example.com");
        requests.add("122723rkvifdg3tu0xeb.1803173851-3.something.example.com");

        // Act
        StealerExfilDecoder decoder = StealerExfilDecoder.create(baseDomain);
        Map<String, Map<String, String>> decodedData = decoder.decodeFqdn(requests);

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
    public void testDecodeRequests_SeveralFqdnInRandomOrder_DecodedData() throws DecoderException {
        // Arrange
        String baseDomain = "something.example.com";
        List<String> requests = new ArrayList<>(4);
        requests.add("k7at41cawtdro28xbdrz.eulez6hjfzvmmlxo5x10.nbuuqexd0s7d65by7nlu.1803173851-2.something.example.com");
        requests.add("541ahoy2jt7o0fkq24c5.z2lyw46ss75up0s0940i.eegrm2keoz6hqyaaacd1.1803173851-1.something.example.com");
        requests.add("122723rkvifdg3tu0xeb.1803173851-3.something.example.com");
        requests.add("8se1aj857sjdyg5khofr.4jrq1mzr3ba0qezdc3cf.axy5h1e2movi97indkzn.l23gi4rn.1803173851-0.something.example.com");

        // Act
        StealerExfilDecoder decoder = StealerExfilDecoder.create(baseDomain);
        Map<String, Map<String, String>> decodedData = decoder.decodeFqdn(requests);

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

    /**
     * Test the sorting of recursive queries of subdomains.
     * <p>For a domain 'x.y.z.example.com', DNS resolvers will issue sub-query for:</p>
     * <li>
     *     <ul>com</ul>
     *     <ul>example.com</ul>
     *     <ul>z.example.com</ul>
     *     <ul>y.z.example.com</ul>
     *     <ul>x.y.z.example.com</ul>
     * </li>
     * <p>Your authoritative DNS server at 'example.com' will not see the sub-query for 'com',
     * but it will see and resolve the rest.</p>
     *
     * @throws DecoderException If the subdomains are wrong
     */
    @Test
    public void testDecodeFqdn_RecursiveRequests_OneQuery() throws DecoderException {
        // Arrange
        String baseDomain = "something.example.com";
        List<String> requests = new ArrayList<>(18);
        // SeqNo 2 subdomains 2 3 0 1 D
        requests.add("eulez6hjfzvmmlxo5x10.nbuuqexd0s7d65by7nlu.1803173851-2.something.example.com");
        requests.add("k7at41cawtdro28xbdrz.eulez6hjfzvmmlxo5x10.nbuuqexd0s7d65by7nlu.1803173851-2.something.example.com");
        requests.add("1803173851-2.something.example.com");
        requests.add("nbuuqexd0s7d65by7nlu.1803173851-2.something.example.com");
        requests.add("something.example.com");

        // SeqNo 1 subdomains D 0 1 2 3
        requests.add("something.example.com");
        requests.add("1803173851-1.something.example.com");
        requests.add("eegrm2keoz6hqyaaacd1.1803173851-1.something.example.com");
        requests.add("z2lyw46ss75up0s0940i.eegrm2keoz6hqyaaacd1.1803173851-1.something.example.com");
        requests.add("541ahoy2jt7o0fkq24c5.z2lyw46ss75up0s0940i.eegrm2keoz6hqyaaacd1.1803173851-1.something.example.com");

        // SeqNo 3
        requests.add("122723rkvifdg3tu0xeb.1803173851-3.something.example.com");
        requests.add("1803173851-3.something.example.com");

        // SeqNo 0 subdomains 4 0 3 1 2 D
        requests.add("8se1aj857sjdyg5khofr.4jrq1mzr3ba0qezdc3cf.axy5h1e2movi97indkzn.l23gi4rn.1803173851-0.something.example.com");
        requests.add("1803173851-0.something.example.com");
        requests.add("4jrq1mzr3ba0qezdc3cf.axy5h1e2movi97indkzn.l23gi4rn.1803173851-0.something.example.com");
        requests.add("l23gi4rn.1803173851-0.something.example.com");
        requests.add("axy5h1e2movi97indkzn.l23gi4rn.1803173851-0.something.example.com");
        requests.add("something.example.com");

        // Act
        StealerExfilDecoder decoder = StealerExfilDecoder.create(baseDomain);
        Map<String, Map<String, String>> decodedData = decoder.decodeFqdn(requests);

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

    /**
     * Test conflicting subdomains within the same recursive query.
     * This would be an invalid behaviour by a DNS resolver.
     *
     * @throws DecoderException Expected
     */
    @Test(expected = DecoderException.class)
    public void testDecodeFqdn_ConflictingSubdomains_Exception() throws DecoderException {
        // Arrange
        String baseDomain = "something.example.com";
        List<String> requests = new ArrayList<>(18);
        requests.add("y.z.123-0.something.example.com");
        requests.add("z.123-0.something.example.com");
        requests.add("x.B.z.123-0.something.example.com");  // This is wrong
        requests.add("B.z.123-0.something.example.com");    // This is also wrong

        // Act
        StealerExfilDecoder decoder = StealerExfilDecoder.create(baseDomain);
        decoder.decodeFqdn(requests);   // Expect exception
    }

    /**
     * Test a collision on uniqueId and seqNo.
     * This could either be a very odd chance (uniqueId collision) or someone/something enumerating subdomains of your
     * uniqueId-seqNo combo.
     * It's not unthinkable that a blue team would try to probe your domains for clues.
     * As it stands right now, this would actually be an effective way of disrupting your data set (the decoder can't
     * handle conflicting data on the same uniqueId).
     *
     * @throws DecoderException Expected
     */
    @Test(expected = DecoderException.class)
    public void testDecodeFqdn_ConflictingSequenceNumbers_Exception() throws DecoderException {
        // Arrange
        String baseDomain = "something.example.com";
        List<String> requests = new ArrayList<>(18);
        requests.add("x.y.z.123-0.something.example.com");
        requests.add("a.b.c.123-0.something.example.com");

        // Act
        StealerExfilDecoder decoder = StealerExfilDecoder.create(baseDomain);
        decoder.decodeFqdn(requests);   // Expect exception
    }
    @Ignore("This test covers the case of packet loss, something the application does not support.")
    @Test(expected = DecoderException.class)
    public void testDecodeFqdn_MissingSequenceNumbers_Exception() throws DecoderException {
        // Arrange
        String baseDomain = "something.example.com";
        List<String> requests = new ArrayList<>(18);
        requests.add("4jrq1mzr3ba0qezdc3cf.axy5h1e2movi97indkzn.l23gi4rn.572518471-0.something.example.com");
        requests.add("z2lyw46ss75up0s0940i.eegrm2keoz6hqyaaacd1.8se1aj857sjdyg5khofr.572518471-1.something.example.com");
        requests.add("122723rkvifdg3tu0xeb.k7at41cawtdro28xbdrz.572518471-3.something.example.com");

        // Act
        StealerExfilDecoder decoder = StealerExfilDecoder.create(baseDomain);
        decoder.decodeFqdn(requests);   // Expect exception
    }
}