package io.github.w1th4d.jarplant;

import javassist.bytecode.*;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

import static io.github.w1th4d.jarplant.Helpers.readClassFile;
import static io.github.w1th4d.jarplant.TestHelpers.findTestEnvironmentDir;
import static org.junit.Assert.*;

/**
 * Tests for the helper methods in the actual JarPlant library.
 * Not to be confused with TestHelpers and TestHelpersTests.
 */
public class HelpersTests {
    private ClassFile testClass;

    @Before
    public void getTestClassFile() throws IOException {
        Path testEnv = findTestEnvironmentDir(this.getClass());
        Path aClassFile = testEnv.resolve("io/github/w1th4d/jarplant/TestClass.class");
        this.testClass = readClassFile(aClassFile);
    }

    @Test
    public void testReadClassFile_FileOnDisk_ClassFileInstance() {
        // Assert
        assertNotNull("Class file was read and parsed.", testClass);
    }

    @Test
    public void testIsStaticFlagSet_KnownStaticField_True() {
        // Arrange
        Optional<FieldInfo> knownStaticField = testClass.getFields().stream()
                .filter(field -> field.getName().equals("staticField"))
                .findAny();
        if (knownStaticField.isEmpty()) {
            throw new RuntimeException("Missing field in TestClass. Update test case?");
        }

        // Act
        boolean staticFlagSet = Helpers.isStaticFlagSet(knownStaticField.get());

        // Assert
        assertTrue("Static field was properly identified.", staticFlagSet);
    }

    @Test
    public void testIsVolatileFlagSet_KnownVolatileField_True() {
        // Arrange
        Optional<FieldInfo> knownVolatileField = testClass.getFields().stream()
                .filter(field -> field.getName().startsWith("volatileField"))
                .findAny();
        if (knownVolatileField.isEmpty()) {
            throw new RuntimeException("Missing field in TestClass. Update test case?");
        }

        // Act
        boolean volatileFlagSet = Helpers.isVolatileFlagSet(knownVolatileField.get());

        // Assert
        assertTrue("Volatile field was properly identified.", volatileFlagSet);
    }

    @Test
    public void testSetStaticFlagForMethod_KnownRegularMethod_True() {
        // Arrange
        Optional<MethodInfo> knownRegularMethod = testClass.getMethods().stream()
                .filter(method -> method.getName().equals("regularMethod"))
                .findAny();
        if (knownRegularMethod.isEmpty()) {
            throw new RuntimeException("Missing method in TestClass. Update test case?");
        }

        // Act
        Helpers.setStaticFlagForMethod(knownRegularMethod.get());

        // Assert
        int isStaticNow = knownRegularMethod.get().getAccessFlags() & AccessFlag.STATIC;
        assertTrue("Static flag was set for a regular method.", isStaticNow != 0);
    }

    @Test
    public void testCreateAndAddClassInitializerStub_ValidClass_ClinitStubWithOnlyAReturnOpcode() throws DuplicateMemberException, IOException {
        // Act
        MethodInfo stub = Helpers.createAndAddClassInitializerStub(testClass);

        // Assert
        assertEquals("<clinit>", stub.getName());
        assertTrue("<clinit> method is static.",
                (stub.getAccessFlags() & AccessFlag.STATIC) != 0);
        assertEquals("<clinit> stub only contains one opcode.",
                1, stub.getCodeAttribute().getCodeLength());

        // Assert: Interpret bytecode as unsigned byte
        DataInput converter = new DataInputStream(new ByteArrayInputStream(stub.getCodeAttribute().getCode()));
        int opcode = converter.readUnsignedByte();
        assertEquals("Stub <clinit> is just a return opcode.",
                Opcode.RETURN, opcode);
    }

    @Test
    public void testSearchForEndOfMethodIndex_ValidCode_LastIndexOfBytecode() throws IOException {
        // Arrange
        MethodInfo method = testClass.getMethod("methodWithSomeCode");
        if (method == null) {
            throw new RuntimeException("Missing method in TestClass. Update test case?");
        }
        CodeAttribute codeAttr = method.getCodeAttribute();

        // Act
        Optional<Integer> index = Helpers.searchForEndOfMethodIndex(codeAttr, codeAttr.iterator());

        // Assert
        assertTrue("Did find the final return opcode.",
                index.isPresent());
        assertEquals("Index points to the last opcode in bytecode.",
                codeAttr.getCodeLength() - 1, (long) index.get());
    }
}
