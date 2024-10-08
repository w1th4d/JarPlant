package io.github.w1th4d.jarplant;

import javassist.bytecode.*;
import javassist.bytecode.annotation.Annotation;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static io.github.w1th4d.jarplant.TestHelpers.*;
import static org.junit.Assert.*;

public class SpringInjectorTests {
    // Test implant originating from the test-implant-spring module
    private ImplantHandler testConfigImplantHandler;
    private ImplantHandler testBeanImplantHandler;

    private SpringInjector injector;

    // Path to actual JARs packaged by the test-app-spring-{simple,complex} modules
    private Path simpleSpringBootApp;
    private Path complexSpringBootApp;

    // Borrow the regular JAR from test-app
    private Path regularApp;

    // Temporary files to work with
    private Path tempInputFile;
    private Path tempOutputFile;

    @Before
    public void configureLogger() {
        TestHelpers.configureLogger();
    }

    @Before
    public void loadTestImplants() throws IOException {
        Path testSpringImplantPath = getJarFileFromResourceFolder("test-implant-spring.jar");
        this.testConfigImplantHandler = ImplantHandlerMock.findInJar(testSpringImplantPath, "TestSpringConfigImplant");
        this.testBeanImplantHandler = ImplantHandlerMock.findInJar(testSpringImplantPath, "TestSpringBeanImplant");
    }

    @Before
    public void loadTargetApps() throws IOException {
        this.simpleSpringBootApp = getJarFileFromResourceFolder("test-app-spring-simple.jar");
        this.complexSpringBootApp = getJarFileFromResourceFolder("test-app-spring-complex.jar");
    }

    @Before
    public void loadRegularApp() throws IOException {
        this.regularApp = getJarFileFromResourceFolder("test-app-pojo-without-debug.jar");
    }

    @Before
    public void setupSpringInjector() throws ImplantException {
        this.injector = SpringInjector.createLoadedWith(testBeanImplantHandler, testConfigImplantHandler);
    }

    @Before
    public void createMiscTestJars() throws IOException {
        tempInputFile = Files.createTempFile("JarPlantTests-", ".jar");
        tempOutputFile = Path.of(tempInputFile.toAbsolutePath() + "-output.jar");
    }

    @After
    public void removeTempInputFile() throws IOException {
        if (tempInputFile != null && Files.exists(tempInputFile)) {
            Files.delete(tempInputFile);
        }
    }

    @After
    public void removeTempOutputFile() throws IOException {
        if (tempOutputFile != null && Files.exists(tempOutputFile)) {
            Files.delete(tempOutputFile);
        }
    }

    @Test(expected = ImplantException.class)
    public void testCreate_InvalidImplant_ImplantException() throws ImplantException {
        // Arrange
        ImplantHandler invalidImplant = new ImplantHandlerMock(generateDummyClassFile("io.github.w1th4d.jarplant.implants.InvalidImplant"));

        // Act
        SpringInjector.createLoadedWith(invalidImplant, invalidImplant);    // Should throw up
    }

    @Test
    public void testInjectInto_SpringJar_Success() throws IOException {
        // Arrange
        JarFiddler jar = JarFiddler.buffer(simpleSpringBootApp);
        Map<String, String> hashesBeforeInfect = hashAllJarContents(jar);

        // Act
        boolean didInfect = injector.injectInto(jar);

        // Assert
        assertTrue("Did successfully inject.", didInfect);
        Map<String, String> hashesAfterInfect = hashAllJarContents(jar);
        assertNotEquals("At least one class file in JAR has changed.", hashesAfterInfect, hashesBeforeInfect);
    }

    @Test
    public void testInjectInto_EmptyJar_Untouched() throws IOException {
        // Arrange: Create an empty JAR
        JarOutputStream createJar = new JarOutputStream(new FileOutputStream(tempInputFile.toFile()));
        createJar.close();
        JarFiddler jar = JarFiddler.buffer(tempInputFile);

        // Act
        boolean didInfect = injector.injectInto(jar);

        // Assert
        assertFalse("Did not infect anything in an empty JAR.", didInfect);
    }

