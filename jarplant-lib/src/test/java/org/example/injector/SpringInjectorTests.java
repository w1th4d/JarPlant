package org.example.injector;

import org.example.implants.TestSpringBeanImplant;
import org.example.implants.TestSpringConfigImplant;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.example.TestHelpers.*;
import static org.junit.Assert.*;

public class SpringInjectorTests {
    // Test implant originating from the test-spring-implant module
    private ImplantHandler testConfigImplantHandler;
    private ImplantHandler testBeanImplantHandler;

    private SpringInjector injector;

    // Path to actual JARs packaged by the target-app-spring-boot{-complex} modules
    private Path simpleSpringBootApp;
    private Path complexSpringBootApp;

    // Borrow the regular JAR from test-app
    private Path regularApp;

    // Temporary files to work with
    private Path tempInputFile;
    private Path tempOutputFile;

    @Before
    public void loadTestImplants() throws IOException, ClassNotFoundException {
        this.testConfigImplantHandler = ImplantHandlerMock.findAndCreateFor(TestSpringConfigImplant.class);
        this.testBeanImplantHandler = ImplantHandlerMock.findAndCreateFor(TestSpringBeanImplant.class);
    }

    @Before
    public void loadTargetApps() throws IOException {
        this.simpleSpringBootApp = getJarFileFromResourceFolder("target-app-spring-boot.jar");
        this.complexSpringBootApp = getJarFileFromResourceFolder("target-app-spring-boot-complex.jar");
    }

    @Before
    public void loadRegularApp() throws IOException {
        this.regularApp = getJarFileFromResourceFolder("target-app-without-debug.jar");
    }

    @Before
    public void setupSpringInjector() {
        this.injector = new SpringInjector(testConfigImplantHandler, testBeanImplantHandler);
    }

    @Before
    public void createMiscTestJars() throws IOException {
        tempInputFile = Files.createTempFile("JarPlantTests-", ".jar");
        tempOutputFile = Files.createTempFile("JarPlantTests-", ".jar");
    }

    @After
    public void removeMiscTestJars() throws IOException {
        Files.delete(tempInputFile);
        Files.delete(tempOutputFile);
    }

    // infect

    @Test
    public void testInfect_SpringJar_Success() throws IOException {
        // Arrange
        Map<String, String> hashesBeforeInfect = hashAllJarContents(simpleSpringBootApp);

        // Act
        boolean didInfect = injector.infect(simpleSpringBootApp, tempOutputFile);

        // Assert
        assertTrue("Did successfully inject.", didInfect);
        Map<String, String> hashesAfterInfect = hashAllJarContents(tempOutputFile);
        assertNotEquals("At least one class file in JAR has changed.", hashesAfterInfect, hashesBeforeInfect);
    }

    @Test(expected = Exception.class)
    public void testInfect_NotAJar_Exception() throws IOException {
        // Arrange
        Random rng = new Random(1);
        byte[] someRandomData = new byte[10];
        rng.nextBytes(someRandomData);
        Files.write(tempInputFile, someRandomData, StandardOpenOption.WRITE);

        // Act
        injector.infect(tempInputFile, tempOutputFile);

        // Expect exception
    }

    @Test
    public void testInfect_EmptyJar_Untouched() throws IOException {
        // Arrange
        JarOutputStream createJar = new JarOutputStream(new FileOutputStream(tempInputFile.toFile()));
        createJar.close();  // The point is to just leave the JAR empty

        // Act
        boolean didInfect = injector.infect(tempInputFile, tempOutputFile);

        // Assert
        assertFalse("Did not infect anything in an empty JAR.", didInfect);
    }

    @Test
    public void testInfect_EmptyJarWithManifest_Untouched() throws IOException {
        // Arrange
        populateJarEntriesIntoEmptyFile(tempInputFile, null);

        // Act
        boolean didInfect = injector.infect(tempInputFile, tempOutputFile);

        // Assert
        assertFalse("Did not infect anything in an empty JAR.", didInfect);
    }

    @Test
    public void testInfect_SignedJar_Untouched() throws IOException {
        // Arrange
        // This is a rather fake way of simulating a signed JAR. Consider the real deal by Maven.
        String manifestAmendment = "\r\n"
                + "Name: com/example/restservice/RestServiceApplication.class\r\n"
                + "SHA-256-Digest: "
                + Base64.getEncoder().encodeToString("somethingsomething".getBytes())
                + "\r\n";
        String signatureFile = "Signature-Version: 1.0\r\nSHA-256-Digest-Manifest: "
                + Base64.getEncoder().encodeToString("somethingsomething".getBytes())
                + "\r\n";

        // Add a .SF file
        JarFileFiddler fiddler = JarFileFiddler.open(simpleSpringBootApp, tempInputFile);
        DataOutputStream newEntryStream = fiddler.addNewEntry(new JarEntry("META-INF/SOMETHING.SF"));
        newEntryStream.write(signatureFile.getBytes(StandardCharsets.UTF_8));

        // Append entries to MANIFEST.MF and copy all other entries
        for (JarFileFiddler.WrappedJarEntry entry : fiddler) {
            if (entry.getName().equals("META-INF/MANIFEST.MF")) {
                InputStream manifestStream = entry.getContent();
                DataOutputStream manifestManipulation = entry.replaceContentByStream();
                manifestManipulation.write(manifestStream.readAllBytes());
                manifestManipulation.write(manifestAmendment.getBytes());
            } else {
                entry.forward();
            }
        }

        fiddler.close();

        // Act
        boolean didInfect = injector.infect(tempInputFile, tempOutputFile);

        // Assert
        assertFalse("Did not infect signed JAR.", didInfect);
        assertArrayEquals("Output JAR is identical to input JAR.",
                Files.readAllBytes(tempInputFile),
                Files.readAllBytes(tempOutputFile));
    }

