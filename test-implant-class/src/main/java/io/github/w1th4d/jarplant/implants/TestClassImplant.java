package io.github.w1th4d.jarplant.implants;

import org.apache.commons.io.HexDump;

import java.io.IOException;

public class TestClassImplant {
    @SuppressWarnings("all")
    private static volatile String CONF_STRING = "Original";
    @SuppressWarnings("all")
    private static volatile boolean CONF_BOOLEAN = false;
    @SuppressWarnings("all")
    private static volatile int CONF_INT = 1;

    /*
     * The init() is called from <clinit> that may contain variable initialization code. As init() returns and the
     * <clinit> continues, these may change the config variables. Hence, depending on how the Injector handles
     * existing bytecode in <clinit>, the config properties may differ at the time of init() and later (like when
     * things happen in a thread). This needs to be tested for.
     */

    @SuppressWarnings("unused")
    public static String init() {
        Thread.dumpStack();

        // Use something from an internal dependency (ie just another class form this project)
        TestDependencyClass otherClass = new TestDependencyClass();
        otherClass.doSomething();

        // Use something from an external dependency
        try {
            HexDump.dump(new byte[]{1, 2, 3, 4, 5}, System.out);
        } catch (IOException ignored) {
        }

        return getConfigDump();
    }

    public static String getConfigDump() {
        Thread.dumpStack();
        return "CONF_STRING=\"" + CONF_STRING + "\";CONF_BOOLEAN=" + CONF_BOOLEAN + ";CONF_INT=" + CONF_INT + ";";
    }
}
