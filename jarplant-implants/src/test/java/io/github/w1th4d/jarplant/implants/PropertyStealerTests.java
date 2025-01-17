package io.github.w1th4d.jarplant.implants;

import org.junit.Before;
import org.junit.Test;

import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PropertyStealerTests {
    public static final Properties GLOBAL_PROPERTIES = new Properties();

    @Before
    public void setAppConfig() {
        System.setProperty("config.database.url", "jdbc:mysql://localhost:3306/mydb");
        System.setProperty("config.server.port", "8080");
        System.setProperty("config.username","root");
        System.setProperty("config.password","secret");
        System.setProperty("application.name", "Proudhon");
        System.setProperty("application.version", "1.0");
        // Uncomment to test global properties.
        // GLOBAL_PROPERTIES.putAll(System.getProperties());
    }

    @Test
    public void testMatchingOneProperty() {
        // Act
        PropertyStealer.CONF_STEAL_THIS_KEY_REGEX = ".*password.*";
        Properties matching = PropertyStealer.matchProperties(System.getProperties());

        // Assert
        assertTrue("Matched system property.", matching.containsValue("secret"));
    }

    @Test
    public void testMatchingMultipleProperties() {
        // Act
        PropertyStealer.CONF_STEAL_THIS_KEY_REGEX = ".*config.*";
        Properties matching = PropertyStealer.matchProperties(System.getProperties());

        // Assert
        assertEquals("Matched multiple system properties.", 4, matching.size());
    }
}
