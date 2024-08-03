package org.example.implants;

import java.util.Arrays;

public class TestClassImplant {
    private static final String CONF_STRING = "Original";
    private static final boolean CONF_BOOLEAN = false;
    private static final int CONF_INT = 1;

    /*
     * The init() is called from <clinit> that may contain variable initialization code. As init() returns and the
     * <clinit> continues, these may change the config variables. Hence, depending on how the Injector handles
     * existing bytecode in <clinit>, the config properties may differ at the time of init() and later (like when
     * things happen in a thread). This needs to be tested for.
     */

    @SuppressWarnings("unused")
    public static void init() {
        System.out.println("BOOM!");
        System.out.println();

        StackTraceElement[] stackTraceElements = Thread.getAllStackTraces().get(Thread.currentThread());
        System.out.println("Stack trace:");
        Arrays.stream(stackTraceElements).forEach(line -> System.out.println("   " + line));
        System.out.println("Config: ");
        System.out.println("   CONF_STRING: " + CONF_STRING);
        System.out.println("   CONF_BOOLEAN: " + CONF_BOOLEAN);
        System.out.println("   CONF_INT: " + CONF_INT);
        System.out.println();
    }
}
