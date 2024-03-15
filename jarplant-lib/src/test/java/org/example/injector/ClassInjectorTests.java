package org.example.injector;

import javassist.bytecode.*;
import org.example.TestImplantRunner;
import org.example.implants.TestImplant;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import static org.example.TestHelpers.createTempJarFile;
import static org.example.TestHelpers.findTestEnvironmentDir;
import static org.example.injector.Helpers.readClassFile;
import static org.junit.Assert.*;

public class ClassInjectorTests {
    private ClassFile testImplant;

    @Before
    public void getTestClassFile() throws IOException {
        Path testEnv = findTestEnvironmentDir(this.getClass());
        Path aClassFile = testEnv.resolve("org/example/implants/TestImplant.class");
        this.testImplant = readClassFile(aClassFile);
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
