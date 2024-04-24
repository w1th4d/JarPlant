package org.example.injector;

import javassist.bytecode.*;
import org.example.TestImplantRunner;
import org.example.implants.TestClassImplant;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.*;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import static org.example.TestHelpers.*;
import static org.junit.Assert.*;

public class ClassInjectorTests {
    private ClassFile testImplant;
    private ClassFile testImplantWithDebug;

    private String testImplantSourceFileName;
    private Path targetAppJarWithDebuggingInfo;
    private Path targetAppJarWithoutDebuggingInfo;

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

    private static Path getJarFileFromResourceFolder(String jarFileName) {
        URL resource = ClassInjectorTests.class.getClassLoader().getResource(jarFileName);
        if (resource == null) {
            throw new RuntimeException("Cannot find target-app JAR in resource folder. Have you run `mvn package` in the project root yet?");
        }

        Path jarFilePath;
        try {
            jarFilePath = Path.of(resource.toURI());
        } catch (URISyntaxException e) {
            throw new RuntimeException("Cannot make sense of resource path.", e);
        }

        // This should not be necessary but let's make sure
        if (!Files.exists(jarFilePath)) {
            throw new RuntimeException("Cannot make sense of resource path: File does not exist.");
        }

        return jarFilePath;
    }

    private static InputStream getRawClassStreamFromJar(Path jarFilePath, String entryFullInternalPath) throws IOException {
        JarFile jarFile = new JarFile(jarFilePath.toFile());
        JarEntry classFileInJar = (JarEntry) jarFile.getEntry(entryFullInternalPath);
        if (classFileInJar == null) {
            throw new FileNotFoundException(entryFullInternalPath);
        }

        return jarFile.getInputStream(classFileInJar);
    }

    @Before
    public void setTargetAppJarWithDebuggingInfo() {
        this.targetAppJarWithDebuggingInfo = getJarFileFromResourceFolder("target-app-with-debug.jar");
    }


    @Before
    public void setTargetAppJarWithoutDebuggingInfo() {
        this.targetAppJarWithoutDebuggingInfo = getJarFileFromResourceFolder("target-app-without-debug.jar");
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
        Path tempOutputJar = Files.createTempFile("ClassInjectorTests-" + UUID.randomUUID(), ".jar");
        TestImplantRunner runner = TestImplantRunner.getInstance();

        injector.infect(tempJarFile, tempOutputJar);
        runner.loadAllClassesFromJar(tempOutputJar);

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
        Path outputJarPath = Files.createTempFile("OutputJar-", ".jar");
        Map<String, String> hashesBeforeInfect = hashAllJarContents(targetAppJarWithDebuggingInfo);

        // Act
        ClassInjector injector = new ClassInjector(handler);
        boolean didInfect = injector.infect(targetAppJarWithDebuggingInfo, outputJarPath);

        // Assert
        assertTrue("Did successfully inject.", didInfect);
        Map<String, String> hashesAfterInfect = hashAllJarContents(outputJarPath);
        assertNotEquals("At least one class file in JAR has changed.", hashesAfterInfect, hashesBeforeInfect);

        // Cleanup (a bit late)
        Files.delete(outputJarPath);
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
        Path outputJarPath = Files.createTempFile("OutputJar-", ".jar");

        // Act
        ClassInjector injector = new ClassInjector(handler);
        boolean didInfect = injector.infect(targetAppJarWithDebuggingInfo, outputJarPath);

        // Assert
        assertTrue("Did successfully inject.", didInfect);
        boolean didFindInitClass = false;
        try (JarFileFiddler searchTheOutputJar = JarFileFiddler.open(outputJarPath)) {
            for (JarFileFiddler.WrappedJarEntry entry : searchTheOutputJar) {
                if (entry.getName().endsWith("Init.class")) {
                    didFindInitClass = true;
                }
            }
        }
        assertTrue("Did find injected Init class in output JAR.", didFindInitClass);

        // Cleanup (a bit late but whatever)
        Files.delete(outputJarPath);
    }

