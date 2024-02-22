package org.example.injector;

import javassist.bytecode.ClassFile;
import javassist.bytecode.DuplicateMemberException;
import javassist.bytecode.FieldInfo;
import org.example.TestHelpers;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;

import static org.junit.Assert.*;

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
    public void testIterator_ForwardWholeJar_AllClassesInOutput() throws IOException {
        Set<String> forwardedEntries = new HashSet<>(expectedFileNames.size());

        // Go through the whole testJar and forward() all entries
        try (JarFileFiddler subject = JarFileFiddler.open(testJar, outputJar)) {
            for (JarFileFiddler.WrappedJarEntry entry : subject) {
                entry.forward();
                forwardedEntries.add(entry.getName());
            }
        }

        // Now re-open outputJar and go through all entries
        Set<String> foundEntries = readAllJarEntries(outputJar).stream()
                .map(ZipEntry::getName)
                .collect(Collectors.toSet());
        assertEquals("All files were forwarded to output JAR.", foundEntries, forwardedEntries);
    }

    @Test
    @Ignore // JarFileFiddler does not yet copy all the ZipFile properties (WrappedJarEntry.equals() checks these)
    public void testIterator_ForwardWholeJar_CopiedEntriesMetadata() throws IOException {
        Set<JarEntry> forwardedEntries = new HashSet<>(expectedFileNames.size());

        try (JarFileFiddler subject = JarFileFiddler.open(testJar, outputJar)) {
            for (JarFileFiddler.WrappedJarEntry entry : subject) {
                entry.forward();
                forwardedEntries.add(entry.getEntry());
            }
        }

        Set<JarEntry> entriesInOutput = readAllJarEntries(outputJar);
        assertEquals("All metadata for entries were forwarded to output JAR.", forwardedEntries, entriesInOutput);
    }

    @Test
    public void testIterator_ModifyEntry_OnlyEntryModified() throws IOException {
        String nameOfMain = "org/example/target/Main.class";
        HashMap<String, Long> originalFileHashes = new HashMap<>();

        // Go through the entire JAR and modify Main.class
        try (JarFileFiddler subject = JarFileFiddler.open(testJar, outputJar)) {
            for (JarFileFiddler.WrappedJarEntry entry : subject) {
                originalFileHashes.put(entry.getName(), entry.getEntry().getCrc());

                if (entry.getName().equals(nameOfMain)) {
                    ClassFile mainClass = new ClassFile(new DataInputStream(entry.getContent()));
                    mainClass.addField(new FieldInfo(mainClass.getConstPool(), "ADDED_FIELD", "I"));
                    // This is what's being tested:
                    mainClass.write(entry.replaceAndGetStream());
                } else {
                    entry.forward();
                }
            }
        } catch (DuplicateMemberException e) {
            throw new RuntimeException(e);
        }

        // Make sure that only Main.class is modified (but not the other entries)
        for (JarEntry entry : readAllJarEntries(outputJar)) {
            long originalHash = originalFileHashes.get(entry.getName());
            long foundHash = entry.getCrc();
            if (entry.getName().equals(nameOfMain)) {
                assertNotEquals("Modified entry CRC differs.", originalHash, foundHash);
            } else {
                assertEquals("Unmodified entry CRC matches.", originalHash, foundHash);
            }
        }
    }

    @Test
    public void testIterator_ReplaceEntryFromBuffer_EntryReplaced() throws IOException {
        String nameOfMain = "org/example/target/Main.class";
        ByteBuffer replacementData = ByteBuffer.wrap(new byte[]{1, 2, 3, 4, 5});

        // Go through entire JAR but replace Main with something else
        try (JarFileFiddler subject = JarFileFiddler.open(testJar, outputJar)) {
            for (JarFileFiddler.WrappedJarEntry entry : subject) {
                if (entry.getName().equals(nameOfMain)) {
                    // This is what's being tested
                    entry.replaceWith(replacementData);
                } else {
                    entry.forward();
                }
            }
        }

        // Take a look at the Main only
        try (JarFile output = new JarFile(outputJar.toFile())) {
            ZipEntry main = output.getEntry(nameOfMain);
            assertNotNull("Entry was added.", main);

            long originalHash = crc32(replacementData.rewind());
            long foundHash = main.getCrc();
            assertEquals("Entry is modified.", originalHash, foundHash);
        }
    }

    @Test
    public void testIterator_ReplaceEntryFromStream_EntryReplaced() throws IOException {
        String nameOfMain = "org/example/target/Main.class";
        byte[] replacementData = new byte[]{1, 2, 3, 4, 5};
        InputStream replacementStream = new ByteArrayInputStream(replacementData);

        // Go through entire JAR but replace Main with something else
        try (JarFileFiddler subject = JarFileFiddler.open(testJar, outputJar)) {
            for (JarFileFiddler.WrappedJarEntry entry : subject) {
                if (entry.getName().equals(nameOfMain)) {
                    // This is what's being tested
                    entry.replaceWith(replacementStream);
                } else {
                    entry.forward();
                }
            }
        }

        // Take a look at the Main only
        try (JarFile output = new JarFile(outputJar.toFile())) {
            ZipEntry main = output.getEntry(nameOfMain);
            assertNotNull("Entry was added.", main);

            long originalHash = crc32(ByteBuffer.wrap(replacementData));
            long foundHash = main.getCrc();
            assertEquals("Entry is modified.", originalHash, foundHash);
        }
    }

    @Test
    public void testIterator_ReplaceAndGetStream_EntryReplaced() throws IOException {
        String nameOfMain = "org/example/target/Main.class";
        byte[] replacementData = new byte[]{1, 2, 3, 4, 5};

        // Go through entire JAR but replace Main with something else
        try (JarFileFiddler subject = JarFileFiddler.open(testJar, outputJar)) {
            for (JarFileFiddler.WrappedJarEntry entry : subject) {
                if (entry.getName().equals(nameOfMain)) {
                    // This is what's being tested
                    DataOutputStream replacementStream = entry.replaceAndGetStream();
                    replacementStream.write(replacementData);
                } else {
                    entry.forward();
                }
            }
        }

        // Take a look at the Main only
        try (JarFile output = new JarFile(outputJar.toFile())) {
            ZipEntry main = output.getEntry(nameOfMain);
            assertNotNull("Entry was added.", main);

            long originalHash = crc32(ByteBuffer.wrap(replacementData));
            long foundHash = main.getCrc();
            assertEquals("Entry is modified.", originalHash, foundHash);
        }
    }

    @Test(expected = IllegalStateException.class)
    public void testIterator_AddToReadOnlyFiddler_Exception() throws IOException {
        try (JarFileFiddler subject = JarFileFiddler.open(testJar)) {
            for (JarFileFiddler.WrappedJarEntry entry : subject) {
                // Forward it despite there being no output JAR
                entry.forward();
            }
        }
    }

    @Test(expected = IllegalStateException.class)
    public void testIterator_ForwardSameEntryTwice_Exception() throws IOException {
        try (JarFileFiddler subject = JarFileFiddler.open(testJar, outputJar)) {
            for (JarFileFiddler.WrappedJarEntry entry : subject) {
                // Forward it twice
                entry.forward();
                entry.forward();
            }
        }
    }

    @Test(expected = IllegalStateException.class)
    public void testIterator_ModifyAndForwardSameEntry_Exception() throws IOException {
        ByteBuffer replacement = ByteBuffer.wrap(new byte[]{1, 2, 3, 4, 5});

        try (JarFileFiddler subject = JarFileFiddler.open(testJar, outputJar)) {
            for (JarFileFiddler.WrappedJarEntry entry : subject) {
                // First forward and then try to modify the same entry
                entry.forward();
                entry.replaceWith(replacement);
            }
        }
    }

    @Test(expected = Exception.class)
    public void testCreate_SameFile_Exception() throws IOException {
        JarFileFiddler subject = JarFileFiddler.open(testJar, testJar);
        subject.close();
    }

    private static Set<JarEntry> readAllJarEntries(Path jarFile) {
        Set<JarEntry> results = new HashSet<>();

        try (JarFile jar = new JarFile(jarFile.toFile())) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                results.add(entries.nextElement());
            }
        } catch (IOException e) {
            throw new RuntimeException("Cannot open JAR file used for testing.", e);
        }

        return results;
    }

    private static long crc32(ByteBuffer buffer) {
        CRC32 sum = new CRC32();
        sum.update(buffer);
        return sum.getValue();
    }
}
