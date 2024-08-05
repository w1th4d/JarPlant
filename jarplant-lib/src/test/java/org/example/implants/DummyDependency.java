package org.example.implants;

public class DummyDependency {
    public static String somethingUseful() {
        return "This is directly used. + " + DummySubDependency.somethingUseful();
    }
}
