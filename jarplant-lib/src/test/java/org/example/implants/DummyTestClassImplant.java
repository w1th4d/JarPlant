package org.example.implants;

/**
 * Bare minimum class implant.
 * Used to test the ImplantHandler itself. Not used for end-to-end tests. See the <code>test-implant-class</code>
 * Maven submodule for that.
 */
public class DummyTestClassImplant {
    @SuppressWarnings("unused")
    public static void init() {
        String something = DummyDependency.somethingUseful();
    }
}
