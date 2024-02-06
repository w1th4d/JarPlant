package org.example.implants;

public class ClassImplant implements Runnable, Thread.UncaughtExceptionHandler {
    public static void init() {
        // "java.class.init" is a made up property used to determine if an implant is already running in this JVM
        // (as could the case be if more than one class is infected)
        if (System.getProperty("java.class.init") == null) {
            if (System.setProperty("java.class.init", "true") == null) {
                System.out.println("This is the implant running (once per JVM)!");
                ClassImplant implant = new ClassImplant();
                Thread background = new Thread(implant);
                background.setDaemon(true); // This means that the thread will die when the main thread is done
                background.setUncaughtExceptionHandler(implant);
                background.start();
            }
        }
    }

    @Override
    public void run() {
        try {
            Thread.sleep(3000);
        } catch (InterruptedException ignored) {
            System.out.println("Interrupted.");
        }

        System.out.println("Implant goes BOOM!");
        throw new RuntimeException("This is intentional.");
    }

    @Override
    public void uncaughtException(Thread thread, Throwable throwable) {
        // Silently ignore (don't throw up error messages on stderr)
    }
}
