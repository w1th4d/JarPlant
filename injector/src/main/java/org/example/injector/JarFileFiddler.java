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

        public void copy() throws IOException {
            assertThatOutputJarIsSpecified();
            InputStream currentEntryStream = jarFile.getInputStream(jarEntry);
            outputJarStream.putNextEntry(jarEntry);
            outputJarStream.write(currentEntryStream.readAllBytes());   // Can be optimized
            outputJarStream.closeEntry();
        }

        public void add(ByteBuffer content) throws IOException {
            assertThatOutputJarIsSpecified();
            outputJarStream.putNextEntry(jarEntry);
            outputJarStream.write(content.array(), content.position(), content.limit());
            // Accessing the underlying array like that may be problematic for some sources
            outputJarStream.closeEntry();
        }

        public void add(InputStream in) throws IOException {
            assertThatOutputJarIsSpecified();
            outputJarStream.putNextEntry(jarEntry);
            outputJarStream.write(in.readAllBytes());   // Can be optimized
            outputJarStream.closeEntry();
        }

        // This is a spectacular one...
        public DataOutputStream addOnly() throws IOException {
            assertThatOutputJarIsSpecified();
            outputJarStream.putNextEntry(jarEntry);
            return new DataOutputStream(outputJarStream);
            // The next invocation of putNextEntry() will close the previous entry
        }

        private void assertThatOutputJarIsSpecified() throws IllegalStateException {
            if (outputJarStream == null) {
                throw new IllegalStateException("Not using any output JAR file.");
            }
        }
    }
}
