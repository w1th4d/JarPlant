package org.example.implants;

public class Stub {
    public static void init() {
        // "java.class.init" is a made up property used to determine if an implant is already running in this JVM
        // (as could the case be if more than one class is infected)
        if (System.getProperty("java.class.init") == null) {
            if (System.setProperty("java.class.init", "true") == null) {
                System.out.println("This is the implant running (once per JVM)!");
            }
        }
    }
}
