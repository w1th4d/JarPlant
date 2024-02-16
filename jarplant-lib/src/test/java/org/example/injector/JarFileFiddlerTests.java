package org.example.injector;

import org.example.TestHelpers;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.jar.JarFile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class JarFileFiddlerTests {
    private Path testJar;
    private Path outputJar;
    private final Set<String> expectedFileNames = Set.of(
            "META-INF/",
            "META-INF/MANIFEST.MF",
            "org/",
            "org/example/",
            "org/example/target/",
            "META-INF/maven/",
            "META-INF/maven/org.example/",
            "META-INF/maven/org.example/target-app/",
            "org/example/target/Main.class",
            "org/example/target/Lib.class",
            "META-INF/maven/org.example/target-app/pom.xml",
            "META-INF/maven/org.example/target-app/pom.properties"
    );

    @Before
    public void getTestJar() {
        Path testEnv = TestHelpers.findTestEnvironmentDir(this.getClass());
        Path jarPath = testEnv.resolve("test.jar");
        if (Files.notExists(jarPath)) {
            throw new RuntimeException("Cannot find test JAR: " + jarPath);
        }

        testJar = jarPath;
    }

    @Before
    public void createOutputJar() throws IOException {
        outputJar = Files.createTempFile("JarFileFiddlerTests", UUID.randomUUID().toString());
    }

    @After
    public void cleanupOutputJar() throws IOException {
        if (Files.exists(outputJar)) {
            Files.delete(outputJar);
        }
    }

    @Test
    public void testOpen_OnlyInput_Instance() throws IOException {
        JarFile jarFile;
        try (JarFileFiddler subject = JarFileFiddler.open(testJar)) {
            jarFile = subject.getJarFile();
        }

        assertNotNull(jarFile);
    }

    @Test
    public void testOpen_WithOutput_Instance() throws IOException {
        try (JarFileFiddler subject = JarFileFiddler.open(testJar, outputJar)) {
            assertNotNull(subject);
            assertNotNull(subject.getJarFile());
        }
    }

    @Test
    public void testIterator_OnlyReadWholeJar_FoundAllClasses() throws IOException {
        Set<String> foundFileNames = new HashSet<>(expectedFileNames.size());

        try (JarFileFiddler subject = JarFileFiddler.open(testJar)) {
            for (JarFileFiddler.WrappedJarEntry entry : subject) {
                foundFileNames.add(entry.getName());
            }
        }

        assertEquals("Found all files in JAR.", expectedFileNames, foundFileNames);
    }

    @Test
    public void testIterator_PassOnWholeJar_AllClassesInOutput() throws IOException {
        // Arrange
        Set<String> passedOnEntries = new HashSet<>(expectedFileNames.size());
        Set<String> entriesInOutput = new HashSet<>(expectedFileNames.size());

        // Act
        // Go through the whole testJar and passOn() all entries
        try (JarFileFiddler subject = JarFileFiddler.open(testJar, outputJar)) {
            for (JarFileFiddler.WrappedJarEntry entry : subject) {
                entry.passOn();
                passedOnEntries.add(entry.getName());
            }
        }

        // Assert
        // Now re-open outputJar and go through all entries
        try (JarFileFiddler output = JarFileFiddler.open(outputJar)) {
            for (JarFileFiddler.WrappedJarEntry entry : output) {
                entriesInOutput.add(entry.getName());
            }
        }

        // Match all entries that was passedOn() with the entries found in output
        assertEquals("All files were passed on to output JAR.", passedOnEntries, entriesInOutput);
    }

    @Test
    @Ignore // This is not yet implemented
    public void testIterator_PassOnWholeJar_CopiedEntriesMetadata() throws IOException {
        // Arrange
        Set<JarFileFiddler.WrappedJarEntry> passedOnEntries = new HashSet<>(expectedFileNames.size());
        Set<JarFileFiddler.WrappedJarEntry> entriesInOutput = new HashSet<>(expectedFileNames.size());

        // Act
        // Go through the whole testJar and passOn() all entries
        try (JarFileFiddler subject = JarFileFiddler.open(testJar, outputJar)) {
            for (JarFileFiddler.WrappedJarEntry entry : subject) {
                entry.passOn();
                passedOnEntries.add(entry);
            }
        }

        // Assert
        // Now re-open outputJar and go through all entries
        try (JarFileFiddler output = JarFileFiddler.open(outputJar)) {
            for (JarFileFiddler.WrappedJarEntry entry : output) {
                entriesInOutput.add(entry);
            }
        }

        // Match all entries that was passedOn() with the entries found in output
        assertEquals("All metadata for entries were passed on to output JAR.", passedOnEntries, entriesInOutput);
    }

    @Test
    @Ignore
    public void testIterator_ModifyEntry_OnlyEntryModified() {

    }

    @Test
    @Ignore
    public void testIterator_AddEntryFromBuffer_EntryAdded() {

    }

    @Test
    @Ignore
    public void testIterator_AddEntryFromStream_EntryAdded() {

    }

    @Test
    @Ignore
    public void testIterator_AddAndGetStream_EntryAdded() {

    }

    @Test(expected = Exception.class)
    @Ignore
    public void testIterator_AddToReadOnlyFiddler_Exception() {

    }
}
