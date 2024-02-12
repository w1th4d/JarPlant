package org.example.implants;

/**
 * A kind of inventory for the bundled implants.
 * <p>There's no need to add custom implants to this list if you only intend to use it privately and load it
 * directly from a file.</p>
 * <p>However, if you want to publish it as a general purpose implant and bundle it with JarPlant, then please
 * <ul>
 *    <li>Fork the project over at GitHub.</li>
 *    <li>Add your implant class(es) to the Maven module `jarplant-implants` under the `org.example.implants`
 *  package.</li>
 *    <li>dd a new value to the `ImplantInfo` enum as appropriate.</li>
 *    <li>Create a Pull Request on the official GitHub repo.</li>
 * </ul></p>
 * <p>Sharing is caring! <3</p>
 */
public enum ImplantInfo {
    ClassImplant(
            org.example.implants.ClassImplant.class,
            "Template for a class implant."),
    SpringImplantController(
            org.example.implants.SpringImplantController.class,
            "Template for a Spring REST controller."),
    SpringImplantConfiguration(
            org.example.implants.SpringImplantConfiguration.class,
            "Template for adding your Spring implant component to a Spring configuration class.");

    public final Class<?> clazz;
    public final String summary;

    ImplantInfo(Class<?> clazz, String summary) {
        this.clazz = clazz;
        this.summary = summary;
    }
}
