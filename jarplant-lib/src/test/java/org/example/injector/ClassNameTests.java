package org.example.injector;

import javassist.bytecode.ClassFile;
import org.junit.Ignore;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.jar.JarEntry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class ClassNameTests {
    @Test
    public void testFromFullClassName_fullClassName_success() throws ClassNameException {
        // Arrange
        String input = "org.example.ClassName";

        // Act
        ClassName subject = ClassName.fromFullClassName(input);

        // Assert
        assertEquals("org.example.ClassName", subject.getFullClassName());
        assertEquals("org.example", subject.getPackageName());
        assertEquals("ClassName", subject.getClassName());
        assertEquals("org/example/ClassName", subject.getClassFormatInternalName());
        assertEquals("org/example/ClassName.class", subject.getClassFilePath());
        assertEquals("BOOT-INF/classes/org/example/ClassName.class", subject.getSpringJarEntryPath());
    }

    @Test
    public void testFromFullClassName_nameWithValidSpecialChars_fine() throws ClassNameException {
        // Arrange
        String input = "org.example-thing.Class_Name2";

        // Act
        ClassName subject = ClassName.fromFullClassName(input);

        // Assert
        assertEquals("org.example-thing.Class_Name2", subject.getFullClassName());
        assertEquals("org.example-thing", subject.getPackageName());
        assertEquals("Class_Name2", subject.getClassName());
        assertEquals("org/example-thing/Class_Name2", subject.getClassFormatInternalName());
        assertEquals("org/example-thing/Class_Name2.class", subject.getClassFilePath());
        assertEquals("BOOT-INF/classes/org/example-thing/Class_Name2.class", subject.getSpringJarEntryPath());
    }

    @Test
    public void testFromFullClassName_fullClassNamePlusExtension_extensionRemoved() throws ClassNameException {
        // Arrange
        String input = "org.example.ClassName.class";

        // Act
        ClassName subject = ClassName.fromFullClassName(input);

        // Assert
        assertEquals("org.example.ClassName", subject.getFullClassName());
        assertEquals("org.example", subject.getPackageName());
        assertEquals("ClassName", subject.getClassName());
        assertEquals("org/example/ClassName", subject.getClassFormatInternalName());
        assertEquals("org/example/ClassName.class", subject.getClassFilePath());
        assertEquals("BOOT-INF/classes/org/example/ClassName.class", subject.getSpringJarEntryPath());
    }

    @Test
    public void testFromFullClassName_justClassName_successEmptyPackage() throws ClassNameException {
        // Arrange
        String input = "ClassName";

        // Act
        ClassName subject = ClassName.fromFullClassName(input);

        // Assert
        assertEquals("ClassName", subject.getFullClassName());
        assertEquals("", subject.getPackageName());
        assertEquals("ClassName", subject.getClassName());
        assertEquals("ClassName", subject.getClassFormatInternalName());
        assertEquals("ClassName.class", subject.getClassFilePath());
        assertEquals("BOOT-INF/classes/ClassName.class", subject.getSpringJarEntryPath());
    }

    @Test
    public void testFromFullClassName_justClassNameWithExtension_successEmptyPackageExtensionRemoved() throws ClassNameException {
        // Arrange
        String input = "ClassName.class";

        // Act
        ClassName subject = ClassName.fromFullClassName(input);

        // Assert
        assertEquals("ClassName", subject.getFullClassName());
        assertEquals("", subject.getPackageName());
        assertEquals("ClassName", subject.getClassName());
        assertEquals("ClassName", subject.getClassFormatInternalName());
        assertEquals("ClassName.class", subject.getClassFilePath());
        assertEquals("BOOT-INF/classes/ClassName.class", subject.getSpringJarEntryPath());
    }

    @Test
    public void testFromFullClassName_fullInnerClassName_success() throws ClassNameException {
        // Arrange
        String input = "org.example.ClassName$InnerClass";

        // Act
        ClassName subject = ClassName.fromFullClassName(input);

        // Assert
        assertEquals("org.example.ClassName$InnerClass", subject.getFullClassName());
        assertEquals("org.example", subject.getPackageName());
        assertEquals("ClassName$InnerClass", subject.getClassName());
        assertEquals("org/example/ClassName$InnerClass", subject.getClassFormatInternalName());
        assertEquals("org/example/ClassName$InnerClass.class", subject.getClassFilePath());
        assertEquals("BOOT-INF/classes/org/example/ClassName$InnerClass.class", subject.getSpringJarEntryPath());
    }

    @Test
    public void testFromFullClassName_fullInnerClassNamePlusExtension_extensionRemoved() throws ClassNameException {
        // Arrange
        String input = "org.example.ClassName$InnerClass.class";

        // Act
        ClassName subject = ClassName.fromFullClassName(input);

        // Assert
        assertEquals("org.example.ClassName$InnerClass", subject.getFullClassName());
        assertEquals("org.example", subject.getPackageName());
        assertEquals("ClassName$InnerClass", subject.getClassName());
        assertEquals("org/example/ClassName$InnerClass", subject.getClassFormatInternalName());
        assertEquals("org/example/ClassName$InnerClass.class", subject.getClassFilePath());
        assertEquals("BOOT-INF/classes/org/example/ClassName$InnerClass.class", subject.getSpringJarEntryPath());
    }

    @Test
    public void testFromFullClassName_justInnerClassName_successEmptyPackage() throws ClassNameException {
        // Arrange
        String input = "ClassName$InnerClass";

        // Act
        ClassName subject = ClassName.fromFullClassName(input);

        // Assert
        assertEquals("ClassName$InnerClass", subject.getFullClassName());
        assertEquals("", subject.getPackageName());
        assertEquals("ClassName$InnerClass", subject.getClassName());
        assertEquals("ClassName$InnerClass", subject.getClassFormatInternalName());
        assertEquals("ClassName$InnerClass.class", subject.getClassFilePath());
        assertEquals("BOOT-INF/classes/ClassName$InnerClass.class", subject.getSpringJarEntryPath());
    }

    @Test
    public void testFromFullClassName_justInnerClassNamePlusExtension_successEmptyPackageRemovedExtension() throws ClassNameException {
        // Arrange
        String input = "ClassName$InnerClass.class";

        // Act
        ClassName subject = ClassName.fromFullClassName(input);

        // Assert
        assertEquals("ClassName$InnerClass", subject.getFullClassName());
        assertEquals("", subject.getPackageName());
        assertEquals("ClassName$InnerClass", subject.getClassName());
        assertEquals("ClassName$InnerClass", subject.getClassFormatInternalName());
        assertEquals("ClassName$InnerClass.class", subject.getClassFilePath());
        assertEquals("BOOT-INF/classes/ClassName$InnerClass.class", subject.getSpringJarEntryPath());
    }

    @Test
    @Ignore // Technically, these are invalid class names, but ClassFile will accept them
    public void testFromFullClassName_invalidCharacters_fail() {
        List<String> invalidNames = Arrays.asList(
                "org.example.1Class",
                "1Class",
                "org.example.Class-Name",
                "$",
                ".class",
                "$.class",
                "123",
                "-",
                "int",
                "long",
                "boolean",
                "class",
                "package",
                "synchronized"
        );

        for (String invalidName : invalidNames) {
            try {
                ClassName.fromFullClassName(invalidName);
                fail(invalidName);
            } catch (ClassNameException ignored) {
                // Good
            }
        }
    }

    @Test
    public void testFromClass_someClass_success() {
        // Act
        ClassName subject = ClassName.of(this.getClass());

        // Assert
        assertEquals("org.example.injector.ClassNameTests", subject.getFullClassName());
        assertEquals("org.example.injector", subject.getPackageName());
        assertEquals("ClassNameTests", subject.getClassName());
        assertEquals("org/example/injector/ClassNameTests", subject.getClassFormatInternalName());
        assertEquals("org/example/injector/ClassNameTests.class", subject.getClassFilePath());
        assertEquals("BOOT-INF/classes/org/example/injector/ClassNameTests.class", subject.getSpringJarEntryPath());
    }

    @Test
    public void testFromClassFile_someClassFile_success() {
        // Arrange
        ClassFile classFile = new ClassFile(false, "org.example.ClassName", null);

        // Act
        ClassName subject = ClassName.of(classFile);

        // Assert
        assertEquals("org.example.ClassName", subject.getFullClassName());
        assertEquals("org.example", subject.getPackageName());
        assertEquals("ClassName", subject.getClassName());
        assertEquals("org/example/ClassName", subject.getClassFormatInternalName());
        assertEquals("org/example/ClassName.class", subject.getClassFilePath());
        assertEquals("BOOT-INF/classes/org/example/ClassName.class", subject.getSpringJarEntryPath());
    }

    @Test
    public void testFromJarEntry_someJarEntry_success() throws ClassNameException {
        // Arrange
        JarEntry jarEntry = new JarEntry("org/example/ClassName.class");

        // Act
        ClassName subject = ClassName.of(jarEntry);

        // Assert
        assertEquals("org.example.ClassName", subject.getFullClassName());
        assertEquals("org.example", subject.getPackageName());
        assertEquals("ClassName", subject.getClassName());
        assertEquals("org/example/ClassName", subject.getClassFormatInternalName());
        assertEquals("org/example/ClassName.class", subject.getClassFilePath());
        assertEquals("BOOT-INF/classes/org/example/ClassName.class", subject.getSpringJarEntryPath());
    }

    @Test(expected = ClassNameException.class)
    public void testFromJarEntry_someNonClassEntry_failed() throws ClassNameException {
        // Arrange
        JarEntry jarEntry = new JarEntry("resources/something.txt");

        // Act
        ClassName.of(jarEntry);
    }
}