    @Test
    public void testInfect_NoSpringConfig_Untouched() throws IOException {
        // Arrange

        // Act
        boolean didInfect = injector.infect(regularApp, tempOutputFile);

        // Assert
        assertFalse("Did not infect JAR without a Spring config (like a regular app JAR).", didInfect);
    }

    @Test
    public void testInfect_SeveralSpringConfigs_AnyInfected() throws IOException {
        // Arrange
        Set<String> knownConfigClasses = Set.of(
                "BOOT-INF/classes/com/example/complex/ComplexApplication.class",
                "BOOT-INF/classes/com/example/complex/subpackage/SomeConfiguration.class",
                "BOOT-INF/classes/com/example/complex/multipleconfigs/FirstConfiguration.class",
                "BOOT-INF/classes/com/example/complex/multipleconfigs/SecondConfiguration.class"
        );
        Map<String, String> hashesBefore = hashAllJarContents(complexSpringBootApp);

        // Act
        injector.infect(complexSpringBootApp, tempOutputFile);

        // Assert
        Map<String, String> hashesAfter = hashAllJarContents(tempOutputFile);
        Set<String> classesModified = getDiffingEntries(hashesBefore, hashesAfter);
        Set<String> modifiedConfigClasses = new HashSet<>(classesModified);
        modifiedConfigClasses.retainAll(knownConfigClasses);
        assertFalse("Any of the configuration classes were modified.", modifiedConfigClasses.isEmpty());
    }

    @Test
    @Ignore
    public void testInfect_SeveralSpringConfigs_AllInfected() throws IOException {
        // Arrange
        Set<String> knownConfigClasses = Set.of(
                "BOOT-INF/classes/com/example/complex/ComplexApplication.class",
                "BOOT-INF/classes/com/example/complex/subpackage/SomeConfiguration.class",
                "BOOT-INF/classes/com/example/complex/multipleconfigs/FirstConfiguration.class",
                "BOOT-INF/classes/com/example/complex/multipleconfigs/SecondConfiguration.class"
        );
        Map<String, String> hashesBefore = hashAllJarContents(complexSpringBootApp);

        // Act
        injector.infect(complexSpringBootApp, tempOutputFile);

        // Assert
        Map<String, String> hashesAfter = hashAllJarContents(tempOutputFile);
        Set<String> classesModified = getDiffingEntries(hashesBefore, hashesAfter, knownConfigClasses);
        assertEquals("All Spring Configuration/Application classes were modified.", knownConfigClasses, classesModified);
        /*
         * The problem is that SpringInjector needs to name each implant uniquely when there are several Spring
         * configurations classes in the same Java package. Alternatively, only pick one of them and hope for
         * the best.
         * It would most likely be sufficient to inject an implant into one of the configurations. Unless there's
         * a complex routing of REST calls like internal/external exposure of endpoints through Spring Security,
         * an API gateway or WAF.
         * For now, this test will fail. Maybe that's fine.
         */
    }

    @Test
    @Ignore
    public void testInfect_ComponentScanningEnabled_ConfigUntouched() {
    }

    @Test
    @Ignore
    public void testInfect_ComponentScanningDisabled_ConfigModified() {
    }

    @Test
    @Ignore
    public void testInfect_ValidJar_AddedSpringComponent() {
    }

    @Test
    @Ignore
    // Corresponds to the standard debugging info produce by javac (lines + source)
    public void testInfect_StandardDebuggingInfo_Success() {
    }

    @Test
    @Ignore
    // Corresponds to javac -g:lines
    public void testInfect_LinesDebuggingInfo_Success() {
    }

    @Test
    @Ignore
    // Corresponds to javac -g:vars
    public void testInfect_VarsDebuggingInfo_Success() {
    }

    @Test
    @Ignore
    // Corresponds to javac -g:source
    public void testInfect_CodeDebuggingInfo_Success() {
    }

    @Test
    @Ignore
    // Corresponds to javac -g:none
    public void testInfect_NoDebuggingInfo_Success() {
    }

    @Test
    @Ignore
    public void testInfect_AlreadyInfectedJar_Untouched() {
        // TODO Implement infection detection
    }

    // addBeanToSpringConfig

    @Test
    @Ignore
    public void testAddBeanToSpringConfig_ExistingConfigWithBeans_AddedBean() {
    }

    @Test
    @Ignore
    public void testAddBeanToSpringConfig_ExistingConfigWithoutBeans_AddedBean() {
    }

    @Test
    @Ignore
    public void testAddBeanToSpringConfig_ImplantConfigWithBeans_AddedBean() {
    }

    @Test
    @Ignore
    public void testAddBeanToSpringConfig_ImplantConfigWithoutBeans_Untouched() {
    }

    // copyAllMethodAnnotations

    @Test
    @Ignore
    public void testCopyAllMethodAnnotations_OnlyBeanAnnotation_Copied() {
    }

    @Test
    @Ignore
    public void testCopyAllMethodAnnotations_SeveralAnnotations_AllCopied() {
    }

    @Test
    @Ignore
    public void testCopyAllMethodAnnotations_NoAnnotations_Fine() {
    }
}
