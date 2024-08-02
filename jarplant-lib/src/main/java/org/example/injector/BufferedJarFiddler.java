package org.example.injector;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipException;

/**
 * Utility for modifying contents of a JAR file.
 * <p>This class will read the entire contents of a JAR file and buffer it into memory.
 * Use the iterator to go through all entries and replace its content as appropriate.</p>
 * <p>The iterator is thread safe as it's backed by a <copy>CopyOnWriteArrayList</copy>, but
 * modifying the content of an entry is not (by design).
 * This class is not designed to be used by multithreaded code.</p>
 * <p>All methods of this class will operate upon the buffered state in memory.
 * Use the <code>write</code> methods to persist the state to disk (or elsewhere).</p>
 * <p>Example:</p>
 * <code>
 * BufferedJarFiddler fiddler = BufferedJarFiddler.read(jarFilePath);
 * for (BufferedJarFiddler.BufferedJarEntry entry : fiddler) {
 * if (isInteresting(entry)) {
 * entry.replaceContentWith(somethingElse);
 * }
 * }
 * subject.write(jarFilePath);
 * </code>
 */
public class BufferedJarFiddler implements Iterable<BufferedJarFiddler.BufferedJarEntry> {
    private final List<BufferedJarEntry> entries;
    private final Set<String> cachedEntryNames;

    BufferedJarFiddler(List<BufferedJarEntry> entries) {
        this.entries = new CopyOnWriteArrayList<>(entries);
        this.cachedEntryNames = new HashSet<>(entries.size());
        for (BufferedJarEntry entry : this.entries) {
            this.cachedEntryNames.add(entry.getName());
        }
    }

    /**
     * Read the entire content of a JAR file into memory.
     *
     * @param jarFilePath Path to existing JAR file
     * @return Instance
     * @throws IOException If unable to read file
     */
    public static BufferedJarFiddler read(Path jarFilePath) throws IOException {
        List<BufferedJarEntry> readEntries = new ArrayList<>();
        try (JarFile jar = new JarFile(jarFilePath.toFile())) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                byte[] content = jar.getInputStream(entry).readAllBytes();
                readEntries.add(new BufferedJarEntry(entry, content));
            }
        }
        return new BufferedJarFiddler(readEntries);
    }

    /**
     * Write the current state to a file.
     * The output file can be the same as the input file.
     *
     * @param outputFile File to create or overwrite
     * @throws IOException If unable to create or write file
     */
    public void write(Path outputFile) throws IOException {
        try (OutputStream outputStream = Files.newOutputStream(outputFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            write(outputStream);
        }
    }

    /**
     * Write the current state to a stream.
     * There are no guarantees that this method will produce the exact binary content as the input file used for
     * <code>read</code> (even when no entries were modified). It's recommended to only overwrite JARs that has
     * actually been modified.
     *
     * @param outputStream An open output stream
     * @throws IOException If failed to write
     */
    public void write(OutputStream outputStream) throws IOException {
        /*
         * JarOutputStream.putNextEntry() will add a magic number to the "extra" metadata field
         * of the first entry in the JAR. It seems that JARs from Maven may not contain these.
         * This may result in the metadata being slightly modified for the first entry, despite
         * it being untouched. This is true even if an entire JAR is read and written by the fiddler
         * without having any modified entries.
         * ZipOutputStream could be used instead of JarOutputStream to leave this metadata untouched.
         * However, we'll stick with the JarOutputStream to be sure.
         */
        try (JarOutputStream jarOutputStream = new JarOutputStream(outputStream)) {
            for (BufferedJarEntry entry : entries) {
                jarOutputStream.putNextEntry(entry.metadata);
                jarOutputStream.write(entry.content);
                jarOutputStream.closeEntry();
            }
        }
    }

    /**
     * Add a new entry at the end of the JAR.
     *
     * @param newEntry Entry to add
     * @param contents Entry file content
     * @throws ZipException If the entry name already exist
     */
    public void addNewEntry(JarEntry newEntry, byte[] contents) throws ZipException {
        if (cachedEntryNames.contains(newEntry.getName())) {
            throw new ZipException("Entry " + newEntry.getName() + " already exist.");
        }

        entries.add(new BufferedJarEntry(newEntry, contents));
        cachedEntryNames.add(newEntry.getName());
    }

    /**
     * List all entries found in the JAR.
     *
     * @return File names
     */
    public List<String> listEntries() {
        List<String> results = new ArrayList<>(entries.size());
        for (BufferedJarEntry entry : entries) {
            results.add(entry.metadata.getName());
        }
        return Collections.unmodifiableList(results);
    }

    /**
     * Get a specific entry (file) from the JAR.
     *
     * @param path Full internal path in the JAR. Ex: /META-INF/MANIFEST.MF
     * @return Entry, if it was found
     */
    public Optional<BufferedJarEntry> getEntry(String path) {
        for (BufferedJarEntry entry : entries) {
            if (entry.metadata.getName().equals(path)) {
                return Optional.of(entry);
            }
        }
        return Optional.empty();
    }

    /**
     * Get all entries found in the JAR.
     * Returned objects are mutable. Any changes to these objects will stick.
     *
     * @return Entries
     */
    public List<BufferedJarEntry> getEntries() {
        return entries;
    }

    @Override
    public ListIterator<BufferedJarEntry> iterator() {
        return entries.listIterator();
    }

    /**
     * Holds both the JarEntry (metadata) and actual file contents.
     * This is suprizingly revolutionary in comparison to the native API for handling JAR files.
     */
    public static class BufferedJarEntry {
        private final JarEntry metadata;
        private byte[] content;

        public BufferedJarEntry(JarEntry metadata, byte[] content) {
            this.metadata = metadata;
            this.content = content;
        }

        /**
         * Get the full file name of this entry.
         */
        public String getName() {
            return metadata.getName();
        }

        /**
         * Get a clone of the underlying JarEntry.
         *
         * @return JarEntry
         */
        public JarEntry toJarEntry() {
            return (JarEntry) metadata.clone();
        }

        /**
         * Get the file content of this entry.
         *
         * @return Bytes
         */
        public byte[] getContent() {
            return content;
        }

        /**
         * Get the file contents of this entry.
         *
         * @return An open stream
         */
        public InputStream getContentStream() {
            return new ByteArrayInputStream(content);
        }

        /**
         * Replace the file contents with new data.
         *
         * @param newContent Data that will replace the content
         */
        public void replaceContentWith(byte[] newContent) {
            this.content = newContent;
        }

        /**
         * Replace the file contents with new data.
         *
         * @param content ByteBuffer that is ready to read
         */
        public void replaceContentWith(ByteBuffer content) {
            byte[] bytes = new byte[content.remaining()];
            content.get(bytes);
            replaceContentWith(bytes);
        }

        /**
         * Replace the file contents with new data.
         * The input stream will be read until EOF.
         *
         * @param in An open InputStream
         * @throws IOException If failed to read the stream
         */
        public void replaceContentWith(InputStream in) throws IOException {
            byte[] bytes = in.readAllBytes();
            replaceContentWith(bytes);
        }

        @Override
        public String toString() {
            return metadata.getName();
        }
    }
}
