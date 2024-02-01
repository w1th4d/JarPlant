package org.example.implants;

public class ClassImplant implements Runnable {
    public static void implant() {
        // "java.class.init" is a made up property used to determine if an implant is already running in this JVM
        // (as could the case be if more than one class is infected)
        if (System.getProperty("java.class.init") == null) {
            if (System.setProperty("java.class.init", "true") == null) {
                System.out.println("This is the implant running (once per JVM)!");
                Thread background = new Thread(new ClassImplant());
                background.setUncaughtExceptionHandler(null);
                //Runtime.getRuntime().addShutdownHook(background);   // This is just to prevent it from getting GC:d
                background.start();
            }
        }
    }

    @Override
    public void run() {
        try {
            Thread.sleep(3000);
        } catch (InterruptedException ignored) {
        }

        System.out.println("Doing the thing!");
    }
}
