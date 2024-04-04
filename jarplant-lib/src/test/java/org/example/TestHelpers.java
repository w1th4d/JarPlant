package org.example;

import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.jar.JarEntry;
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

    public static Path createTempJarFile(Path baseDir, Path... files) {
        Path tmpFile = null;

        try {
            tmpFile = Files.createTempFile("TestImplant-" + UUID.randomUUID(), ".jar");
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
}