    @Test
    public void testInjectInto_EmptyJarWithManifest_Untouched() throws IOException {
        // Arrange: Create a JAR with only a manifest but no classes
        populateJarEntriesIntoEmptyFile(tempInputFile, null);
        JarFiddler jar = JarFiddler.buffer(tempInputFile);

        // Act
        boolean didInfect = injector.injectInto(jar);

        // Assert
        assertFalse("Did not infect anything in an empty JAR.", didInfect);
    }

    @Test
    public void testInjectInto_SignedJar_Untouched() throws IOException, DuplicateEntryException {
        // Arrange
        BufferedJarFiddler jar = JarFiddler.buffer(simpleSpringBootApp);

        // This is a rather fake way of simulating a signed JAR. Consider the real deal by Maven.
        String manifestAmendment = "\r\n"
                + "Name: io/github/w1th4d/jarplant/test/restservice/RestServiceApplication.class\r\n"
                + "SHA-256-Digest: "
                + Base64.getEncoder().encodeToString("somethingsomething".getBytes())
                + "\r\n";
        String signatureFile = "Signature-Version: 1.0\r\nSHA-256-Digest-Manifest: "
                + Base64.getEncoder().encodeToString("somethingsomething".getBytes())
                + "\r\n";

        // Add a .SF file
        jar.addNewEntry(
                new JarEntry("META-INF/SOMETHING.SF"),
                signatureFile.getBytes(StandardCharsets.UTF_8)
        );

        // Append entries to MANIFEST.MF and copy all other entries
        JarFiddler.Entry manifestEntry = jar.getEntry("META-INF/MANIFEST.MF").orElseThrow();
        ByteArrayOutputStream newManifestContent = new ByteArrayOutputStream();
        newManifestContent.write(manifestEntry.getContent());
        newManifestContent.write(manifestAmendment.getBytes(StandardCharsets.UTF_8));
        manifestEntry.replaceContentWith(newManifestContent.toByteArray());

        // Act
        boolean didInfect = injector.injectInto(jar);

        // Assert
        assertFalse("Did not infect signed JAR.", didInfect);
    }

    @Test
    public void testInjectInto_NoSpringConfig_Untouched() throws IOException {
        // Arrange
        JarFiddler jar = JarFiddler.buffer(regularApp);

        // Act
        boolean didInfect = injector.injectInto(jar);

        // Assert
        assertFalse("Did not infect JAR without a Spring config (like a regular app JAR).", didInfect);
    }

    @Test
    public void testInjectInto_SeveralSpringConfigsNoComponentScanning_AnyInfected() throws IOException {
        // Arrange
        Set<String> knownConfigClasses = Set.of(
                "BOOT-INF/classes/io/github/w1th4d/jarplant/test/complex/ComplexApplication.class",
                "BOOT-INF/classes/io/github/w1th4d/jarplant/test/complex/subpackage/SomeConfiguration.class",
                "BOOT-INF/classes/io/github/w1th4d/jarplant/test/complex/multipleconfigs/FirstConfiguration.class",
                "BOOT-INF/classes/io/github/w1th4d/jarplant/test/complex/multipleconfigs/SecondConfiguration.class"
        );
        JarFiddler jar = JarFiddler.buffer(complexSpringBootApp);
        Map<String, String> hashesBefore = hashAllJarContents(jar);

        // Act
        injector.injectInto(jar);

        // Assert
        Map<String, String> hashesAfter = hashAllJarContents(jar);
        Set<String> classesModified = getDiffingEntries(hashesBefore, hashesAfter);
        assertFalse("Any of the configuration classes were modified.",
                setIntersection(classesModified, knownConfigClasses).isEmpty());
    }

