package org.example.injector;

import javassist.bytecode.ClassFile;
import org.example.TestImplantRunner;
import org.example.implants.TestImplant;
import org.junit.Test;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.example.TestHelpers.findTestEnvironmentDir;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class ImplantHandlerTests {
    private final TestImplantRunner runner = new TestImplantRunner();

    @Test
    public void testCreateFor_ClassFile_Success() throws IOException {
        Path testEnv = findTestEnvironmentDir(this.getClass());
        Path classFile = testEnv.resolve("org/example/implants/TestImplant.class");

        ImplantHandler implant = ImplantHandler.createFor(classFile);
        runner.exec(implant.loadFreshRawSpecimen());
    }

    @Test
    public void testFindAndCreateFor_Class_Success() throws IOException, ClassNotFoundException {
        Class<?> clazz = TestImplant.class;

        ImplantHandler implant = ImplantHandler.findAndCreateFor(clazz);
        runner.exec(implant.loadFreshRawSpecimen());
    }

    @Test
    public void testFindAndCreateFor_ClassNameAndPath_Success() throws IOException, ClassNotFoundException {
        String className = TestImplant.class.getName();
        Path classPath = findTestEnvironmentDir(this.getClass());

        ImplantHandler implant = ImplantHandler.findAndCreateFor(className, classPath);
        runner.exec(implant.loadFreshRawSpecimen());
    }

    @Test
    public void testFindAndCreateFor_JarFile_Success() throws IOException, ClassNotFoundException {
        // Arrange
        Path tmpFile = null;
        try {
            tmpFile = Files.createTempFile("TestImplant", UUID.randomUUID().toString());
            JarOutputStream jarWriter = new JarOutputStream(new FileOutputStream(tmpFile.toFile()));

            String manifest = "Manifest-Version: 1.0\n\rBuild-Jdk-Spec: 17\n\r";
            JarEntry manifestEntry = new JarEntry("META-INF/MANIFEST.MF");
            jarWriter.putNextEntry(manifestEntry);
            jarWriter.write(manifest.getBytes(StandardCharsets.UTF_8));

            Path classFile = findTestEnvironmentDir(this.getClass()).resolve("org/example/implants/TestImplant.class");
            JarEntry classEntry = new JarEntry("org/example/implants/TestImplant.class");
            jarWriter.putNextEntry(classEntry);
            jarWriter.write(Files.readAllBytes(classFile));

            jarWriter.close();

            // Act + Assert
            ImplantHandler.findAndCreateFor(TestImplant.class.getName(), tmpFile);
        } catch (IOException e) {
            throw new IOException("Failed to stage a temporary JAR file for testing.", e);
        } finally {
            // Teardown
            if (tmpFile != null && Files.exists(tmpFile)) {
                Files.delete(tmpFile);
            }
        }
    }

    @Test
    public void testGetImplantClassName_FullClassName() throws IOException, ClassNotFoundException {
        ImplantHandler implant = ImplantHandler.findAndCreateFor(TestImplant.class);

        String name = implant.getImplantClassName();

        assertEquals("org.example.implants.TestImplant", name);
    }

    @Test
    public void testGetAvailableConfig_AfterLoaded_CorrectType() throws IOException, ClassNotFoundException {
        ImplantHandler implant = ImplantHandler.findAndCreateFor(TestImplant.class);

        Map<String, ImplantHandler.ConfDataType> availableConfig = implant.getAvailableConfig();
        ImplantHandler.ConfDataType confString = availableConfig.get("CONF_STRING");
        ImplantHandler.ConfDataType confBool = availableConfig.get("CONF_BOOLEAN");
        ImplantHandler.ConfDataType confInt = availableConfig.get("CONF_INT");

        assertNotNull(confString);
        assertNotNull(confBool);
        assertNotNull(confInt);
        assertEquals(ImplantHandler.ConfDataType.STRING, confString);
        assertEquals(ImplantHandler.ConfDataType.BOOLEAN, confBool);
        assertEquals(ImplantHandler.ConfDataType.INT, confInt);
    }

    @Test
    public void testSetConfig_CorrectProp_Success() throws IOException, ClassNotFoundException, ImplantConfigException {
        ImplantHandler implant = ImplantHandler.findAndCreateFor(TestImplant.class);

        implant.setConfig("CONF_STRING", "Modified");
        implant.setConfig("CONF_BOOLEAN", true);
        implant.setConfig("CONF_INT", 2);
        ClassFile implantClass = implant.loadFreshConfiguredSpecimen();

        String ret = runner.exec(implantClass);
        assertEquals("CONF_STRING=\"Modified\";CONF_BOOLEAN=true;CONF_INT=2;", ret);
    }

    @Test
    public void testSetConfig_PartialOverride_OnlyChangeAffectedField() throws IOException, ClassNotFoundException, ImplantConfigException {
        ImplantHandler implant = ImplantHandler.findAndCreateFor(TestImplant.class);

        implant.setConfig("CONF_BOOLEAN", true);
        ClassFile implantClass = implant.loadFreshConfiguredSpecimen();

        String ret = runner.exec(implantClass);
        assertEquals("CONF_STRING=\"Original\";CONF_BOOLEAN=true;CONF_INT=1;", ret);
    }

    @Test
    public void testSetConfig_BulkConfigOverride_AddAllOverrides() throws IOException, ClassNotFoundException, ImplantConfigException {
        ImplantHandler implant = ImplantHandler.findAndCreateFor(TestImplant.class);
        Map<String, Object> bulk = new HashMap<>();
        bulk.put("CONF_STRING", "Modified");
        bulk.put("CONF_BOOLEAN", true);
        bulk.put("CONF_INT", 2);

        implant.setConfig(bulk);
        ClassFile implantClass = implant.loadFreshConfiguredSpecimen();

        String ret = runner.exec(implantClass);
        assertEquals("CONF_STRING=\"Modified\";CONF_BOOLEAN=true;CONF_INT=2;", ret);
    }

    @Test
    public void testSetConfig_PartialBulkConfOverride_AddOnlyAffectedField() throws IOException, ClassNotFoundException, ImplantConfigException {
        ImplantHandler implant = ImplantHandler.findAndCreateFor(TestImplant.class);
        Map<String, Object> bulk = new HashMap<>();
        bulk.put("CONF_BOOLEAN", true);

        implant.setConfig(bulk);
        ClassFile implantClass = implant.loadFreshConfiguredSpecimen();

        String ret = runner.exec(implantClass);
        assertEquals("CONF_STRING=\"Original\";CONF_BOOLEAN=true;CONF_INT=1;", ret);
    }

    @Test(expected = ImplantConfigException.class)
    public void testSetConfig_InvalidDataType_ThrowException() throws IOException, ClassNotFoundException, ImplantConfigException {
        ImplantHandler implant = ImplantHandler.findAndCreateFor(TestImplant.class);

        implant.setConfig("CONF_BOOLEAN", 2);
        implant.loadFreshConfiguredSpecimen();
    }

    @Test
    public void testSetConfig_StringValues_ConvertFromString() throws IOException, ClassNotFoundException, ImplantConfigException {
        ImplantHandler implant = ImplantHandler.findAndCreateFor(TestImplant.class);

        implant.setConfig("CONF_BOOLEAN", "true");
        implant.setConfig("CONF_INT", "2");
        ClassFile implantClass = implant.loadFreshConfiguredSpecimen();

        String ret = runner.exec(implantClass);
        assertEquals("CONF_STRING=\"Original\";CONF_BOOLEAN=true;CONF_INT=2;", ret);
    }

    @Test(expected = ImplantConfigException.class)
    public void testSetConfig_InvalidBooleanString_ThrowException() throws IOException, ClassNotFoundException, ImplantConfigException {
        ImplantHandler implant = ImplantHandler.findAndCreateFor(TestImplant.class);

        implant.setConfig("CONF_BOOLEAN", "yes");
        implant.loadFreshConfiguredSpecimen();
    }

    @Test(expected = ImplantConfigException.class)
    public void testSetConfig_InvalidIntString_ThrowException() throws IOException, ClassNotFoundException, ImplantConfigException {
        ImplantHandler implant = ImplantHandler.findAndCreateFor(TestImplant.class);

        implant.setConfig("CONF_INT", "1.0");
        implant.loadFreshConfiguredSpecimen();
    }
}
