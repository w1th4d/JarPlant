package io.github.w1th4d.jarplant;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public interface JarFiddler extends Iterable<JarFiddler.Entry> {
    /**
     * Read the entire content of a JAR file into memory.
     *
     * @param jarFilePath Path to existing JAR file
     * @return Instance
     * @throws IOException If unable to read file
     */
    static BufferedJarFiddler buffer(Path jarFilePath) throws IOException {
        List<Entry> readEntries = new ArrayList<>();
        try (JarFile jar = new JarFile(jarFilePath.toFile())) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                byte[] content = jar.getInputStream(entry).readAllBytes();
                readEntries.add(new BufferedJarFiddler.BufferedJarEntry(entry, content));
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
    void write(Path outputFile) throws IOException;

    /**
     * Write the current state to a stream.
     * There are no guarantees that this method will produce the exact binary content as the input file used for
     * <code>read</code> (even when no entries were modified). It's recommended to only overwrite JARs that has
     * actually been modified.
     *
     * @param outputStream An open output stream
     * @throws IOException If failed to write
     */
    void write(OutputStream outputStream) throws IOException;

    /**
     * Add a new entry at the end of the JAR.
     *
     * @param newEntry Entry to add
     * @param contents Entry file content
     * @throws DuplicateEntryException If the entry name already exist
     */
    void addNewEntry(JarEntry newEntry, byte[] contents) throws DuplicateEntryException;

    /**
     * List all entries found in the JAR.
     *
     * @return File names
     */
    List<String> listEntries();

    /**
     * Get a specific entry (file) from the JAR.
     *
     * @param path Full internal path in the JAR. Ex: /META-INF/MANIFEST.MF
     * @return Entry, if it was found
     */
    Optional<Entry> getEntry(String path);

    /**
     * Get all entries found in the JAR.
     * Returned objects are mutable. Any changes to these objects will stick.
     *
     * @return Entries
     */
    List<Entry> getEntries();

    @Override
    ListIterator<Entry> iterator();

    interface Entry {
        /**
         * Get the full file name of this entry.
         */
        String getName();

        /**
         * Get a clone of the underlying JarEntry.
         *
         * @return JarEntry
         */
        JarEntry toJarEntry();

        /**
         * Get the file content of this entry.
         *
         * @return Bytes
         */
        byte[] getContent();

        /**
         * Get the file contents of this entry.
         *
         * @return An open stream
         */
        InputStream getContentStream();

        /**
         * Replace the file contents with new data.
         *
         * @param newContent Data that will replace the content
         */
        void replaceContentWith(byte[] newContent);

        /**
         * Replace the file contents with new data.
         *
         * @param content ByteBuffer that is ready to read
         */
        void replaceContentWith(ByteBuffer content);

        /**
         * Replace the file contents with new data.
         * The input stream will be read until EOF.
         *
         * @param in An open InputStream
         * @throws IOException If failed to read the stream
         */
        void replaceContentWith(InputStream in) throws IOException;
    }
}
