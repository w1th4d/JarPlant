package org.example.injector;

import javassist.bytecode.*;
import org.example.TestHelpers;
import org.example.TestImplantRunner;
import org.example.implants.TestClassImplant;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.DataInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.jar.*;
import java.util.zip.ZipFile;

import static org.example.TestHelpers.*;
import static org.example.injector.Helpers.readClassFile;
import static org.junit.Assert.*;

public class ClassInjectorTests {
    // These are compiled classes of the TestClassImplant that were packaged in a proper JAR by the test-implant-class module
    private ClassFile testImplant;
    private ClassFile testImplantWithDebug;
    private String testImplantSourceFileName;

    // These are target JARs coming from the test-app-pojo module
    private Path targetAppJarWithDebuggingInfo;
    private Path targetAppJarWithoutDebuggingInfo;

    // Also use the Spring app for some tests
    private Path targetSpringBootApp;

    // These can be used for test that just needs an empty JAR to work with
    private Path tempInputFile;
    private Path tempOutputFile;

    @Before
    public void configureLogger() {
        TestHelpers.configureLogger();
    }

    @Before
    public void getTestImplants() throws IOException {
        Path jarPath = getJarFileFromResourceFolder("test-implant-class-without-debug.jar");
        InputStream rawClass = getRawClassStreamFromJar(jarPath, "org/example/implants/TestClassImplant.class");
        ClassFile testImplant = new ClassFile(new DataInputStream(rawClass));

        Path jarPathDbg = getJarFileFromResourceFolder("test-implant-class-with-debug.jar");
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
        this.targetAppJarWithDebuggingInfo = getJarFileFromResourceFolder("test-app-pojo-with-debug.jar");
    }


    @Before
    public void setTargetAppJarWithoutDebuggingInfo() throws IOException {
        this.targetAppJarWithoutDebuggingInfo = getJarFileFromResourceFolder("test-app-pojo-without-debug.jar");
    }

    @Before
    public void getTargetSpringBootApp() throws IOException {
        this.targetSpringBootApp = getJarFileFromResourceFolder("test-app-spring-simple.jar");
    }

    @Before
    public void createMiscTestJars() throws IOException {
        tempInputFile = Files.createTempFile("JarPlantTests-", ".jar");
        tempOutputFile = Path.of(tempInputFile.toAbsolutePath() + "-output.jar");
    }

    @After
    public void removeTempInputFile() throws IOException {
        Files.delete(tempInputFile);
    }

    @After
    public void removeTempOutputFile() throws IOException {
        if (Files.exists(tempOutputFile)) {
            Files.delete(tempOutputFile);
        }
    }

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
        Optional<ClassFile> modified = ClassInjector.modifyClinit(testClass, testImplant);

        // Assert
        assertTrue("Did modify <clinit>", modified.isPresent());
        MethodInfo actual = modified.get().getMethod(MethodInfo.nameClinit);
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
        Optional<ClassFile> modified = ClassInjector.modifyClinit(testClass, testImplant);

        // Assert
        assertTrue("Did modify <clinit>", modified.isPresent());
        MethodInfo actual = modified.get().getMethod(MethodInfo.nameClinit);
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
        populateJarEntriesIntoEmptyFile(
                tempInputFile,
                findTestEnvironmentDir(ClassInjectorTests.class),
                Path.of("org/example/implants/DummyTestClassImplant.class")
        );
        TestImplantRunner runner = new TestImplantRunner();

        injector.infect(tempInputFile, tempOutputFile);
        runner.loadAllClassesFromJar(tempOutputFile);

        // TODO Why is this failing and where the hell does it go wrong!? During inject?
        String actualAtInit = runner.runMethod("org.example.implants.DummyTestClassImplant", "init", String.class);
        String actualPostInit = runner.runMethod("org.example.implants.DummyTestClassImplant", "getConfigDump", String.class);

