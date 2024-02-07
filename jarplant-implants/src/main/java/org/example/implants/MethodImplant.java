package org.example.implants;

/**
 * Template class for the MethodInjector.
 * <p>This implant type is the most rudimentary. It's very limited in its capabilities. As of now, only one method
 * can be injected using this method. Using classes (and using methods from) the standard Java library is supported.
 * Using custom classes is not supported out of the box. More interestingly, calling other static methods
 * (aka functions) is not supported yet. Avoid using fancy language features such as lambdas (these are compiled as
 * subclasses which is not supported).</p>
 * <p>This implant is more stealthy in the sense that it _merges_ in with the target class (so no additional class file
 * required). As of now, this only operates on a single class file (not an entire JAR).</p>
 * <p>More work could be done on this implant type. Experiments have been made to merge an *entire class* into a target
 * class, but this turns out to be more complicated than first anticipated.</p>
 */
public class MethodImplant {
    /**
     * JVM system property to create and use as a "marker" to determine if an implant has been detonated in this JVM.
     * This property name could be anything that does not already naturally exist in the JVM. Just make it blend in.
     */
    static final String CONF_JVM_MARKER_PROP = "java.class.init";

    /**
     * The entry point in this implant class.
     * <p>The MethodInjector will copy this method to the target and modify the target's class initializer function
     * to invoke this method.</p>
     * <p>Note that this function will be called by an unsuspecting caller just trying to create an object.
     * DO NOT BLOCK THIS THREAD! Do the thing quickly and return.</p>
     */
    @SuppressWarnings("unused")
    public static void init() {
        if (System.getProperty(CONF_JVM_MARKER_PROP) == null) {
            if (System.setProperty(CONF_JVM_MARKER_PROP, "true") == null) {

                // ---------- BEGIN PAYLOAD CODE HERE ----------
                System.out.println("BOOM!");

            }
        }
    }
}
