package org.example;

/**
 * A test class containing some fields and methods.
 * Implicitly used by HelpersTests (by dynamically parsing this class file at runtime).
 * This class may show up as "unused" in IDEs, but it's not.
 */
@SuppressWarnings("unused")
public class TestClass {
    // Maintenance note: Don't initialize these as it will create a <clinit> for this class (we need it to not have one)
    public static int staticField;
    public volatile int volatileField;
    public static volatile int staticVolatileField;

    public void regularMethod() {
    }

    public static void staticMethod() {
    }

    public static double methodWithSomeCode() {
        // Nonsense code ahead
        double a = Math.random();
        double b = Math.random();
        if (a == b) {
            return 0;   // Note the mid-method return, this is subject to testing
        }
        return a + b;
    }
}
