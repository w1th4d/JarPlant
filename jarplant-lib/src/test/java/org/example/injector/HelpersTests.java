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
import static org.junit.Assert.*;

// testMethodName_Input_ExpectedOutcome
public class HelpersTests {
    private ClassFile testClass;

    @Before
    public void getTestClassFile() throws IOException {
        Path testEnv = findTestEnvironmentDir(this.getClass());
        Path aClassFile = testEnv.resolve("org/example/TestClass.class");
        this.testClass = Helpers.readClassFile(aClassFile);
    }

    @Test
    public void testReadClassFile_FileOnDisk_ClassFileInstance() {
        assertNotNull(testClass);
    }

    @Test
    public void testIsStaticFlagSet_KnownStaticField_True() {
        Optional<FieldInfo> knownStaticField = testClass.getFields().stream()
                .filter(field -> field.getName().equals("staticField"))
                .findAny();
        if (knownStaticField.isEmpty()) {
            throw new RuntimeException("Missing field in TestClass. Update test case?");
        }

        boolean staticFlagSet = Helpers.isStaticFlagSet(knownStaticField.get());

        assertTrue(staticFlagSet);
    }

    @Test
    public void testIsVolatileFlagSet_KnownVolatileField_True() {
        Optional<FieldInfo> knownVolatileField = testClass.getFields().stream()
                .filter(field -> field.getName().startsWith("volatileField"))
                .findAny();
        if (knownVolatileField.isEmpty()) {
            throw new RuntimeException("Missing field in TestClass. Update test case?");
        }

        boolean volatileFlagSet = Helpers.isVolatileFlagSet(knownVolatileField.get());

        assertTrue(volatileFlagSet);
    }

    @Test
    public void testSetStaticFlagForMethod_KnownRegularMethod_True() {
        Optional<MethodInfo> knownRegularMethod = testClass.getMethods().stream()
                .filter(method -> method.getName().equals("regularMethod"))
                .findAny();
        if (knownRegularMethod.isEmpty()) {
            throw new RuntimeException("Missing method in TestClass. Update test case?");
        }

        Helpers.setStaticFlagForMethod(knownRegularMethod.get());

        int isStaticNow = knownRegularMethod.get().getAccessFlags() & AccessFlag.STATIC;
        assertTrue(isStaticNow != 0);
    }

    // parsePackageNameFromFcqn()
    @Test
    public void testParsePackageNameFromFcqn_ValidFcqn_CorrectPackageName() {
        String packageName = Helpers.parsePackageNameFromFqcn("org.example.TestClass");

        assertEquals("org.example", packageName);
    }

    @Test(expected = RuntimeException.class)
    public void testParsePackageNameFromFcqn_PlainString_RuntimeException() {
        Helpers.parsePackageNameFromFqcn("orgexampleTestClass");
    }

    @Test(expected = RuntimeException.class)
    public void testParsePackageNameFromFcqn_ClassFileStyle_RuntimeException() {
        Helpers.parsePackageNameFromFqcn("org/example/TestClass");
    }

    @Test(expected = RuntimeException.class)
    public void testParsePackageNameFromFcqn_OnlyClassName_RuntimeException() {
        Helpers.parsePackageNameFromFqcn("TestClass");
    }

    @Test(expected = RuntimeException.class)
    public void testParsePackageNameFromFcqn_OnlyDots_RuntimeException() {
        Helpers.parsePackageNameFromFqcn("...");
    }

    // parseClassNameFromFcqn()
    @Test
    public void testParseClassNameFromFcqn_ValidFcqn_CorrectClassName() {
        String className = Helpers.parseClassNameFromFqcn("org.example.TestClass");

        assertEquals("TestClass", className);
    }

    @Test(expected = RuntimeException.class)
    public void testParseClassNameFromFcqn_PlainString_RuntimeException() {
        Helpers.parseClassNameFromFqcn("orgexampleTestClass");
    }

    @Test(expected = RuntimeException.class)
    public void testParseClassNameFromFcqn_ClassFileStyle_RuntimeException() {
        Helpers.parseClassNameFromFqcn("org/example/TestClass");
    }

    @Test(expected = RuntimeException.class)
    public void testParseClassNameFromFcqn_OnlyClassName_RuntimeException() {
        Helpers.parseClassNameFromFqcn("TestClass");
    }

    @Test(expected = RuntimeException.class)
    public void testParseClassNameFromFcqn_OnlyDots_RuntimeException() {
        Helpers.parseClassNameFromFqcn("...");
    }

    @Test(expected = RuntimeException.class)
    public void testParseClassNameFromFcqn_WithFileExt_RuntimeException() {
        Helpers.parseClassNameFromFqcn("org.example.TestClass.class");
    }

    @Test(expected = RuntimeException.class)
    public void testParseClassNameFromFcqn_WithFileExtOnly_RuntimeException() {
        Helpers.parseClassNameFromFqcn("TestClass.class");
    }

    // convertToClassFormatFcqn()
    @Test
    public void testConvertToClassFormatFcqn_ValidFcqn_CorrectFormat() {
        String classFcqn = Helpers.convertToClassFormatFqcn("org.example.TestClass");

        assertEquals("org/example/TestClass", classFcqn);
    }

    @Test(expected = RuntimeException.class)
    public void testConvertToClassFormatFcqn_AlreadyClassFcqn_RuntimeException() {
        Helpers.convertToClassFormatFqcn("org/example/TestClass");
    }

    @Test(expected = RuntimeException.class)
    public void testConvertToClassFormatFcqn_PlainString_RuntimeException() {
        Helpers.convertToClassFormatFqcn("orgexampleTestClass");
    }

    @Test
    public void testConvertToJarEntry_ValidClassFile_EndsWithPath() {
        JarEntry jarEntry = Helpers.convertToJarEntry(testClass);

        assertTrue("Jar entry ends with the class name path.",
                jarEntry.getName().endsWith("org/example/TestClass.class"));
    }

    @Test
    public void testCreateAndAddClassInitializerStu_ValidClass_ClinitStubWithOnlyAReturnOpcode() throws DuplicateMemberException, IOException {
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
        MethodInfo method = testClass.getMethod("methodWithSomeCode");
        if (method == null) {
            throw new RuntimeException("Missing method in TestClass. Update test case?");
        }
        CodeAttribute codeAttr = method.getCodeAttribute();

        Optional<Integer> index = Helpers.searchForEndOfMethodIndex(codeAttr, codeAttr.iterator());

        assertTrue("Did find the final return opcode.",
                index.isPresent());
        assertEquals("Index points to the last opcode in bytecode.",
                codeAttr.getCodeLength() - 1, (long) index.get());
    }
}
