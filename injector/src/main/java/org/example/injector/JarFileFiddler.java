package org.example.injector;

import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

public class JarFileFiddler implements Iterable<JarFileFiddler.WrappedJarEntry>, AutoCloseable {
    private final JarFile jarFile;
    private final JarOutputStream outputJarStream;

    public JarFileFiddler(final JarFile jarFile) {
        this.jarFile = jarFile;
        this.outputJarStream = null;
    }

    public JarFileFiddler(final JarFile jarFile, JarOutputStream outputJarStream) {
        this.jarFile = jarFile;
        this.outputJarStream = outputJarStream;
    }

    public static JarFileFiddler open(final Path jarFilePath) throws IOException {
        return new JarFileFiddler(new JarFile(jarFilePath.toFile()));
    }

    public static JarFileFiddler open(final Path jarFilePath, final Path outputJarFilePath) throws IOException {
        return new JarFileFiddler(
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
    public JarEntryIterator iterator() {
        return new JarEntryIterator(jarFile, outputJarStream);
    }

    public void close() throws IOException {
        jarFile.close();
        if (outputJarStream != null) {
            outputJarStream.close();
        }
    }

    public static class JarEntryIterator implements Iterator<WrappedJarEntry> {
        private final JarFile jarFile;
        private final JarOutputStream outputJarStream;
        private final Enumeration<JarEntry> enumeration;

        JarEntryIterator(JarFile jarFile, JarOutputStream outputJarStream) {
            this.jarFile = jarFile;
            this.outputJarStream = outputJarStream;
            this.enumeration = jarFile.entries();
        }

        @Override
        public boolean hasNext() {
            return enumeration.hasMoreElements();
        }

        @Override
        public WrappedJarEntry next() {
            return new WrappedJarEntry(enumeration.nextElement(), jarFile, outputJarStream);
        }
    }

    public static class WrappedJarEntry {
        private final JarEntry jarEntry;
        private final JarFile jarFile;
        private final JarOutputStream outputJarStream;
        private boolean hasWrittenToOutputJar = false;

        public WrappedJarEntry(JarEntry jarEntry, JarFile jarFileRef, JarOutputStream outputJarStreamRef) {
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

        public void passOn() throws IOException {
            assertThatOutputJarIsSpecified();
            assertThatEntryHasNotAlreadyBeenWritten();

            InputStream currentEntryStream = jarFile.getInputStream(jarEntry);
            outputJarStream.putNextEntry(jarEntry);
            outputJarStream.write(currentEntryStream.readAllBytes());   // Can be optimized
            outputJarStream.closeEntry();

            hasWrittenToOutputJar = true;
        }

        public void add(ByteBuffer content) throws IOException {
            assertThatOutputJarIsSpecified();
            assertThatEntryHasNotAlreadyBeenWritten();

            outputJarStream.putNextEntry(jarEntry);
            outputJarStream.write(content.array(), content.position(), content.limit());
            // Accessing the underlying array like that may be problematic for some sources
            outputJarStream.closeEntry();

            hasWrittenToOutputJar = true;
        }

        public void add(InputStream in) throws IOException {
            assertThatOutputJarIsSpecified();
            assertThatEntryHasNotAlreadyBeenWritten();

            outputJarStream.putNextEntry(jarEntry);
            outputJarStream.write(in.readAllBytes());   // Can be optimized
            outputJarStream.closeEntry();

            hasWrittenToOutputJar = true;
        }

        // This is a spectacular one...
        public DataOutputStream addOnly() throws IOException {
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
