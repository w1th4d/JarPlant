package io.github.w1th4d.jarplant.implants;

import javassist.bytecode.ClassFile;

public class DummyDependency {
    @SuppressWarnings("unused")
    public static String somethingUseful() {
        // Use an external dependency class
        ClassFile something = new ClassFile(false, "Something", null);

        // Use an internal dependency class
        return "This is directly used. + " + DummySubDependency.somethingUseful();
    }
}