    @Test
    @Ignore // TODO Fix failing test. See comment block.
    public void testInjectInto_SeveralSpringConfigsNoComponentScanning_AllInfected() throws IOException {
        // Arrange
        Set<String> knownConfigClasses = Set.of(
                "BOOT-INF/classes/io/github/w1th4d/jarplant/test/complex/ComplexApplication.class",
                "BOOT-INF/classes/io/github/w1th4d/jarplant/test/complex/subpackage/SomeConfiguration.class",
                "BOOT-INF/classes/io/github/w1th4d/jarplant/test/complex/multipleconfigs/FirstConfiguration.class",
                "BOOT-INF/classes/io/github/w1th4d/jarplant/test/complex/multipleconfigs/SecondConfiguration.class"
        );
        JarFiddler jar = JarFiddler.buffer(complexSpringBootApp);
        Map<String, String> hashesBefore = hashAllJarContents(jar);

        // Act
        injector.injectInto(jar);

        // Assert
        Map<String, String> hashesAfter = hashAllJarContents(jar);
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

    /**
     * Makes sure that an app that has component scanning (a Spring auto-magic thing) enabled does not need its
     * configuration classes modified.
     * As of now, there's an admitted flaw in the test suite: The basic Spring Boot test app may use either
     * `@SpringBootApplication` or `@ComponentScan` and this test will be able to test both.
     */
    @Test
    public void testInjectInto_ComponentScanningEnabled_ConfigUntouched() throws IOException {
        // Arrange
        Set<String> knownConfigClasses = Set.of(
                "BOOT-INF/classes/io/github/w1th4d/jarplant/test/simple/SimpleApplication.class"
        );
        JarFiddler jar = JarFiddler.buffer(simpleSpringBootApp);
        Map<String, String> hashesBefore = hashAllJarContents(jar);

        // Act
        injector.injectInto(jar);

        // Assert
        Map<String, String> hashesAfter = hashAllJarContents(jar);
        Set<String> modifiedFiles = getDiffingEntries(hashesBefore, hashesAfter);
        assertTrue("No Spring configurations were modified because the app uses component scanning.",
                setIntersection(modifiedFiles, knownConfigClasses).isEmpty());
    }

    @Test
    public void testInjectInto_ValidJar_AddedSpringComponent() throws IOException {
        // Arrange
        JarFiddler jar = JarFiddler.buffer(simpleSpringBootApp);
        Map<String, String> hashesBefore = hashAllJarContents(jar);

        // Act
        injector.injectInto(jar);

        // Assert
        Map<String, String> hashesAfter = hashAllJarContents(jar);
        Set<String> addedClasses = new HashSet<>(hashesAfter.keySet());
        addedClasses.removeAll(hashesBefore.keySet());
        assertFalse("Some class was added.", addedClasses.isEmpty());
    }

    @Test
    public void testInjectInto_AlreadyInfectedJar_Untouched() throws IOException {
        // Arrange
        JarFiddler jar = JarFiddler.buffer(simpleSpringBootApp);

        // Act
        boolean didInfectFirst = injector.injectInto(jar);
        boolean didInfectSecond = injector.injectInto(jar);

        // Assert
        assertTrue("Did infect the first time.", didInfectFirst);
        assertFalse("Did not infect the second time.", didInfectSecond);
    }

    @Test
    public void testAddBeanToSpringConfig_ExistingConfigWithBeans_AddedBean() {
        // Arrange
        ClassFile existingConfigClass = createSpringConfWithBean("io.github.w1th4d.jarplant.test.test.ExistingConfiguration", "ExistingBean");
        ClassFile injectConfigClass = createSpringConfWithBean("io.github.w1th4d.jarplant.test.implant.ImplantConfiguration", "ImplantBean");
        ClassFile injectComponentClass = new ClassFile(false, "io.github.w1th4d.jarplant.test.implant.ImplantBean", null);

        // Act
        Optional<ClassFile> modifiedSpringConfig = SpringInjector.addBeanToSpringConfig(existingConfigClass, injectConfigClass, injectComponentClass);

        // Assert
        assertTrue("Spring config is modified.", modifiedSpringConfig.isPresent());

        MethodInfo injectedBean = modifiedSpringConfig.get().getMethod("ImplantBean");
        assertNotNull("Config implant exists.", injectedBean);

        AttributeInfo annotationsAttr = injectedBean.getAttribute("RuntimeVisibleAnnotations");
        assertNotNull("Config implant has annotations.", annotationsAttr);
        assertTrue("Config implant has annotations.", annotationsAttr.length() > 0);
    }

    @Test
    public void testAddBeanToSpringConfig_ExistingConfigWithoutBeans_AddedBean() {
        // Arrange
        ClassFile existingConfigClass = new ClassFile(false, "io.github.w1th4d.jarplant.test.ExistingConfiguration", null);    // <---
        ClassFile injectConfigClass = createSpringConfWithBean("io.github.w1th4d.jarplant.test.implant.ImplantConfiguration", "ImplantBean");
        ClassFile injectComponentClass = new ClassFile(false, "io.github.w1th4d.jarplant.test.implant.ImplantBean", null);

        // Act
        Optional<ClassFile> modifiedSpringConfig = SpringInjector.addBeanToSpringConfig(existingConfigClass, injectConfigClass, injectComponentClass);

        // Assert
        assertTrue("Spring config is modified.", modifiedSpringConfig.isPresent());

        MethodInfo injectedBean = modifiedSpringConfig.get().getMethod("ImplantBean");
        assertNotNull("Config implant exists.", injectedBean);

        AttributeInfo annotationsAttr = injectedBean.getAttribute("RuntimeVisibleAnnotations");
        assertNotNull("Config implant has annotations.", annotationsAttr);
        assertTrue("Config implant has annotations.", annotationsAttr.length() > 0);

        AttributeInfo codeAttr = injectedBean.getCodeAttribute();
        assertNotNull("Config implant has a code attribute.", codeAttr);
    }

    @Test
    public void testAddBeanToSpringConfig_ImplantConfigWithoutBeans_Untouched() {
        // Arrange
        ClassFile existingConfigClass = createSpringConfWithBean("io.github.w1th4d.jarplant.test.test.ExistingConfiguration", "ExistingBean");
        ClassFile injectConfigClass = new ClassFile(false, "io.github.w1th4d.jarplant.test.implants.EmptyConfiguration", null);    // <---
        ClassFile injectComponentClass = new ClassFile(false, "io.github.w1th4d.jarplant.test.implant.ImplantBean", null);

        // Act
        Optional<ClassFile> modifiedSpringConfig = SpringInjector.addBeanToSpringConfig(existingConfigClass, injectConfigClass, injectComponentClass);

        // Assert
        assertTrue("Spring config is modified.", modifiedSpringConfig.isPresent());
        MethodInfo injectedBean = existingConfigClass.getMethod("ImplantBean");
        assertNull("There was no config to implant, so nothing was implanted into existing config.", injectedBean);
    }

    @Test
    public void testCopyAllMethodAnnotations_SeveralAnnotations_AllCopied() {
        // Arrange
        MethodInfo sourceMethod = new MethodInfo(new ConstPool("SourceClass"), "sourceMethod", "V()");
        AnnotationsAttribute sourceAnnotations = new AnnotationsAttribute(sourceMethod.getConstPool(), "RuntimeVisibleAnnotations");
        sourceAnnotations.addAnnotation(new Annotation("io.github.w1th4d.jarplant.test.SomeAnnotation", sourceMethod.getConstPool()));
        sourceAnnotations.addAnnotation(new Annotation("org.springframework.context.annotation.Bean", sourceMethod.getConstPool()));
        sourceAnnotations.addAnnotation(new Annotation("org.springframework.context.annotation.SomethingElse", sourceMethod.getConstPool()));
        AnnotationsAttribute sourceAnnotationsCopy = (AnnotationsAttribute) sourceAnnotations.copy(sourceMethod.getConstPool(), null);
        sourceMethod.addAttribute(sourceAnnotations);

        MethodInfo targetMethod = new MethodInfo(new ConstPool("TargetClass"), "targetMethod", "V()");

        // Act
        SpringInjector.copyAllMethodAnnotations(targetMethod, sourceMethod);

        // Assert
        AnnotationsAttribute sourceAnnotationAttr = (AnnotationsAttribute) sourceMethod.getAttribute("RuntimeVisibleAnnotations");
        AnnotationsAttribute targetAnnotationAttr = (AnnotationsAttribute) targetMethod.getAttribute("RuntimeVisibleAnnotations");
        assertNotNull("Target method now has an annotations attribute.", targetAnnotationAttr);
        assertArrayEquals("All annotations are copied.",
                sourceAnnotationAttr.getAnnotations(),
                targetAnnotationAttr.getAnnotations());
        assertArrayEquals("Source method still has its original annotations.",
                sourceAnnotationsCopy.getAnnotations(),
                ((AnnotationsAttribute) sourceMethod.getAttribute("RuntimeVisibleAnnotations")).getAnnotations()
        );
    }

    @Test
    public void testCopyAllMethodAnnotations_AlreadyExistingAnnotations_ExistingRemainsPlusCopied() {
        // Arrange
        MethodInfo sourceMethod = new MethodInfo(new ConstPool("SourceClass"), "sourceMethod", "V()");
        AnnotationsAttribute sourceAnnotations = new AnnotationsAttribute(sourceMethod.getConstPool(), "RuntimeVisibleAnnotations");
        sourceAnnotations.addAnnotation(new Annotation("io.github.w1th4d.jarplant.test.AlreadyExistingAnnotation", sourceMethod.getConstPool()));
        sourceAnnotations.addAnnotation(new Annotation("org.springframework.context.annotation.Bean", sourceMethod.getConstPool()));
        sourceAnnotations.addAnnotation(new Annotation("org.springframework.context.annotation.SomethingElse", sourceMethod.getConstPool()));
        sourceMethod.addAttribute(sourceAnnotations);

        MethodInfo targetMethod = new MethodInfo(new ConstPool("TargetClass"), "targetMethod", "V()");
        AnnotationsAttribute targetAnnotations = new AnnotationsAttribute(targetMethod.getConstPool(), "RuntimeVisibleAnnotations");
        targetAnnotations.addAnnotation(new Annotation("io.github.w1th4d.jarplant.test.AlreadyExistingAnnotation", targetMethod.getConstPool()));
        targetAnnotations.addAnnotation(new Annotation("org.springframework.context.annotation.Bean", targetMethod.getConstPool()));
        targetAnnotations.addAnnotation(new Annotation("io.github.w1th4d.jarplant.test.AnnotationOnlyInTarget", targetMethod.getConstPool()));
        targetMethod.addAttribute(targetAnnotations);

        Set<Annotation> expectedAnnotations = Set.of(
                new Annotation("io.github.w1th4d.jarplant.test.AlreadyExistingAnnotation", sourceMethod.getConstPool()),
                new Annotation("org.springframework.context.annotation.Bean", sourceMethod.getConstPool()),
                new Annotation("org.springframework.context.annotation.SomethingElse", sourceMethod.getConstPool()),
                new Annotation("io.github.w1th4d.jarplant.test.AnnotationOnlyInTarget", sourceMethod.getConstPool())
        );

        // Act
        SpringInjector.copyAllMethodAnnotations(targetMethod, sourceMethod);

        // Assert
        AnnotationsAttribute targetAnnotationAttr = (AnnotationsAttribute) targetMethod.getAttribute("RuntimeVisibleAnnotations");
        Set<Annotation> actualTargetAnnotations = Set.of(targetAnnotationAttr.getAnnotations());
        assertEquals("Target method still has no annotations.",
                expectedAnnotations,
                actualTargetAnnotations);
    }

    @Test
    public void testCopyAllMethodAnnotations_NoSourceAnnotations_Untouched() {
        // Arrange
        MethodInfo sourceMethod = new MethodInfo(new ConstPool("SourceClass"), "sourceMethod", "V()");
        MethodInfo targetMethod = new MethodInfo(new ConstPool("TargetClass"), "targetMethod", "V()");
        MethodInfo targetMethodClone = new MethodInfo(new ConstPool("TargetClass"), "targetMethod", "V()");

        // Act
        SpringInjector.copyAllMethodAnnotations(targetMethod, sourceMethod);

        // Assert
        assertEquals("Target method still has no annotations.",
                targetMethodClone.getAttribute("RuntimeVisibleAnnotations"),
                targetMethod.getAttribute("RuntimeVisibleAnnotations"));
        assertNull("Source method still has no annotations.",
                sourceMethod.getAttribute("RuntimeVisibleAnnotations"));
    }
}
