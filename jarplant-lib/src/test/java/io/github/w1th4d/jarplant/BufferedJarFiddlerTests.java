package io.github.w1th4d.jarplant;

import javassist.bytecode.ClassFile;
import javassist.bytecode.DuplicateMemberException;
import javassist.bytecode.FieldInfo;
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

public class BufferedJarFiddlerTests {
    private Path testJar;
    private Path outputJar;
    private final Set<String> expectedEntries = Set.of(
            "META-INF/",
            "META-INF/MANIFEST.MF",
            "io/",
            "io/github/",
            "io/github/w1th4d/",
            "io/github/w1th4d/jarplant/",
            "io/github/w1th4d/jarplant/test/",
            "META-INF/maven/",
            "META-INF/maven/io.github.w1th4d.jarplant/",
            "META-INF/maven/io.github.w1th4d.jarplant/test-app-pojo/",
            "io/github/w1th4d/jarplant/test/Main.class",
            "io/github/w1th4d/jarplant/test/Lib.class",
            "META-INF/maven/io.github.w1th4d.jarplant/test-app-pojo/pom.xml",
            "META-INF/maven/io.github.w1th4d.jarplant/test-app-pojo/pom.properties"
    );

    @Before
    public void configureLogger() {
        TestHelpers.configureLogger();
    }

    @Before
    public void getTestJar() throws IOException {
        Path testEnv = TestHelpers.findTestEnvironmentDir(this.getClass());
        Path testJarPath = testEnv.resolve("test-app-pojo-with-debug.jar");
        if (Files.notExists(testJarPath)) {
            throw new RuntimeException("Cannot find test JAR: " + testJarPath + ". Have you tried running `mvn package` (at the parent level)?");
        }

        Path tempFile = Files.createTempFile("test-app-", UUID.randomUUID() + ".jar");
        Files.write(tempFile, Files.readAllBytes(testJarPath));

        this.testJar = tempFile;
    }

    @Before
    public void createOutputJar() throws IOException {
        outputJar = Files.createTempFile("BufferedJarFiddlerTests-", UUID.randomUUID() + ".jar");
    }

    @After
    public void removeTempTestJar() throws IOException {
        Files.delete(testJar);
    }

    @After
    public void removeOutputJar() throws IOException {
        Files.delete(outputJar);
    }

    @Test
    public void testIterator_OnlyRead_FoundAllClasses() throws IOException {
        // Arrange
        Set<String> entriesFoundInJar = new HashSet<>(expectedEntries.size());

        // Act
        BufferedJarFiddler subject = JarFiddler.buffer(testJar);
        for (JarFiddler.Entry entry : subject) {
            entriesFoundInJar.add(entry.getName());
        }

        // Assert
        assertEquals("Found all files in JAR.", expectedEntries, entriesFoundInJar);
    }

    @Test
    public void testIterator_CopyWholeJar_AllClassesInOutput() throws IOException {
        // Arrange
        Set<String> entriesFoundInJar = new HashSet<>(expectedEntries.size());

        // Act
        BufferedJarFiddler subject = JarFiddler.buffer(testJar);
        for (JarFiddler.Entry entry : subject) {
            entriesFoundInJar.add(entry.getName());
        }
        subject.write(outputJar);

        // Assert
        Set<String> entriesInOutputJar = readAllJarEntries(outputJar).stream()
                .map(ZipEntry::getName)
                .collect(Collectors.toSet());
        assertEquals("All files were forwarded to output JAR.", entriesInOutputJar, entriesFoundInJar);
    }

    @Test
    public void testIterator_CopyWholeJar_CopiedEntriesMetadata() throws IOException {
        // Arrange
        Set<JarEntry> entriesFoundInJar = new HashSet<>(expectedEntries.size());

        // Act
        BufferedJarFiddler subject = JarFiddler.buffer(testJar);
        for (JarFiddler.Entry entry : subject) {
            entriesFoundInJar.add(entry.toJarEntry());
        }
        subject.write(outputJar);

        // Assert: Make sure all metadata is intact
        try (JarFile outputJarFile = new JarFile(outputJar.toFile())) {
            for (JarEntry entryFoundInJar : entriesFoundInJar) {
                JarEntry correspondingOutputEntry = (JarEntry) outputJarFile.getEntry(entryFoundInJar.getName());
                assertAllJarEntryMetadataEquals(entryFoundInJar, correspondingOutputEntry);
            }
        }
    }

