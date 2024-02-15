package org.example;

import java.net.URL;
import java.nio.file.Path;

public class TestHelpers {
    public static Path findTestEnvironmentDir(Class<?> testClass) {
        // This perhaps naively exploits the fact that the root resource path will be the `target/test-classes` dir.
        URL testClassesDir = testClass.getClassLoader().getResource("");
        if (testClassesDir == null) {
            throw new RuntimeException("Failed to find resource directory for testing environment.");
        }

        return Path.of(testClassesDir.getPath());
    }
}
