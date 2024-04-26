package org.example.injector;

import org.example.implants.TestSpringBeanImplant;
import org.example.implants.TestSpringConfigImplant;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.example.TestHelpers.getJarFileFromResourceFolder;
import static org.example.TestHelpers.hashAllJarContents;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class SpringInjectorTests {
    // Test implant originating from the test-spring-implant module
    private ImplantHandler testConfigImplantHandler;
    private ImplantHandler testBeanImplantHandler;

    // Path to an actual JAR packaged by the target-app-spring-boot module
    private Path targetSpringBootApp;

    // Temporary files to work with
    private Path tempInputFile;
    private Path tempOutputFile;

    @Before
    public void loadTestImplants() throws IOException, ClassNotFoundException {
        this.testConfigImplantHandler = ImplantHandlerMock.findAndCreateFor(TestSpringConfigImplant.class);
        this.testBeanImplantHandler = ImplantHandlerMock.findAndCreateFor(TestSpringBeanImplant.class);
    }

    @Before
    public void loadTargetApp() throws IOException {
        this.targetSpringBootApp = getJarFileFromResourceFolder("target-app-spring-boot.jar");
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
    @Ignore
    public void testInfect_SpringJar_Success() {
    }

    @Test
    @Ignore
    public void testInfect_SpringBootJar_Success() {
    }

    @Test
    @Ignore
    public void testInfect_NotAJar_Untouched() {
    }

    @Test
    @Ignore
    public void testInfect_EmptyJar_Untouched() {
    }

    @Test
    @Ignore
    public void testInfect_JarWithoutClasses_Untouched() {
    }

    @Test
    @Ignore
    public void testInfect_JarWithoutManifest_Success() {
        // Success or fail could be debatable
    }

    @Test
    @Ignore
    public void testInfect_VersionedJar_AllVersionsModified() {
    }

    @Test
    @Ignore
    public void testInfect_SignedClasses_SignedClassesUntouched() {
    }

    @Test
    @Ignore
    public void testInfect_NoSpringConfig_Untouched() {
    }

    @Test
    @Ignore
    public void testInfect_SeveralSpringConfigs_AllInfected() {
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