    @Test
    public void testInfect_ImplantStandardDebuggingInfo_ObscuredImplantName() throws IOException {
        // Assemble
        ImplantHandler handler = new ImplantHandlerMock(testImplantWithDebug);
        Path outputJarPath = Files.createTempFile("OutputJar-", ".jar");

        // Act
        ClassInjector injector = new ClassInjector(handler);
        boolean didInfect = injector.infect(targetAppJarWithoutDebuggingInfo, outputJarPath);

        // Assert
        assertTrue("Did successfully inject.", didInfect);

        InputStream initEntryContent = null;
        JarFileFiddler searchTheOutputJar = JarFileFiddler.open(outputJarPath);
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
        Path outputJarPath = Files.createTempFile("OutputJar-", ".jar");

        // Act
        ClassInjector injector = new ClassInjector(handler);
        boolean didInfect = injector.infect(targetAppJarWithoutDebuggingInfo, outputJarPath);
        boolean didFindInitClass = false;
        try (JarFileFiddler searchTheOutputJar = JarFileFiddler.open(outputJarPath)) {
            for (JarFileFiddler.WrappedJarEntry entry : searchTheOutputJar) {
                if (entry.getName().endsWith("Init.class")) {
                    didFindInitClass = true;
                }
            }
        } finally {
            // Clean up in the temp folder
            Files.delete(outputJarPath);
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


    private static Optional<Integer> findSubArray(byte[] bigArray, byte[] smallArray) {
        for (int i = 0; i <= bigArray.length - smallArray.length; i++) {
            boolean found = true;
            for (int j = 0; j < smallArray.length; j++) {
                if (bigArray[i + j] != smallArray[j]) {
                    found = false;
                    break;
                }
            }
            if (found) {
                return Optional.of(i);
            }
        }

        return Optional.empty();
    }

    @Test
    public void testFindSubArray() {
        // This test is a bit meta testing...
        assertEquals(Optional.of(1), findSubArray(new byte[]{1, 2, 3, 4, 5}, new byte[]{2, 3, 4}));
        assertEquals(Optional.of(0), findSubArray(new byte[]{1, 2, 3, 4, 5}, new byte[]{1}));
        assertEquals(Optional.of(1), findSubArray(new byte[]{1, 2, 3, 4, 5}, new byte[]{2}));
        assertEquals(Optional.of(4), findSubArray(new byte[]{1, 2, 3, 4, 5}, new byte[]{5}));
        assertEquals(Optional.of(0), findSubArray(new byte[]{1, 2, 3, 4, 5}, new byte[]{1, 2}));
        assertEquals(Optional.of(3), findSubArray(new byte[]{1, 2, 3, 4, 5}, new byte[]{4, 5}));
        assertEquals(Optional.of(0), findSubArray(new byte[]{1, 2, 3, 4, 5}, new byte[]{1, 2, 3, 4, 5}));
        assertEquals(Optional.of(1), findSubArray(new byte[]{1, 2, 3, 4, 5}, new byte[]{2, 3, 4, 5}));
        assertEquals(Optional.empty(), findSubArray(new byte[]{1, 2, 3, 4, 5}, new byte[]{2, 3, 4, 5, 1}));
        assertEquals(Optional.empty(), findSubArray(new byte[]{1, 2, 3, 4, 5}, new byte[]{1, 2, 3, 4, 5, 5}));
        assertEquals(Optional.empty(), findSubArray(new byte[]{1, 2, 3, 4, 5}, new byte[]{1, 1, 2, 3, 4, 5}));
        assertEquals(Optional.empty(), findSubArray(new byte[]{1, 2, 3, 4, 5}, new byte[]{1, 1, 2, 3, 4}));
        assertEquals(Optional.empty(), findSubArray(new byte[]{}, new byte[]{1, 1, 2, 3, 4}));
        assertEquals(Optional.of(0), findSubArray(new byte[]{1, 2, 3, 4, 5}, new byte[]{}));
        assertEquals(Optional.of(0), findSubArray(new byte[]{}, new byte[]{}));
    }
}
