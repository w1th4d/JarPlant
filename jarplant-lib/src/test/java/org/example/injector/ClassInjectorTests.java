package org.example.injector;

import javassist.bytecode.*;
import org.example.TestImplantRunner;
import org.example.implants.TestClassImplant;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.jar.JarOutputStream;

import static org.example.TestHelpers.*;
import static org.junit.Assert.*;

public class ClassInjectorTests {
    // These are compiled classes of the TestClassImplant that were packaged in a proper JAR by the test-class-implant module
    private ClassFile testImplant;
    private ClassFile testImplantWithDebug;
    private String testImplantSourceFileName;

    // These are target JARs coming from the target-app module
    private Path targetAppJarWithDebuggingInfo;
    private Path targetAppJarWithoutDebuggingInfo;

    // These can be used for test that just needs an empty JAR to work with
    private Path tempInputFile;
    private Path tempOutputFile;

    @Before
    public void getTestImplants() throws IOException {
        Path jarPath = getJarFileFromResourceFolder("test-class-implant-without-debug.jar");
        InputStream rawClass = getRawClassStreamFromJar(jarPath, "org/example/implants/TestClassImplant.class");
        ClassFile testImplant = new ClassFile(new DataInputStream(rawClass));

        Path jarPathDbg = getJarFileFromResourceFolder("test-class-implant-with-debug.jar");
        InputStream rawClassDbg = getRawClassStreamFromJar(jarPathDbg, "org/example/implants/TestClassImplant.class");
        ClassFile testImplantDbg = new ClassFile(new DataInputStream(rawClassDbg));

        // There's only file names encoded into the class with debugging info
        List<String> originalNames = testImplantDbg.getAttributes().stream()
                .filter(attr -> attr instanceof SourceFileAttribute)
                .map(attr -> (SourceFileAttribute) attr)
                .map(SourceFileAttribute::getFileName)
                .toList();
        HashSet<String> distinctOriginalNames = new HashSet<>(originalNames);

        // Pre-assert test-app
        assertFalse("test-app has SourceFileAttributes", originalNames.isEmpty());
        assertEquals("All SourceFileAttributes in test-app are the same", 1, distinctOriginalNames.size());
        String originalFileName = distinctOriginalNames.stream()
                .findAny()
                .orElseThrow()
                .replace(".java", "");

        this.testImplant = testImplant;
        this.testImplantWithDebug = testImplantDbg;
        this.testImplantSourceFileName = originalFileName;
    }

    @Before
    public void setTargetAppJarWithDebuggingInfo() throws IOException {
        this.targetAppJarWithDebuggingInfo = getJarFileFromResourceFolder("target-app-with-debug.jar");
    }


