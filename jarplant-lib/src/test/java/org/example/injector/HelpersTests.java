package org.example.injector;

import javassist.bytecode.*;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.jar.JarEntry;

import static org.example.TestHelpers.findTestEnvironmentDir;
import static org.example.injector.Helpers.readClassFile;
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
        Path aClassFile = testEnv.resolve("org/example/TestClass.class");
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
    public void testParsePackageNameFromFcqn_ValidFcqn_CorrectPackageName() {
        // Act
        String packageName = Helpers.parsePackageNameFromFqcn("org.example.TestClass");

        // Assert
        assertEquals("Parsed package name.", "org.example", packageName);
    }

    @Test(expected = RuntimeException.class)
    public void testParsePackageNameFromFcqn_PlainString_RuntimeException() {
        // Act + Assert
        Helpers.parsePackageNameFromFqcn("orgexampleTestClass");
    }

    @Test(expected = RuntimeException.class)
    public void testParsePackageNameFromFcqn_ClassFileStyle_RuntimeException() {
        // Act + Assert
        Helpers.parsePackageNameFromFqcn("org/example/TestClass");
    }

    @Test(expected = RuntimeException.class)
    public void testParsePackageNameFromFcqn_OnlyClassName_RuntimeException() {
        // Act + Assert
        Helpers.parsePackageNameFromFqcn("TestClass");
    }

    @Test(expected = RuntimeException.class)
    public void testParsePackageNameFromFcqn_OnlyDots_RuntimeException() {
        // Act + Assert
        Helpers.parsePackageNameFromFqcn("...");
    }

    @Test
    public void testParseClassNameFromFcqn_ValidFcqn_CorrectClassName() {
        // Act
        String className = Helpers.parseClassNameFromFqcn("org.example.TestClass");

        // Assert
        assertEquals("Class name was parsed.", "TestClass", className);
    }

    @Test(expected = RuntimeException.class)
    public void testParseClassNameFromFcqn_PlainString_RuntimeException() {
        // Act + Assert
        Helpers.parseClassNameFromFqcn("orgexampleTestClass");
    }

    @Test(expected = RuntimeException.class)
    public void testParseClassNameFromFcqn_ClassFileStyle_RuntimeException() {
        // Act + Assert
        Helpers.parseClassNameFromFqcn("org/example/TestClass");
    }

    @Test(expected = RuntimeException.class)
    public void testParseClassNameFromFcqn_OnlyClassName_RuntimeException() {
        // Act + Assert
        Helpers.parseClassNameFromFqcn("TestClass");
    }

    @Test(expected = RuntimeException.class)
    public void testParseClassNameFromFcqn_OnlyDots_RuntimeException() {
        // Act + Assert
        Helpers.parseClassNameFromFqcn("...");
    }

    @Test(expected = RuntimeException.class)
    public void testParseClassNameFromFcqn_WithFileExt_RuntimeException() {
        // Act + Assert
        Helpers.parseClassNameFromFqcn("org.example.TestClass.class");
    }

    @Test(expected = RuntimeException.class)
    public void testParseClassNameFromFcqn_WithFileExtOnly_RuntimeException() {
        // Act + Assert
        Helpers.parseClassNameFromFqcn("TestClass.class");
    }

    @Test
    public void testConvertToClassFormatFcqn_ValidFcqn_CorrectFormat() {
        // Act
        String classFcqn = Helpers.convertToClassFormatFqcn("org.example.TestClass");

        // Assert
        assertEquals("Binary name was converted to fully qualified class name used in the ConstPool.",
                "org/example/TestClass", classFcqn);
    }

    @Test(expected = RuntimeException.class)
    public void testConvertToClassFormatFcqn_AlreadyClassFcqn_RuntimeException() {
        // Act + Assert
        Helpers.convertToClassFormatFqcn("org/example/TestClass");
    }

    @Test(expected = RuntimeException.class)
    public void testConvertToClassFormatFcqn_PlainString_RuntimeException() {
        // Act + Assert
        Helpers.convertToClassFormatFqcn("orgexampleTestClass");
    }

    @Test
    public void convertToBinaryClassNameFromPath_ValidJarEntryPath_BinaryClassName() {
        // Act + Assert
        assertEquals("",
                Helpers.convertToBinaryClassNameFromPath("org/example/TestClass.class"),
                "org.example.TestClass");
        assertEquals(Helpers.convertToBinaryClassNameFromPath("/org/example/TestClass.class"),
                "org.example.TestClass");
        assertEquals(Helpers.convertToBinaryClassNameFromPath("TestClass.class"),
                "TestClass");
        assertEquals(Helpers.convertToBinaryClassNameFromPath("/TestClass.class"),
                "TestClass");
    }

    @Test
    public void convertToBinaryClassNameFromPath_InvalidJarEntryPath_Exception() {
        // Arrange
        String[] invalidInputs = new String[]{
                "org/example/TestClass",
                "org/example/TestClass/",
                "TestClass",
                "org.example.TestClass",
                "org.example.TestClass.class",
                "",
        };

        // Act + Assert: Fail if any input did not result in an exception
        for (String invalidInput : invalidInputs) {
            try {
                Helpers.convertToBinaryClassNameFromPath(invalidInput);
                fail();
            } catch (Exception ignored) {
            }
        }
    }

    @Test
    public void testConvertToJarEntry_ValidClassFile_EndsWithPath() {
        // Act
        JarEntry jarEntry = Helpers.convertToJarEntry(testClass);

        // Assert
        assertTrue("JAR entry ends with the class name path.",
                jarEntry.getName().endsWith("org/example/TestClass.class"));
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
