package org.example.implants;

public class TestImplant {
    private static volatile String CONF_STRING = "Test configuration!";
    private static volatile boolean CONF_BOOLEAN = false;
    private static volatile int CONF_INT = 1337;

    @SuppressWarnings("unused")
    public static String init() {
        return "CONF_STRING=\"" + CONF_STRING + "\";CONF_BOOLEAN=" + CONF_BOOLEAN + ";CONF_INT=" + CONF_INT + ";";
    }
}
