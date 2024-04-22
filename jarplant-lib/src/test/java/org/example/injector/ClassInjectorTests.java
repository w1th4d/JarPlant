package org.example.injector;

import javassist.bytecode.*;
import org.example.TestImplantRunner;
import org.example.implants.TestImplant;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.example.TestHelpers.createTempJarFile;
import static org.example.TestHelpers.findTestEnvironmentDir;
import static org.example.injector.Helpers.readClassFile;
import static org.junit.Assert.*;

public class ClassInjectorTests {
    private ClassFile testImplant;
    private String testImplantSourceFileName;

    @Before
    public void getTestImplantClassFile() throws IOException {
        Path testEnv = findTestEnvironmentDir(this.getClass());
        Path testImplantFile = testEnv.resolve("org/example/implants/TestImplant.class");
        this.testImplant = readClassFile(testImplantFile);

        List<String> originalNames = testImplant.getAttributes().stream()
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
        this.testImplantSourceFileName = originalFileName;
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
    @Ignore
    public void testModifyClinit_NoClinit_AddedClinit() {
    }

    /**
     * Test config override consistency.
     * This one is a bit special. It tests that the config values are the same both at time of init() and later.
     * This relates to how and where the config override bytecode is inserted into the implant class initializer.
     */
    @Test
    @Ignore // TODO Fix the failing test
    public void testConfigOverride_DifferentTimeOfRead_SameValues() throws IOException, ClassNotFoundException, ImplantConfigException {
        ImplantHandler implant = ImplantHandler.findAndCreateFor(TestImplant.class);
        implant.setConfig("CONF_STRING", "Modified");
        implant.setConfig("CONF_BOOLEAN", true);
        implant.setConfig("CONF_INT", 2);
        ClassInjector injector = new ClassInjector(implant);
        Path tempJarFile = createTempJarFile(
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
        List<String> originalNames = testImplant.getAttributes().stream()
                .filter(attr -> attr instanceof SourceFileAttribute)
                .map(attr -> (SourceFileAttribute) attr)
                .map(SourceFileAttribute::getFileName)
                .toList();

        // Act
        ClassInjector.deepRenameClass(testImplant, "local.target", "NewName");
        List<String> changedNames = testImplant.getAttributes().stream()
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
        String originalFqcn = Helpers.parsePackageNameFromFqcn(testImplant.getName());
        byte[] classDataBefore = asBytes(testImplant);

        // Act
        ClassInjector.deepRenameClass(testImplant, originalFqcn, testImplantSourceFileName);

        // Assert
        byte[] classDataAfter = asBytes(testImplant);
        assertArrayEquals("Class is not changed", classDataBefore, classDataAfter);
    }

    private static byte[] asBytes(ClassFile classFile) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        classFile.write(new DataOutputStream(buffer));
        return buffer.toByteArray();
    }

    @Test
    @Ignore
    public void testDeepRenameClass_NoDebuggingInfo_Unmodified() {
    }

    // infect

    @Test
    @Ignore
    public void testInfect_NormalJar_SomeClassModified() {
    }

    @Test
    @Ignore
    public void testInfect_NormalJar_AllClassesModified() {
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
