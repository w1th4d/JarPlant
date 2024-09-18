package io.github.w1th4d.jarplant.implants.utils;

import org.junit.Test;

import java.io.IOException;
import java.util.Set;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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
}
