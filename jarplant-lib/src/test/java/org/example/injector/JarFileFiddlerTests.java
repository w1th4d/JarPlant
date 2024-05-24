package org.example.injector;

import javassist.bytecode.ClassFile;
import javassist.bytecode.DuplicateMemberException;
import javassist.bytecode.FieldInfo;
import org.example.TestHelpers;
import org.junit.After;
import org.junit.Before;
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
            "META-INF/maven/org.example/test-app-pojo/",
            "org/example/target/Main.class",
            "org/example/target/Lib.class",
            "META-INF/maven/org.example/test-app-pojo/pom.xml",
            "META-INF/maven/org.example/test-app-pojo/pom.properties"
    );

    @Before
    public void getTestJar() {
        Path testEnv = TestHelpers.findTestEnvironmentDir(this.getClass());
        Path jarPath = testEnv.resolve("test-app-pojo-with-debug.jar");
        if (Files.notExists(jarPath)) {
            throw new RuntimeException("Cannot find test JAR: " + jarPath);
        }

        testJar = jarPath;
    }

    @Before
    public void createOutputJar() throws IOException {
        outputJar = Files.createTempFile("JarFileFiddlerTests-", UUID.randomUUID().toString());
    }

    @After
    public void removeOutputJar() throws IOException {
        Files.delete(outputJar);
    }

    @Test
    public void testOpen_OnlyInput_Instance() throws IOException {
        // Act
        JarFile jarFile;
        try (JarFileFiddler subject = JarFileFiddler.open(testJar)) {
            jarFile = subject.getJarFile();
        }

        // Assert
        assertNotNull(jarFile);
    }

    @Test
    public void testOpen_WithOutput_Instance() throws IOException {
        // Act
        try (JarFileFiddler subject = JarFileFiddler.open(testJar, outputJar)) {
            // Assert
            assertNotNull("JAR opened.", subject);
            assertNotNull("JAR is returned.", subject.getJarFile());
        }
    }

    @Test
    public void testIterator_OnlyReadWholeJar_FoundAllClasses() throws IOException {
        // Arrange
        Set<String> foundFileNames = new HashSet<>(expectedFileNames.size());

        // Act: Go through all entries
        try (JarFileFiddler subject = JarFileFiddler.open(testJar)) {
            for (JarFileFiddler.WrappedJarEntry entry : subject) {
                foundFileNames.add(entry.getName());
            }
        }

        // Assert
        assertEquals("Found all files in JAR.", expectedFileNames, foundFileNames);
    }

    @Test
    public void testIterator_ForwardWholeJar_AllClassesInOutput() throws IOException {
        // Arrange
        Set<String> forwardedEntries = new HashSet<>(expectedFileNames.size());

        // Act: Go through the whole testJar and forward() all entries
        try (JarFileFiddler subject = JarFileFiddler.open(testJar, outputJar)) {
            for (JarFileFiddler.WrappedJarEntry entry : subject) {
                entry.forward();
                forwardedEntries.add(entry.getName());
            }
        }

        // Assert: Re-open outputJar and go through all entries
        Set<String> foundEntries = readAllJarEntries(outputJar).stream()
                .map(ZipEntry::getName)
                .collect(Collectors.toSet());
        assertEquals("All files were forwarded to output JAR.", foundEntries, forwardedEntries);
    }

    @Test
    public void testIterator_ForwardWholeJar_CopiedEntriesMetadata() throws IOException {
        // Arrange
        Set<JarEntry> forwardedEntries = new HashSet<>(expectedFileNames.size());

        // Act: Go through and forward all entries
        try (JarFileFiddler subject = JarFileFiddler.open(testJar, outputJar)) {
            for (JarFileFiddler.WrappedJarEntry entry : subject) {
                entry.forward();
                forwardedEntries.add(entry.getEntry());
            }
        }

        // Assert: Make sure all metadata is intact
        try (JarFile outputJarFile = new JarFile(outputJar.toFile())) {
            for (JarEntry forwardedEntry : forwardedEntries) {
                JarEntry correspondingOutputEntry = (JarEntry) outputJarFile.getEntry(forwardedEntry.getName());
                assertAllJarEntryMetadataEquals(forwardedEntry, correspondingOutputEntry);
            }
        }
    }

    @Test
    public void testIterator_ModifyEntry_OnlyEntryModified() throws IOException {
        // Arrange
        String nameOfMain = "org/example/target/Main.class";
        HashMap<String, Long> originalFileHashes = new HashMap<>();

        // Act: Go through the entire JAR and modify Main.class
        try (JarFileFiddler subject = JarFileFiddler.open(testJar, outputJar)) {
            for (JarFileFiddler.WrappedJarEntry entry : subject) {
                originalFileHashes.put(entry.getName(), entry.getEntry().getCrc());

                if (entry.getName().equals(nameOfMain)) {
                    ClassFile mainClass = new ClassFile(new DataInputStream(entry.getContent()));
                    mainClass.addField(new FieldInfo(mainClass.getConstPool(), "ADDED_FIELD", "I"));
                    // This is what's being tested:
                    mainClass.write(entry.replaceContentByStream());
                } else {
                    entry.forward();
                }
            }
        } catch (DuplicateMemberException e) {
            throw new RuntimeException(e);
        }

        // Assert: Make sure that only Main.class is modified (but not the other entries)
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
        // Arrange
        String nameOfMain = "org/example/target/Main.class";
        ByteBuffer replacementData = ByteBuffer.wrap(new byte[]{1, 2, 3, 4, 5});

        // Act: Go through entire JAR but replace Main with something else
        try (JarFileFiddler subject = JarFileFiddler.open(testJar, outputJar)) {
            for (JarFileFiddler.WrappedJarEntry entry : subject) {
                if (entry.getName().equals(nameOfMain)) {
                    // This is what's being tested
                    entry.replaceContentWith(replacementData);
                } else {
                    entry.forward();
                }
            }
        }

        // Arrange: Take a look at the Main only
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
        // Arrange
        String nameOfMain = "org/example/target/Main.class";
        byte[] replacementData = new byte[]{1, 2, 3, 4, 5};
        InputStream replacementStream = new ByteArrayInputStream(replacementData);

        // Act: Go through entire JAR but replace Main with something else
        try (JarFileFiddler subject = JarFileFiddler.open(testJar, outputJar)) {
            for (JarFileFiddler.WrappedJarEntry entry : subject) {
                if (entry.getName().equals(nameOfMain)) {
                    // This is what's being tested
                    entry.replaceContentWith(replacementStream);
                } else {
                    entry.forward();
                }
            }
        }

        // Assert: Take a look at the Main only
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
        // Arrange
        String nameOfMain = "org/example/target/Main.class";
        byte[] replacementData = new byte[]{1, 2, 3, 4, 5};

        // Act: Go through entire JAR but replace Main with something else
        try (JarFileFiddler subject = JarFileFiddler.open(testJar, outputJar)) {
            for (JarFileFiddler.WrappedJarEntry entry : subject) {
                if (entry.getName().equals(nameOfMain)) {
                    // This is what's being tested
                    DataOutputStream replacementStream = entry.replaceContentByStream();
                    replacementStream.write(replacementData);
                } else {
                    entry.forward();
                }
            }
        }

        // Arrange: Take a look at the Main only
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
        // Act + Assert
        try (JarFileFiddler subject = JarFileFiddler.open(testJar)) {
            for (JarFileFiddler.WrappedJarEntry entry : subject) {
                // Forward it despite there being no output JAR
                entry.forward();
            }
        }
    }

    @Test(expected = IllegalStateException.class)
    public void testIterator_ForwardSameEntryTwice_Exception() throws IOException {
        // Act + Assert
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
        // Arrange
        ByteBuffer replacement = ByteBuffer.wrap(new byte[]{1, 2, 3, 4, 5});

        // Act + Assert
        try (JarFileFiddler subject = JarFileFiddler.open(testJar, outputJar)) {
            for (JarFileFiddler.WrappedJarEntry entry : subject) {
                // First forward and then try to modify the same entry
                entry.forward();
                entry.replaceContentWith(replacement);
            }
        }
    }

    @Test(expected = Exception.class)
    public void testCreate_SameFile_Exception() throws IOException {
        // Act + Assert
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

    private static void assertAllJarEntryMetadataEquals(JarEntry a, JarEntry b) {
        assertEquals(a.getName(), b.getName());
        assertEquals(a.getSize(), b.getSize());
        assertEquals(a.getCompressedSize(), b.getCompressedSize());
        assertEquals(a.getCrc(), b.getCrc());
        assertEquals(a.getCreationTime(), b.getCreationTime());
        assertEquals(a.getLastModifiedTime(), b.getLastModifiedTime());
        assertEquals(a.getLastAccessTime(), b.getLastAccessTime());
        assertEquals(a.getTime(), b.getTime());
        assertEquals(a.getTimeLocal(), b.getTimeLocal());
        assertEquals(a.getComment(), b.getComment());
        assertArrayEquals(a.getExtra(), b.getExtra());
        assertArrayEquals(a.getCodeSigners(), b.getCodeSigners());
        assertArrayEquals(a.getCertificates(), b.getCertificates());
    }
}
