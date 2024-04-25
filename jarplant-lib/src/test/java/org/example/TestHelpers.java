package org.example;

import org.example.injector.ClassInjectorTests;
import org.example.injector.JarFileFiddler;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

public class TestHelpers {
    public static Path findTestEnvironmentDir(Class<?> testClass) {
        // This perhaps naively exploits the fact that the root resource path will be the `target/test-classes` dir.
        URL testClassesDir = testClass.getClassLoader().getResource("");
        if (testClassesDir == null) {
            throw new RuntimeException("Failed to find resource directory for testing environment.");
        }

        return Path.of(testClassesDir.getPath());
    }

    public static Path createTempJarFileWithClasses(Path baseDir, Path... files) {
        Path tmpFile = null;

        try {
            tmpFile = Files.createTempFile("JarPlantTests-" + UUID.randomUUID(), ".jar");
            JarOutputStream jarWriter = new JarOutputStream(new FileOutputStream(tmpFile.toFile()));

            String manifest = "Manifest-Version: 1.0\nBuild-Jdk-Spec: 17\n";
            JarEntry manifestEntry = new JarEntry("META-INF/MANIFEST.MF");
            jarWriter.putNextEntry(manifestEntry);
            jarWriter.write(manifest.getBytes(StandardCharsets.UTF_8));

            for (Path fileRelativePath : files) {
                Path fileFullPath = baseDir.resolve(fileRelativePath);
                JarEntry classEntry = new JarEntry(fileRelativePath.toString());
                jarWriter.putNextEntry(classEntry);
                jarWriter.write(Files.readAllBytes(fileFullPath));
                jarWriter.closeEntry();
            }

            jarWriter.close();
        } catch (IOException e) {
            throw new RuntimeException("Failed to stage a temporary JAR file for testing.", e);
        } finally {
            // Here's a dirty trick: Register a shutdown hook to delete this temp file at JVM exit
            Path finalTmpFile = tmpFile;
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                if (finalTmpFile != null && Files.exists(finalTmpFile)) {
                    try {
                        Files.delete(finalTmpFile);
                    } catch (IOException ignore) {
                        // Worst case: There will be a file littering the temp folder (which is fine for tests)
                    }
                }
            }));

        }

        return tmpFile;
    }

    public static Path getJarFileFromResourceFolder(String jarFileName) throws IOException {
        URL resource = ClassInjectorTests.class.getClassLoader().getResource(jarFileName);
        if (resource == null) {
            throw new FileNotFoundException("Cannot find '" + jarFileName + "' in resource folder. Have you run `mvn package` in the project root yet?");
        }

        Path jarFilePath;
        try {
            jarFilePath = Path.of(resource.toURI());
        } catch (URISyntaxException e) {
            throw new IOException("Cannot make sense of resource path: Invalid URI: " + resource, e);
        }

        // This should not be necessary but let's make sure
        if (!Files.exists(jarFilePath)) {
            throw new IOException("Cannot make sense of resource path: File does not exist: " + jarFilePath);
        }

        return jarFilePath;
    }

    public static InputStream getRawClassStreamFromJar(Path jarFilePath, String entryFullInternalPath) throws IOException {
        JarFile jarFile = new JarFile(jarFilePath.toFile());
        JarEntry classFileInJar = (JarEntry) jarFile.getEntry(entryFullInternalPath);
        if (classFileInJar == null) {
            throw new FileNotFoundException(entryFullInternalPath);
        }

        return jarFile.getInputStream(classFileInJar);
    }

    public static Map<String, String> hashAllJarContents(Path jarFile) throws IOException {
        Map<String, String> hashes = new HashMap<>();
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Cannot find SHA-256 provider.", e);
        }

        try (JarFileFiddler jar = JarFileFiddler.open(jarFile)) {
            for (JarFileFiddler.WrappedJarEntry entry : jar) {
                String filename = entry.getName();
                byte[] contentDigest = md.digest(entry.getContent().readAllBytes());
                md.reset();
                String hashString = HexFormat.of().formatHex(contentDigest);

                hashes.put(filename, hashString);
            }
        }

        return hashes;
    }

    public static Optional<Integer> findSubArray(byte[] bigArray, byte[] smallArray) {
        for (int i = 0; i <= bigArray.length - smallArray.length; i++) {
            boolean found = true;
            for (int j = 0; j < smallArray.length; j++) {
                if (bigArray[i + j] != smallArray[j]) {
                    found = false;
                    break;
                }
            }
            if (found) {
                return Optional.of(i);
            }
        }

        return Optional.empty();
    }
}