    @Test
    public void testIterator_ModifyEntry_OnlyEntryModified() throws IOException {
        // Arrange
        String nameOfMain = "io/github/w1th4d/jarplant/test/Main.class";
        HashMap<String, Long> originalFileHashes = new HashMap<>();

        // Act: Go through the entire JAR and modify Main.class
        BufferedJarFiddler subject = JarFiddler.buffer(testJar);
        for (JarFiddler.Entry entry : subject) {
            originalFileHashes.put(entry.getName(), entry.toJarEntry().getCrc());

            if (entry.getName().equals(nameOfMain)) {
                ClassFile mainClass = new ClassFile(new DataInputStream(entry.getContentStream()));
                try {
                    mainClass.addField(new FieldInfo(mainClass.getConstPool(), "ADDED_FIELD", "I"));
                } catch (DuplicateMemberException e) {
                    throw new RuntimeException(e);
                }

                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                mainClass.write(new DataOutputStream(buffer));
                entry.replaceContentWith(buffer.toByteArray()); // Actual act
            }
        }
        subject.write(outputJar);

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
    public void testEntryReplacement_ReplaceEntryFromByteArray_EntryReplaced() throws IOException {
        // Arrange
        String nameOfMain = "io/github/w1th4d/jarplant/test/Main.class";
        byte[] replacementData = new byte[]{1, 2, 3, 4, 5};

        // Act
        BufferedJarFiddler subject = JarFiddler.buffer(testJar);
        subject.getEntry(nameOfMain).orElseThrow().replaceContentWith(replacementData);
        subject.write(outputJar);

        // Assert
        try (JarFile output = new JarFile(outputJar.toFile())) {
            ZipEntry mainEntry = output.getEntry(nameOfMain);
            assertNotNull("Entry exist.", mainEntry);

            long originalHash = crc32(ByteBuffer.wrap(replacementData));
            long foundHash = mainEntry.getCrc();
            assertEquals("Entry CRC is modified.", originalHash, foundHash);

            byte[] mainEntryContent = output.getInputStream(mainEntry).readAllBytes();
            assertNotEquals("Actual content differ.", replacementData, mainEntryContent);
        }
    }

    @Test
    public void testEntryReplacement_ReplaceEntryFromBuffer_EntryReplaced() throws IOException {
        // Arrange
        String nameOfMain = "io/github/w1th4d/jarplant/test/Main.class";
        ByteBuffer replacementData = ByteBuffer.wrap(new byte[]{1, 2, 3, 4, 5});

        // Act
        BufferedJarFiddler subject = JarFiddler.buffer(testJar);
        subject.getEntry(nameOfMain).orElseThrow().replaceContentWith(replacementData);
        subject.write(outputJar);

        // Assert
        try (JarFile output = new JarFile(outputJar.toFile())) {
            ZipEntry mainEntry = output.getEntry(nameOfMain);
            assertNotNull("Entry exist.", mainEntry);

            long originalHash = crc32(replacementData.rewind());
            long foundHash = mainEntry.getCrc();
            assertEquals("Entry CRC is modified.", originalHash, foundHash);

            ByteBuffer mainEntryContent = ByteBuffer.wrap(output.getInputStream(mainEntry).readAllBytes());
            assertNotEquals("Actual content differ.", replacementData, mainEntryContent);
        }
    }

    @Test
    public void testEntryReplacement_ReplaceEntryFromStream_EntryReplaced() throws IOException {
        // Arrange
        String nameOfMain = "io/github/w1th4d/jarplant/test/Main.class";
        byte[] replacementData = new byte[]{1, 2, 3, 4, 5};
        InputStream replacementStream = new ByteArrayInputStream(replacementData);

        // Act
        BufferedJarFiddler subject = JarFiddler.buffer(testJar);
        subject.getEntry(nameOfMain).orElseThrow().replaceContentWith(replacementStream);
        subject.write(outputJar);

        // Assert
        try (JarFile output = new JarFile(outputJar.toFile())) {
            ZipEntry mainEntry = output.getEntry(nameOfMain);
            assertNotNull("Entry exist.", mainEntry);

            long originalHash = crc32(ByteBuffer.wrap(replacementData));
            long foundHash = mainEntry.getCrc();
            assertEquals("Entry CRC is modified.", originalHash, foundHash);

            byte[] mainEntryContent = output.getInputStream(mainEntry).readAllBytes();
            assertNotEquals("Actual content differ.", replacementData, mainEntryContent);
        }
    }

    @Test
    public void testWrite_OpenStream_StreamLeftOpen() throws IOException {
        // Arrange
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        // Act
        JarFiddler.buffer(testJar).write(buffer);

        // Assert
        try {
            buffer.write(new byte[]{1, 2, 3});
        } catch (IOException e) {
            fail("Unable to continue writing to stream (stream closed).");
        }
    }

    @Test
    public void testWrite_SameFile_Fine() throws IOException {
        // Act + Assert
        JarFiddler.buffer(testJar).write(testJar);
    }

    /*
     * Future feature: Produce the exact same output as input when no entries are modified.
     */
    @Test
    @Ignore
    public void testWrite_SameFile_ExactSameBinaryData() throws IOException {
        // Arrange
        byte[] rawDataBefore = Files.readAllBytes(testJar);

        // Act
        JarFiddler.buffer(testJar).write(testJar);

        // Assert
        byte[] rawDataAfter = Files.readAllBytes(testJar);
        assertArrayEquals("Jar is the exact same", rawDataBefore, rawDataAfter);
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
        //assertArrayEquals(a.getExtra(), b.getExtra());
        assertArrayEquals(a.getCodeSigners(), b.getCodeSigners());
        assertArrayEquals(a.getCertificates(), b.getCertificates());
    }
}