        String expected = "CONF_STRING=\"Modified\";CONF_BOOLEAN=true;CONF_INT=2;";
        assertEquals("Modified values at time if init().", expected, actualAtInit);
        assertEquals("Modified values after init().", expected, actualPostInit);
    }


    @Test
    public void testDeepRenameClass_Any_OriginalClassUnchanged() throws IOException {
        // Arrange
        String originalPackageName = ClassName.of(testImplantWithDebug).getPackageName();
        byte[] classDataBefore = asBytes(testImplantWithDebug);

        // Act
        ClassFile renamedClass = ClassInjector.deepRenameClass(testImplantWithDebug, originalPackageName, "Changed");

        // Assert
        byte[] classDataAfter = asBytes(testImplantWithDebug);
        assertArrayEquals("Class is not changed.", classDataBefore, classDataAfter);

        byte[] renamedClassDataAfter = asBytes(renamedClass);
        assertFalse("Renamed class is different from original class.", Arrays.equals(classDataBefore, renamedClassDataAfter));
    }

    @Test
    public void testDeepRenameClass_ValidClass_Renamed() {
        // Arrange
        List<String> originalNames = testImplantWithDebug.getAttributes().stream()
                .filter(attr -> attr instanceof SourceFileAttribute)
                .map(attr -> (SourceFileAttribute) attr)
                .map(SourceFileAttribute::getFileName)
                .toList();

        // Act
        ClassFile renamedClass = ClassInjector.deepRenameClass(testImplantWithDebug, "local.target", "NewName");
        List<String> changedNames = renamedClass.getAttributes().stream()
                .filter(attr -> attr instanceof SourceFileAttribute)
                .map(attr -> (SourceFileAttribute) attr)
                .map(SourceFileAttribute::getFileName)
                .toList();

        // Assert
        assertEquals("No SourceFileAttributes were added or lost.", originalNames.size(), changedNames.size());
        for (String changedName : changedNames) {
            assertEquals("SourceFileAttribute is renamed.", "NewName.java", changedName);
        }
    }

    @Test
    public void testDeepRenameClass_SameName_Unmodified() throws IOException {
        // Arrange
        String originalPackageName = ClassName.of(testImplantWithDebug).getPackageName();
        byte[] classDataBefore = asBytes(testImplantWithDebug);

        // Act
        ClassInjector.deepRenameClass(testImplantWithDebug, originalPackageName, testImplantSourceFileName);

        // Assert
        byte[] classDataAfter = asBytes(testImplantWithDebug);
        assertArrayEquals("Class is not changed.", classDataBefore, classDataAfter);
    }

    @Test
    public void testDeepRenameClass_NoDebuggingInfo_Unmodified() throws IOException {
        // Arrange
        String originalPackageName = ClassName.of(testImplant).getPackageName();
        byte[] classDataBefore = asBytes(testImplant);

        // Act
        ClassInjector.deepRenameClass(testImplant, originalPackageName, testImplantSourceFileName);

        // Assert
        byte[] classDataAfter = asBytes(testImplant);
        assertArrayEquals("Class is not changed.", classDataBefore, classDataAfter);
    }

    @Test
    public void testDeepRenameClass_NoPackageName_Fine() {
        // Arrange
        ClassFile emptyClass = new ClassFile(false, "Original", null);

        // Act
        ClassFile renamedClass = ClassInjector.deepRenameClass(emptyClass, "", "Changed");

        // Assert
        assertEquals("Class name is changed.", "Changed", ClassName.of(renamedClass).getFullClassName());
    }

    @Test
    public void testInfect_NormalJar_SomeClassModified() throws IOException, ClassNotFoundException {
        // Arrange
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

    @Test(expected = Exception.class)
    public void testInfect_NotAJar_Exception() throws IOException {
        // Arrange
        Random rng = new Random(1);
        byte[] someRandomData = new byte[10];
        rng.nextBytes(someRandomData);
        Files.write(tempInputFile, someRandomData, StandardOpenOption.WRITE);

        // Act + Assert
        ImplantHandler handler = new ImplantHandlerMock(testImplant);
        ClassInjector injector = new ClassInjector(handler);
        injector.infect(tempInputFile, tempOutputFile); // Should fail
    }

    @Test
    public void testInfect_EmptyJar_Untouched() throws IOException {
        // Arrange
        JarOutputStream createJar = new JarOutputStream(new FileOutputStream(tempInputFile.toFile()));
        createJar.close();  // The point is to just leave the JAR empty

        // Act
        ImplantHandler handler = new ImplantHandlerMock(testImplant);
        ClassInjector injector = new ClassInjector(handler);
        boolean didInfect = injector.infect(tempInputFile, tempOutputFile);

        // Assert
        assertFalse("Did not infect anything in an empty JAR.", didInfect);
    }

    @Test
    public void testInfect_EmptyJarWithManifest_Untouched() throws IOException {
        // Arrange
        populateJarEntriesIntoEmptyFile(tempInputFile, null);

        // Act
        ImplantHandler handler = new ImplantHandlerMock(testImplant);
        ClassInjector injector = new ClassInjector(handler);
        boolean didInfect = injector.infect(tempInputFile, tempOutputFile);

        // Assert
        assertFalse("Did not infect anything in an empty JAR.", didInfect);
    }

    /*
     * It's debatable if this is a good behaviour or not. What distinguishes a JAR from any regular ZIP file is the
     * fact that there's a manifest (META-INF/MANIFEST.MF) and a certain structure to the archive.
     * As of current behaviour, JarPlant would happily infect any random ZIP file that just so happens to contain
     * a .class file.
     * Future versions may do more stringent validations of the target JAR before infection.
     */
    @Test
    public void testInfect_JarWithoutManifest_Success() throws IOException {
        // Arrange
        JarOutputStream jarWriter = new JarOutputStream(new FileOutputStream(tempInputFile.toFile()));
        jarWriter.putNextEntry(new JarEntry("org/example/Something.class"));
        ClassFile emptyClass = new ClassFile(false, "Something.class", null);
        jarWriter.write(asBytes(emptyClass));
        jarWriter.close();

        // Act
        ImplantHandler handler = new ImplantHandlerMock(testImplant);
        ClassInjector injector = new ClassInjector(handler);
        boolean didInfect = injector.infect(tempInputFile, tempOutputFile);

        // Assert
        assertTrue("Infected JAR without a manifest.", didInfect);
    }

    @Test
    public void testInfect_SpringJar_ClassesModifiedAsUsual() throws IOException {
        // Arrange
        Map<String, String> hashesBeforeInfect = hashAllJarContents(targetSpringBootApp);

        // Act
        ImplantHandler handler = new ImplantHandlerMock(testImplant);
        ClassInjector injector = new ClassInjector(handler);
        boolean didInfect = injector.infect(targetSpringBootApp, tempOutputFile);

        // Assert
        assertTrue("Did successfully inject.", didInfect);
        Map<String, String> hashesAfterInfect = hashAllJarContents(tempOutputFile);
        assertNotEquals("At least one class file in JAR has changed.", hashesAfterInfect, hashesBeforeInfect);
    }

    /*
     * This synthetically creates a multi-release JAR and makes sure that all versions are infected.
     * Consider re-writing this test to use an actual multi-release JAR from Maven.
     */
    @Test
    public void testInfect_VersionedJar_AllVersionsModified() throws IOException {
        // Arrange
        JarOutputStream jarWriter = new JarOutputStream(new FileOutputStream(tempInputFile.toFile()));
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        jarWriter.putNextEntry(new JarEntry("META-INF/MANIFEST.MF"));

        // Standard version
        JarEntry javaClassEntry = new JarEntry("org/example/Main.class");
        ClassFile javaClass = new ClassFile(false, "Main.class", null);
        jarWriter.putNextEntry(javaClassEntry);
        jarWriter.write(asBytes(javaClass));

        // Some other version (totally not actually another version but it does not matter for this test)
        JarEntry java11ClassEntry = new JarEntry("META-INF/versions/11/org/example/Main.class");
        ClassFile java11Class = new ClassFile(false, "Main.class", null);
        jarWriter.putNextEntry(java11ClassEntry);
        jarWriter.write(asBytes(java11Class));

        jarWriter.close();

        // Act
        ImplantHandler handler = new ImplantHandlerMock(testImplant);
        ClassInjector injector = new ClassInjector(handler);
        injector.infect(tempInputFile, tempOutputFile);

        // Assert
        long originalDefClassCrc = new JarFile(tempInputFile.toFile(), false, ZipFile.OPEN_READ)
                .getEntry("org/example/Main.class")
                .getCrc();
        long modifiedDefClassCrc = new JarFile(tempOutputFile.toFile(), false, ZipFile.OPEN_READ)
                .getEntry("org/example/Main.class")
                .getCrc();
        long original11ClassCrc = new JarFile(tempInputFile.toFile(), false, ZipFile.OPEN_READ, Runtime.Version.parse("11"))
                .getEntry("org/example/Main.class")
                .getCrc();
        long modified11ClassCrc = new JarFile(tempOutputFile.toFile(), false, ZipFile.OPEN_READ, Runtime.Version.parse("11"))
                .getEntry("org/example/Main.class")
                .getCrc();
        assertNotEquals("File in the default version namespace is modified.", originalDefClassCrc, modifiedDefClassCrc);
        assertNotEquals("File in the Java11 version namespace is modified.", original11ClassCrc, modified11ClassCrc);
    }

    @Test
    public void testInfect_SignedJar_Untouched() throws IOException {
        // Arrange
        // This is a very rudimentary representation of a signed JAR. Consider generating a legit one somehow. Maven?
        String manifest = "Manifest-Version: 1.0\r\n\r\nName: org/example/Main.class\r\nSHA-256-Digest: "
                + Base64.getEncoder().encodeToString("somethingsomething".getBytes())
                + "\r\n";
        String signatureFile = "Signature-Version: 1.0\r\nSHA-256-Digest-Manifest: "
                + Base64.getEncoder().encodeToString("somethingsomething".getBytes())
                + "\r\n";
        JarOutputStream jarWriter = new JarOutputStream(new FileOutputStream(tempInputFile.toFile()));
        jarWriter.putNextEntry(new JarEntry("META-INF/MANIFEST.MF"));
        jarWriter.write(manifest.getBytes(StandardCharsets.UTF_8));
        jarWriter.putNextEntry(new JarEntry("META-INF/SOMETHING.SF"));
        jarWriter.write(signatureFile.getBytes(StandardCharsets.UTF_8));
        jarWriter.close();

        // Act
        ImplantHandler handler = new ImplantHandlerMock(testImplant);
        ClassInjector injector = new ClassInjector(handler);
        boolean didInfect = injector.infect(tempInputFile, tempOutputFile);

        // Assert
        assertFalse("Did not infect signed JAR.", didInfect);
        assertFalse("Did not write any output JAR.", Files.exists(tempOutputFile));
    }

    // Corresponds to the standard debugging info produce by javac (lines + source)
    @Test
    public void testInfect_TargetHasStandardDebuggingInfo_Success() throws IOException {
        // Arrange
        ImplantHandler handler = new ImplantHandlerMock(testImplant);

        // Act
        ClassInjector injector = new ClassInjector(handler);
        boolean didInfect = injector.infect(targetAppJarWithDebuggingInfo, tempOutputFile);

        // Assert
        assertTrue("Did successfully inject.", didInfect);
        boolean didFindInitClass = false;
        for (BufferedJarFiddler.BufferedJarEntry entry : BufferedJarFiddler.read(tempOutputFile)) {
            if (entry.getName().endsWith("Init.class")) {
                didFindInitClass = true;
                break;
            }
        }
        assertTrue("Did find injected Init class in output JAR.", didFindInitClass);
    }

    @Test
    public void testInfect_ImplantStandardDebuggingInfo_ObscuredImplantName() throws IOException {
        // Arrange
        ImplantHandler handler = new ImplantHandlerMock(testImplantWithDebug);

        // Act
        ClassInjector injector = new ClassInjector(handler);
        boolean didInfect = injector.infect(targetAppJarWithoutDebuggingInfo, tempOutputFile);

        // Assert
        assertTrue("Did successfully inject.", didInfect);

        byte[] initEntryContent = null;
        for (BufferedJarFiddler.BufferedJarEntry entry : BufferedJarFiddler.read(tempOutputFile)) {
            if (entry.getName().endsWith("Init.class")) {
                initEntryContent = entry.getContent();
            }
        }
        if (initEntryContent == null) {
            fail("Failed to find Init class in infected JAR.");
        }
        ClassFile classFile = readClassFile(initEntryContent);
        boolean didFindOriginalName = classFile.getAttributes().stream()
                .filter(attr -> attr instanceof SourceFileAttribute)
                .map(attr -> (SourceFileAttribute) attr)
                .map(SourceFileAttribute::getFileName)
                .anyMatch(name -> name.contains("TestClassImplant"));
        assertFalse("Injected implant does not contain a SourceFileAttribute with the original name.", didFindOriginalName);
    }

    // Corresponds to javac -g:none
    @Test
    public void testInfect_TargetHasNoDebuggingInfo_Success() throws IOException {
        // Arrange
        ImplantHandler handler = new ImplantHandlerMock(testImplant);

        // Act
        ClassInjector injector = new ClassInjector(handler);
        boolean didInfect = injector.infect(targetAppJarWithoutDebuggingInfo, tempOutputFile);
        boolean didFindInitClass = false;
        for (BufferedJarFiddler.BufferedJarEntry entry : BufferedJarFiddler.read(tempOutputFile)) {
            if (entry.getName().endsWith("Init.class")) {
                didFindInitClass = true;
            }
        }

        // Assert
        assertTrue("Did successfully inject.", didInfect);
        assertTrue("Did find injected Init class in output JAR.", didFindInitClass);
    }

    @Test
    public void testInfect_AlreadyInfectedJar_Untouched() throws IOException {
        // Act
        ImplantHandler handler = new ImplantHandlerMock(testImplant);
        ClassInjector injector = new ClassInjector(handler);
        boolean didInfect = injector.infect(targetAppJarWithoutDebuggingInfo, tempInputFile);
        boolean didInfectASecondTime = injector.infect(tempInputFile, tempOutputFile);

        // Assert
        assertTrue("Did infect the first time.", didInfect);
        assertFalse("Did not infect an already infected JAR.", didInfectASecondTime);
        assertFalse("Did not write any output JAR.", Files.exists(tempOutputFile));
    }
}
