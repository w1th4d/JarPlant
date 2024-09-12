package io.github.w1th4d.jarplant;

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
import java.util.jar.JarOutputStream;

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
public class BufferedJarFiddler implements JarFiddler {
    private final List<Entry> entries;
    private final Set<String> cachedEntryNames;

    BufferedJarFiddler(List<Entry> entries) {
        this.entries = new CopyOnWriteArrayList<>(entries);
        this.cachedEntryNames = new HashSet<>(entries.size());
        for (Entry entry : this.entries) {
            this.cachedEntryNames.add(entry.getName());
        }
    }

    @Override
    public void write(Path outputFile) throws IOException {
        try (OutputStream outputStream = Files.newOutputStream(outputFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            write(outputStream);
        }
    }

    @Override
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
            for (Entry entry : entries) {
                jarOutputStream.putNextEntry(entry.toJarEntry());
                jarOutputStream.write(entry.getContent());
                jarOutputStream.closeEntry();
            }
        }
    }

    @Override
    public void addNewEntry(JarEntry newEntry, byte[] contents) throws DuplicateEntryException {
        if (cachedEntryNames.contains(newEntry.getName())) {
            throw new DuplicateEntryException(newEntry.getName());
        }

        entries.add(new BufferedJarEntry(newEntry, contents));
        cachedEntryNames.add(newEntry.getName());
    }

    @Override
    public List<String> listEntries() {
        List<String> results = new ArrayList<>(entries.size());
        for (Entry entry : entries) {
            results.add(entry.getName());
        }
        return Collections.unmodifiableList(results);
    }

    @Override
    public Optional<Entry> getEntry(String path) {
        for (Entry entry : entries) {
            if (entry.getName().equals(path)) {
                return Optional.of(entry);
            }
        }
        return Optional.empty();
    }

    @Override
    public List<Entry> getEntries() {
        return Collections.unmodifiableList(entries);
    }

    @Override
    public ListIterator<Entry> iterator() {
        return entries.listIterator();
    }

    /**
     * Holds both the JarEntry (metadata) and actual file contents.
     * This is suprizingly revolutionary in comparison to the native API for handling JAR files.
     */
    public static class BufferedJarEntry implements Entry {
        private final JarEntry metadata;
        private byte[] content;

        public BufferedJarEntry(JarEntry metadata, byte[] content) {
            this.metadata = metadata;
            this.content = content;
        }

        @Override
        public String getName() {
            return metadata.getName();
        }

        @Override
        public JarEntry toJarEntry() {
            return (JarEntry) metadata.clone();
        }

        @Override
        public byte[] getContent() {
            return content;
        }

        @Override
        public InputStream getContentStream() {
            return new ByteArrayInputStream(content);
        }

        @Override
        public void replaceContentWith(byte[] newContent) {
            this.content = newContent;
        }

        @Override
        public void replaceContentWith(ByteBuffer content) {
            byte[] bytes = new byte[content.remaining()];
            content.get(bytes);
            replaceContentWith(bytes);
        }

        @Override
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