    @Before
    public void setTargetAppJarWithoutDebuggingInfo() throws IOException {
        this.targetAppJarWithoutDebuggingInfo = getJarFileFromResourceFolder("target-app-without-debug.jar");
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

    // modifyClinit

    @Test
    public void testModifyClinit_ExistingClinit_ModifiedClinit() {
        // Arrange
        ClassFile testClass = new ClassFile(false, "TestClass", null);
        MethodInfo clinit = new MethodInfo(testClass.getConstPool(), MethodInfo.nameClinit, "()V");
        Bytecode clinitCode = new Bytecode(clinit.getConstPool());
        clinitCode.addOpcode(Opcode.NOP);
        clinitCode.addOpcode(Opcode.NOP);
        clinitCode.addOpcode(Opcode.NOP);
        clinitCode.addOpcode(Opcode.RETURN);
        clinit.setCodeAttribute(clinitCode.toCodeAttribute());
        try {
            testClass.addMethod(clinit);
        } catch (DuplicateMemberException e) {
            throw new RuntimeException(e);
        }

        // Act
        ClassInjector.modifyClinit(testClass, testImplant);

        // Assert
        MethodInfo actual = testClass.getMethod(MethodInfo.nameClinit);
        assertNotNull("Class initializer method exists.", actual);

        byte[] actualBytecode = actual.getCodeAttribute().getCode();
        byte[] expectedPreservedBytecode = clinitCode.get();
        Optional<Integer> originalBytecodeIndex = findSubArray(actualBytecode, expectedPreservedBytecode);
        assertTrue("The original bytecode still exist somewhere in the modified <clinit>.", originalBytecodeIndex.isPresent());

        int amountOfBytecodeLeft = actualBytecode.length - expectedPreservedBytecode.length;
        assertTrue("There's added bytecode in the modified <clinit>.", amountOfBytecodeLeft > 0);
    }

    @Test
    public void testModifyClinit_NoClinit_AddedClinit() {
        // Arrange
        ClassFile testClass = new ClassFile(false, "TestClass", null);

        // Act
        ClassInjector.modifyClinit(testClass, testImplant);

        // Assert
        MethodInfo actual = testClass.getMethod(MethodInfo.nameClinit);
        assertNotNull("Class initializer method exists.", actual);
    }

    /**
     * Test config override consistency.
     * This one is a bit special. It tests that the config values are the same both at time of init() and later.
     * This relates to how and where the config override bytecode is inserted into the implant class initializer.
     */
    @Test
    @Ignore // TODO Fix the failing test
    public void testConfigOverride_DifferentTimeOfRead_SameValues() throws IOException, ClassNotFoundException, ImplantConfigException {
        ImplantHandler implant = ImplantHandlerImpl.findAndCreateFor(TestClassImplant.class);
        implant.setConfig("CONF_STRING", "Modified");
        implant.setConfig("CONF_BOOLEAN", true);
        implant.setConfig("CONF_INT", 2);
        ClassInjector injector = new ClassInjector(implant);
        Path tempJarFile = createTempJarFileWithClasses(
                findTestEnvironmentDir(ClassInjectorTests.class),
                Path.of("org/example/implants/TestImplant.class")
        );
        TestImplantRunner runner = TestImplantRunner.getInstance();

        injector.infect(tempJarFile, tempOutputFile);
        runner.loadAllClassesFromJar(tempOutputFile);

        // TODO Why is this failing and where the hell does it go wrong!? During inject?
        String actualAtInit = runner.runMethod("org.example.implants.TestImplant", "init", String.class);
        String actualPostInit = runner.runMethod("org.example.implants.TestImplant", "getConfigDump", String.class);

        String expected = "CONF_STRING=\"Modified\";CONF_BOOLEAN=true;CONF_INT=2;";
        assertEquals("Modified values at time if init().", expected, actualAtInit);
        assertEquals("Modified values after init().", expected, actualPostInit);
    }

    // deepRenameClass

    @Test
    public void testDeepRenameClass_ValidClass_Renamed() throws IOException {
        // Assemble
        List<String> originalNames = testImplantWithDebug.getAttributes().stream()
                .filter(attr -> attr instanceof SourceFileAttribute)
                .map(attr -> (SourceFileAttribute) attr)
                .map(SourceFileAttribute::getFileName)
                .toList();

        // Act
        ClassInjector.deepRenameClass(testImplantWithDebug, "local.target", "NewName");
        List<String> changedNames = testImplantWithDebug.getAttributes().stream()
                .filter(attr -> attr instanceof SourceFileAttribute)
                .map(attr -> (SourceFileAttribute) attr)
                .map(SourceFileAttribute::getFileName)
                .toList();

        // Assert
        assertEquals("No SourceFileAttributes were added or lost", originalNames.size(), changedNames.size());
        for (String changedName : changedNames) {
            assertEquals("SourceFileAttribute is renamed", "NewName.java", changedName);
        }
    }

    @Test
    public void testDeepRenameClass_SameName_Unmodified() throws IOException {
        // Assemble
        String originalFqcn = Helpers.parsePackageNameFromFqcn(testImplantWithDebug.getName());
        byte[] classDataBefore = asBytes(testImplantWithDebug);

        // Act
        ClassInjector.deepRenameClass(testImplantWithDebug, originalFqcn, testImplantSourceFileName);

        // Assert
        byte[] classDataAfter = asBytes(testImplantWithDebug);
        assertArrayEquals("Class is not changed", classDataBefore, classDataAfter);
    }

    private static byte[] asBytes(ClassFile classFile) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        classFile.write(new DataOutputStream(buffer));
        return buffer.toByteArray();
    }

    @Test
    @Ignore
    public void testDeepRenameClass_NoDebuggingInfo_Unmodified() throws IOException {
        // Assemble
        String originalFqcn = Helpers.parsePackageNameFromFqcn(testImplant.getName());
        byte[] classDataBefore = asBytes(testImplant);

        // Act
        ClassInjector.deepRenameClass(testImplant, originalFqcn, testImplantSourceFileName);

        // Assert
        byte[] classDataAfter = asBytes(testImplant);
        assertArrayEquals("Class is not changed", classDataBefore, classDataAfter);
    }

