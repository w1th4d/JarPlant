package org.example.injector;

import javassist.bytecode.ClassFile;
import org.example.TestImplantRunner;
import org.example.implants.DummyTestClassImplant;
import org.example.implants.TestClassImplant;
import org.junit.Before;
import org.junit.Test;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import static org.example.TestHelpers.findTestEnvironmentDir;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class ImplantHandlerTests {
    private TestImplantRunner runner;

    @Before
    public void getImplantRunner() {
        this.runner = new TestImplantRunner();
    }

    @Test
    public void testCreateFor_ClassFile_Success() throws IOException {
        Path testEnv = findTestEnvironmentDir(this.getClass());
        Path classFile = testEnv.resolve("org/example/implants/DummyTestClassImplant.class");

        ImplantHandler implant = ImplantHandlerImpl.createFor(classFile);
        runner.load(implant.loadFreshRawSpecimen());
    }

    @Test
    public void testFindAndCreateFor_Class_Success() throws IOException, ClassNotFoundException {
        Class<?> clazz = TestClassImplant.class;

        ImplantHandler implant = ImplantHandlerImpl.findAndCreateFor(clazz);
        runner.load(implant.loadFreshRawSpecimen());
    }

    @Test
    public void testFindAndCreateFor_ClassNameAndPath_Success() throws IOException, ClassNotFoundException {
        String className = DummyTestClassImplant.class.getName();
        Path classPath = findTestEnvironmentDir(this.getClass());

        ImplantHandler implant = ImplantHandlerImpl.findAndCreateFor(className, classPath);
        runner.load(implant.loadFreshRawSpecimen());
    }

    @Test
    public void testFindAndCreateFor_JarFile_Success() throws IOException, ClassNotFoundException {
        // Arrange
        Path tmpFile = null;
        try {
            tmpFile = Files.createTempFile("TestImplant", UUID.randomUUID().toString());
            JarOutputStream jarWriter = new JarOutputStream(new FileOutputStream(tmpFile.toFile()));

            JarEntry manifestEntry = new JarEntry("META-INF/MANIFEST.MF");
            Manifest manifest = new Manifest();
            manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
            jarWriter.putNextEntry(manifestEntry);
            manifest.write(jarWriter);

            Path classFile = findTestEnvironmentDir(this.getClass()).resolve("org/example/implants/DummyTestClassImplant.class");
            JarEntry classEntry = new JarEntry("org/example/implants/DummyTestClassImplant.class");
            jarWriter.putNextEntry(classEntry);
            jarWriter.write(Files.readAllBytes(classFile));

            jarWriter.close();

            // Act + Assert
            ImplantHandlerImpl.findAndCreateFor(DummyTestClassImplant.class.getName(), tmpFile);
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
        ImplantHandler implant = ImplantHandlerImpl.findAndCreateFor(DummyTestClassImplant.class);

        String name = implant.getImplantClassName();

        assertEquals("org.example.implants.DummyTestClassImplant", name);
    }

    @Test
    public void testGetAvailableConfig_AfterLoaded_CorrectType() throws IOException, ClassNotFoundException {
        ImplantHandler implant = ImplantHandlerImpl.findAndCreateFor(TestClassImplant.class);

        Map<String, ImplantHandlerImpl.ConfDataType> availableConfig = implant.getAvailableConfig();
        ImplantHandlerImpl.ConfDataType confString = availableConfig.get("CONF_STRING");
        ImplantHandlerImpl.ConfDataType confBool = availableConfig.get("CONF_BOOLEAN");
        ImplantHandlerImpl.ConfDataType confInt = availableConfig.get("CONF_INT");

        assertNotNull(confString);
        assertNotNull(confBool);
        assertNotNull(confInt);
        assertEquals(ImplantHandlerImpl.ConfDataType.STRING, confString);
        assertEquals(ImplantHandlerImpl.ConfDataType.BOOLEAN, confBool);
        assertEquals(ImplantHandlerImpl.ConfDataType.INT, confInt);
    }

    @Test
    public void testSetConfig_CorrectProp_Success() throws IOException, ClassNotFoundException, ImplantConfigException {
        ImplantHandler implant = ImplantHandlerImpl.findAndCreateFor(TestClassImplant.class);

        implant.setConfig("CONF_STRING", "Modified");
        implant.setConfig("CONF_BOOLEAN", true);
        implant.setConfig("CONF_INT", 2);
        ClassFile implantClass = implant.loadFreshConfiguredSpecimen();

        Class<?> loadedImplantClass = runner.load(implantClass);
        String ret = runner.runMethod(loadedImplantClass, "init", String.class);
        assertEquals("CONF_STRING=\"Modified\";CONF_BOOLEAN=true;CONF_INT=2;", ret);
    }

    @Test
    public void testSetConfig_PartialOverride_OnlyChangeAffectedField() throws IOException, ClassNotFoundException, ImplantConfigException {
        ImplantHandler implant = ImplantHandlerImpl.findAndCreateFor(TestClassImplant.class);

        implant.setConfig("CONF_BOOLEAN", true);
        ClassFile implantClass = implant.loadFreshConfiguredSpecimen();

        Class<?> loadedImplantClass = runner.load(implantClass);
        String ret = runner.runMethod(loadedImplantClass, "init", String.class);
        assertEquals("CONF_STRING=\"Original\";CONF_BOOLEAN=true;CONF_INT=1;", ret);
    }

    @Test
    public void testSetConfig_BulkConfigOverride_AddAllOverrides() throws IOException, ClassNotFoundException, ImplantConfigException {
        ImplantHandler implant = ImplantHandlerImpl.findAndCreateFor(TestClassImplant.class);
        Map<String, Object> bulk = new HashMap<>();
        bulk.put("CONF_STRING", "Modified");
        bulk.put("CONF_BOOLEAN", true);
        bulk.put("CONF_INT", 2);

        implant.setConfig(bulk);
        ClassFile implantClass = implant.loadFreshConfiguredSpecimen();

        Class<?> loadedImplantClass = runner.load(implantClass);
        String ret = runner.runMethod(loadedImplantClass, "init", String.class);
        assertEquals("CONF_STRING=\"Modified\";CONF_BOOLEAN=true;CONF_INT=2;", ret);
    }

    @Test
    public void testSetConfig_PartialBulkConfOverride_AddOnlyAffectedField() throws IOException, ClassNotFoundException, ImplantConfigException {
        ImplantHandler implant = ImplantHandlerImpl.findAndCreateFor(TestClassImplant.class);
        Map<String, Object> bulk = new HashMap<>();
        bulk.put("CONF_BOOLEAN", true);

        implant.setConfig(bulk);
        ClassFile implantClass = implant.loadFreshConfiguredSpecimen();

        Class<?> loadedImplantClass = runner.load(implantClass);
        String ret = runner.runMethod(loadedImplantClass, "init", String.class);
        assertEquals("CONF_STRING=\"Original\";CONF_BOOLEAN=true;CONF_INT=1;", ret);
    }

    @Test(expected = ImplantConfigException.class)
    public void testSetConfig_InvalidDataType_ThrowException() throws IOException, ClassNotFoundException, ImplantConfigException {
        ImplantHandler implant = ImplantHandlerImpl.findAndCreateFor(TestClassImplant.class);

        implant.setConfig("CONF_BOOLEAN", 2);
        implant.loadFreshConfiguredSpecimen();
    }

    @Test
    public void testSetConfig_StringValues_ConvertFromString() throws IOException, ClassNotFoundException, ImplantConfigException {
        ImplantHandler implant = ImplantHandlerImpl.findAndCreateFor(TestClassImplant.class);

        implant.setConfig("CONF_BOOLEAN", "true");
        implant.setConfig("CONF_INT", "2");
        ClassFile implantClass = implant.loadFreshConfiguredSpecimen();

        Class<?> loadedImplantClass = runner.load(implantClass);
        String ret = runner.runMethod(loadedImplantClass, "init", String.class);
        assertEquals("CONF_STRING=\"Original\";CONF_BOOLEAN=true;CONF_INT=2;", ret);
    }

    @Test(expected = ImplantConfigException.class)
    public void testSetConfig_InvalidBooleanString_ThrowException() throws IOException, ClassNotFoundException, ImplantConfigException {
        ImplantHandler implant = ImplantHandlerImpl.findAndCreateFor(TestClassImplant.class);

        implant.setConfig("CONF_BOOLEAN", "yes");
        implant.loadFreshConfiguredSpecimen();
    }

    @Test(expected = ImplantConfigException.class)
    public void testSetConfig_InvalidIntString_ThrowException() throws IOException, ClassNotFoundException, ImplantConfigException {
        ImplantHandler implant = ImplantHandlerImpl.findAndCreateFor(TestClassImplant.class);

        implant.setConfig("CONF_INT", "1.0");
        implant.loadFreshConfiguredSpecimen();
    }
}
