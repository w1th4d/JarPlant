package io.github.w1th4d.jarplant.implants;

public class DummyDependency {
    public static String somethingUseful() {
        return "This is directly used. + " + DummySubDependency.somethingUseful();
    }
}
