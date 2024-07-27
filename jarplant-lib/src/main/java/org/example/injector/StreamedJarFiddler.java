package org.example.injector;

import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

public class StreamedJarFiddler implements Iterable<StreamedJarFiddler.StreamedJarEntry>, AutoCloseable {
    private final JarFile jarFile;
    private final JarOutputStream outputJarStream;

    public StreamedJarFiddler(final JarFile jarFile) {
        this.jarFile = jarFile;
        this.outputJarStream = null;
    }

    public StreamedJarFiddler(final JarFile jarFile, JarOutputStream outputJarStream) {
        this.jarFile = jarFile;
        this.outputJarStream = outputJarStream;
    }

    public static StreamedJarFiddler open(final Path jarFilePath) throws IOException {
        return new StreamedJarFiddler(new JarFile(jarFilePath.toFile()));
    }

    public static StreamedJarFiddler open(final Path jarFilePath, final Path outputJarFilePath) throws IllegalArgumentException, IOException {
        if (Files.exists(outputJarFilePath)) {
            if (outputJarFilePath.toRealPath().equals(jarFilePath.toRealPath())) {
                throw new IllegalArgumentException("Output JAR is the same as input JAR. This is not yet supported.");
            }
        }

        return new StreamedJarFiddler(
                new JarFile(jarFilePath.toFile()),
                new JarOutputStream(new FileOutputStream(outputJarFilePath.toFile()))
        );
    }

    public JarFile getJarFile() {
        return jarFile;
    }

    public DataOutputStream addNewEntry(final JarEntry newEntry) throws IOException {
        if (outputJarStream == null) {
            throw new IllegalStateException("Not using any output JAR file.");
        }
        outputJarStream.putNextEntry(newEntry);
        return new DataOutputStream(outputJarStream);
    }

    @Override
    public StreamedJarEntryIterator iterator() {
        return new StreamedJarEntryIterator(jarFile, outputJarStream);
    }

    public void close() throws IOException {
        jarFile.close();
        if (outputJarStream != null) {
            outputJarStream.close();
        }
    }

    public static class StreamedJarEntryIterator implements Iterator<StreamedJarEntry> {
        private final JarFile jarFile;
        private final JarOutputStream outputJarStream;
        private final Enumeration<JarEntry> enumeration;

        StreamedJarEntryIterator(JarFile jarFile, JarOutputStream outputJarStream) {
            this.jarFile = jarFile;
            this.outputJarStream = outputJarStream;
            this.enumeration = jarFile.entries();
        }

        @Override
        public boolean hasNext() {
            return enumeration.hasMoreElements();
        }

        @Override
        public StreamedJarEntry next() {
            return new StreamedJarEntry(enumeration.nextElement(), jarFile, outputJarStream);
        }
    }

    public static class StreamedJarEntry {
        private final JarEntry jarEntry;
        private final JarFile jarFile;
        private final JarOutputStream outputJarStream;
        private boolean hasWrittenToOutputJar = false;

        StreamedJarEntry(JarEntry jarEntry, JarFile jarFileRef, JarOutputStream outputJarStreamRef) {
            this.jarEntry = jarEntry;
            this.jarFile = jarFileRef;
            this.outputJarStream = outputJarStreamRef;
        }

        public JarEntry getEntry() {
            return jarEntry;
        }

        public InputStream getContent() throws IOException {
            return jarFile.getInputStream(jarEntry);
        }

        public void forward() throws IOException {
            assertThatOutputJarIsSpecified();
            assertThatEntryHasNotAlreadyBeenWritten();

            InputStream currentEntryStream = jarFile.getInputStream(jarEntry);
            outputJarStream.putNextEntry(jarEntry);
            outputJarStream.write(currentEntryStream.readAllBytes());   // Can be optimized
            outputJarStream.closeEntry();

            hasWrittenToOutputJar = true;
        }

        public void replaceContentWith(ByteBuffer content) throws IOException {
            assertThatOutputJarIsSpecified();
            assertThatEntryHasNotAlreadyBeenWritten();

            outputJarStream.putNextEntry(jarEntry);
            outputJarStream.write(content.array(), content.position(), content.limit());
            // Accessing the underlying array like that may be problematic for some sources
            outputJarStream.closeEntry();

            hasWrittenToOutputJar = true;
        }

        public void replaceContentWith(InputStream in) throws IOException {
            assertThatOutputJarIsSpecified();
            assertThatEntryHasNotAlreadyBeenWritten();

            outputJarStream.putNextEntry(jarEntry);
            outputJarStream.write(in.readAllBytes());   // Can be optimized
            outputJarStream.closeEntry();

            hasWrittenToOutputJar = true;
        }

        // This is a spectacular one...
        public DataOutputStream replaceContentByStream() throws IOException {
            assertThatOutputJarIsSpecified();
            assertThatEntryHasNotAlreadyBeenWritten();

            outputJarStream.putNextEntry(jarEntry);
            hasWrittenToOutputJar = true;
            return new DataOutputStream(outputJarStream);
            // The next invocation of putNextEntry() will close the previous entry
        }

        public String getName() {
            return jarEntry.getName();
        }

        private void assertThatOutputJarIsSpecified() throws IllegalStateException {
            if (outputJarStream == null) {
                throw new IllegalStateException("Not using any output JAR file.");
            }
        }

        private void assertThatEntryHasNotAlreadyBeenWritten() throws IllegalStateException {
            if (hasWrittenToOutputJar) {
                throw new IllegalStateException("JAR entry has already been written to output JAR.");
            }
        }
    }
}
