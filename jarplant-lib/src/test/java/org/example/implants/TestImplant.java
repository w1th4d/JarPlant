package org.example.implants;

public class TestImplant {
    private static volatile String CONF_STRING = "Original";
    private static volatile boolean CONF_BOOLEAN = false;
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
        return getConfigDump();
    }

    public static String getConfigDump() {
        Thread.dumpStack();
        return "CONF_STRING=\"modified\";CONF_BOOLEAN=true;CONF_INT=2;";
    }
}