    // infect

    @Test
    public void testInfect_NormalJar_SomeClassModified() throws IOException, ClassNotFoundException {
        // Assemble
        ImplantHandler handler = ImplantHandlerImpl.findAndCreateFor(TestClassImplant.class);
        Map<String, String> hashesBeforeInfect = hashAllJarContents(targetAppJarWithDebuggingInfo);

        // Act
        ClassInjector injector = new ClassInjector(handler);
        boolean didInfect = injector.infect(targetAppJarWithDebuggingInfo, tempOutputFile);

        // Assert
        assertTrue("Did successfully inject.", didInfect);
        Map<String, String> hashesAfterInfect = hashAllJarContents(tempOutputFile);
        assertNotEquals("At least one class file in JAR has changed.", hashesAfterInfect, hashesBeforeInfect);
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
    public void testInfect_SpringJar_ClassesModifiedAsUsual() {
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
    // Corresponds to the standard debugging info produce by javac (lines + source)
    public void testInfect_TargetHasStandardDebuggingInfo_Success() throws IOException {
        // Assemble
        ImplantHandler handler = new ImplantHandlerMock(testImplant);

        // Act
        ClassInjector injector = new ClassInjector(handler);
        boolean didInfect = injector.infect(targetAppJarWithDebuggingInfo, tempOutputFile);

        // Assert
        assertTrue("Did successfully inject.", didInfect);
        boolean didFindInitClass = false;
        try (JarFileFiddler searchTheOutputJar = JarFileFiddler.open(tempOutputFile)) {
            for (JarFileFiddler.WrappedJarEntry entry : searchTheOutputJar) {
                if (entry.getName().endsWith("Init.class")) {
                    didFindInitClass = true;
                }
            }
        }
        assertTrue("Did find injected Init class in output JAR.", didFindInitClass);
    }

    @Test
    public void testInfect_ImplantStandardDebuggingInfo_ObscuredImplantName() throws IOException {
        // Assemble
        ImplantHandler handler = new ImplantHandlerMock(testImplantWithDebug);

        // Act
        ClassInjector injector = new ClassInjector(handler);
        boolean didInfect = injector.infect(targetAppJarWithoutDebuggingInfo, tempOutputFile);

        // Assert
        assertTrue("Did successfully inject.", didInfect);

        InputStream initEntryContent = null;
        JarFileFiddler searchTheOutputJar = JarFileFiddler.open(tempOutputFile);
        for (JarFileFiddler.WrappedJarEntry entry : searchTheOutputJar) {
            if (entry.getName().endsWith("Init.class")) {
                initEntryContent = entry.getContent();
            }
        }
        if (initEntryContent == null) {
            fail("Failed to find Init class in infected JAR.");
        }
        ClassFile classFile = new ClassFile(new DataInputStream(initEntryContent));
        boolean didFindOriginalName = classFile.getAttributes().stream()
                .filter(attr -> attr instanceof SourceFileAttribute)
                .map(attr -> (SourceFileAttribute) attr)
                .map(SourceFileAttribute::getFileName)
                .anyMatch(name -> name.contains("TestClassImplant"));
        assertFalse("Injected implant does not contain a SourceFileAttribute with the original name.", didFindOriginalName);
    }

    @Test
    // Corresponds to javac -g:none
    public void testInfect_TargetHasNoDebuggingInfo_Success() throws IOException, ClassNotFoundException {
        // Assemble
        ImplantHandler handler = new ImplantHandlerMock(testImplant);

        // Act
        ClassInjector injector = new ClassInjector(handler);
        boolean didInfect = injector.infect(targetAppJarWithoutDebuggingInfo, tempOutputFile);
        boolean didFindInitClass = false;
        try (JarFileFiddler searchTheOutputJar = JarFileFiddler.open(tempOutputFile)) {
            for (JarFileFiddler.WrappedJarEntry entry : searchTheOutputJar) {
                if (entry.getName().endsWith("Init.class")) {
                    didFindInitClass = true;
                }
            }
        }

        // Assert
        assertTrue("Did successfully inject.", didInfect);
        assertTrue("Did find injected Init class in output JAR.", didFindInitClass);
    }

    @Test
    @Ignore
    public void testInfect_AlreadyInfectedJar_Untouched() {
        // TODO Implement infection detection
    }
}
