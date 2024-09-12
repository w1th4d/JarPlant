package io.github.w1th4d.jarplant;

import io.github.w1th4d.jarplant.implants.DummyTestClassImplant;
import io.github.w1th4d.jarplant.implants.TestClassImplant;
import javassist.bytecode.ClassFile;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static io.github.w1th4d.jarplant.TestHelpers.findTestEnvironmentDir;
import static io.github.w1th4d.jarplant.TestHelpers.populateJarEntriesIntoEmptyFile;
import static org.junit.Assert.*;

public class ImplantHandlerTests {
    private TestImplantRunner runner;
    private Path tempFile;

    @Before
    public void configureLogger() {
        TestHelpers.configureLogger();
    }

    @Before
    public void getImplantRunner() {
        this.runner = new TestImplantRunner();
    }

    @Before
    public void createTempFiles() throws IOException {
        this.tempFile = Files.createTempFile("JarPlantTests-", ".jar");
    }

    @After
    public void removeTempFile() throws IOException {
        Files.delete(tempFile);
    }

    @Test
    public void testCreateFor_ClassFile_Success() throws IOException {
        // Arrange
        Path testEnv = findTestEnvironmentDir(this.getClass());
        Path classFile = testEnv.resolve("io/github/w1th4d/jarplant/implants/DummyTestClassImplant.class");

        // Act
        ImplantHandler implant = ImplantHandlerImpl.createFor(classFile);
        ClassFile specimen = implant.loadFreshRawSpecimen();
        Class<?> loadedClass = runner.load(specimen);

        // Assert
        assertNotNull("Loaded implant class.", specimen);
        assertNotNull("JVM managed to load the specimen.", loadedClass);
    }

    @Test
    public void testFindAndCreateFor_Class_Success() throws IOException, ClassNotFoundException, ImplantException {
        // Arrange
        Class<?> clazz = DummyTestClassImplant.class;

        // Act
        ImplantHandler implant = ImplantHandlerImpl.findAndCreateFor(clazz);
        ClassFile specimen = implant.loadFreshRawSpecimen();
        Class<?> loadedClass = runner.load(specimen);

        // Assert
        assertNotNull("Loaded implant class.", specimen);
        assertNotNull("JVM managed to load the specimen.", loadedClass);
    }

    @Test
    public void testFindAndCreateFor_ClassNameAndPath_Success() throws IOException, ClassNotFoundException, ImplantException {
        // Arrange
        ClassName className = ClassName.of(DummyTestClassImplant.class);
        Path classPath = findTestEnvironmentDir(this.getClass());

        // Act
        ImplantHandler implant = ImplantHandlerImpl.findAndCreateFor(classPath, className);
        ClassFile specimen = implant.loadFreshRawSpecimen();
        Class<?> loadedClass = runner.load(specimen);

        // Assert
        assertNotNull("Loaded implant class.", specimen);
        assertNotNull("JVM managed to load the specimen.", loadedClass);
    }

    @Test
    public void testFindAndCreateFor_JarFile_Success() throws IOException, ClassNotFoundException, ImplantException {
        // Arrange
        Path baseDir = findTestEnvironmentDir(this.getClass());
        Path relativePath = Path.of("io/github/w1th4d/jarplant/implants/DummyTestClassImplant.class");
        populateJarEntriesIntoEmptyFile(tempFile, baseDir, relativePath);

        // Act + Assert
        ImplantHandlerImpl.findAndCreateFor(tempFile, ClassName.of(DummyTestClassImplant.class));
    }

    @Test(expected = ClassNotFoundException.class)
    public void testFindAndCreateFor_StdlibClass_NotFound() throws ImplantException, IOException, ClassNotFoundException {
        ImplantHandlerImpl.findAndCreateFor(String.class);
    }

    @Test(expected = IOException.class)
    public void testFindAndCreateFor_NotAClassFile_IOException() throws ImplantException, IOException, ClassNotFoundException {
        Path baseDir = findTestEnvironmentDir(this.getClass());
        Path notAClassFile = baseDir.resolve("../maven-archiver/pom.properties");
        ImplantHandlerImpl.findAndCreateFor(notAClassFile, ClassName.of(Object.class));
    }

    @Test
    public void testFindAndCreateFor_WithDependencies_ContainsDependencyClasses() throws IOException, ClassNotFoundException, ClassNameException, ImplantException {
        // Arrange
        Path baseDir = findTestEnvironmentDir(this.getClass());

        // Act
        ImplantHandler subject = ImplantHandlerImpl.findAndCreateFor(baseDir, ClassName.of(DummyTestClassImplant.class));

        // Assert
        Map<ClassName, byte[]> dependencies = subject.getDependencies();
        assertNotNull("Contains specified dependency", dependencies.get(ClassName.fromFullClassName("io.github.w1th4d.jarplant.implants.DummyDependency")));
        assertNotNull("Contains transitive dependency", dependencies.get(ClassName.fromFullClassName("io.github.w1th4d.jarplant.implants.DummySubDependency")));
        assertNull("Implant class is not a dependency", dependencies.get(ClassName.fromFullClassName("io.github.w1th4d.jarplant.implants.DummyTestClassImplant")));
    }

    @Test
    public void testGetImplantClassName_Valid_FullClassName() throws IOException, ClassNotFoundException, ImplantException {
        // Arrange
        ImplantHandler implant = ImplantHandlerImpl.findAndCreateFor(DummyTestClassImplant.class);

        // Act
        ClassName name = implant.getImplantClassName();

        // Assert
        assertEquals("io.github.w1th4d.jarplant.implants.DummyTestClassImplant", name.getFullClassName());
    }

    @Test
    public void testGetAvailableConfig_AfterLoaded_CorrectType() throws IOException, ClassNotFoundException, ImplantException {
        // Arrange
        ImplantHandler implant = ImplantHandlerImpl.findAndCreateFor(TestClassImplant.class);

        // Act
        Map<String, ImplantHandlerImpl.ConfDataType> availableConfig = implant.getAvailableConfig();
        ImplantHandlerImpl.ConfDataType confString = availableConfig.get("CONF_STRING");
        ImplantHandlerImpl.ConfDataType confBool = availableConfig.get("CONF_BOOLEAN");
        ImplantHandlerImpl.ConfDataType confInt = availableConfig.get("CONF_INT");

        // Assert
        assertNotNull("Has config property.", confString);
        assertNotNull("Has config property.", confBool);
        assertNotNull("Has config property.", confInt);
        assertEquals("Config property is of correct data type.", ImplantHandlerImpl.ConfDataType.STRING, confString);
        assertEquals("Config property is of correct data type.", ImplantHandlerImpl.ConfDataType.BOOLEAN, confBool);
        assertEquals("Config property is of correct data type.", ImplantHandlerImpl.ConfDataType.INT, confInt);
    }

    @Test
    public void testSetConfig_CorrectProp_Success() throws IOException, ClassNotFoundException, ImplantConfigException, ImplantException {
        // Arrange
        ImplantHandler implant = ImplantHandlerImpl.findAndCreateFor(TestClassImplant.class);

        // Act: Change all available config values
        implant.setConfig("CONF_STRING", "Modified");
        implant.setConfig("CONF_BOOLEAN", true);
        implant.setConfig("CONF_INT", 2);
        ClassFile specimen = implant.loadFreshConfiguredSpecimen();     // Main subject
        Class<?> loadedImplantClass = runner.load(specimen);
        String implantInitReturnValue = runner.runMethod(loadedImplantClass, "init", String.class);

        // Assert
        assertEquals("Modified config value is used when the implant class runs.",
                "CONF_STRING=\"Modified\";CONF_BOOLEAN=true;CONF_INT=2;", implantInitReturnValue);
    }

    @Test
    public void testSetConfig_PartialOverride_OnlyChangeAffectedField() throws IOException, ClassNotFoundException, ImplantConfigException, ImplantException {
        // Arrange
        ImplantHandler implant = ImplantHandlerImpl.findAndCreateFor(TestClassImplant.class);

        // Act: Only change one of the available config values
        implant.setConfig("CONF_BOOLEAN", true);
        ClassFile specimen = implant.loadFreshConfiguredSpecimen();

        // Assert
        Class<?> loadedImplantClass = runner.load(specimen);
        String implantInitReturnValue = runner.runMethod(loadedImplantClass, "init", String.class);
        assertEquals("Modified config value is used when the implant class runs.",
                "CONF_STRING=\"Original\";CONF_BOOLEAN=true;CONF_INT=1;", implantInitReturnValue);
    }

    @Test
    public void testSetConfig_BulkConfigOverride_AddAllOverrides() throws IOException, ClassNotFoundException, ImplantConfigException, ImplantException {
        // Arrange
        ImplantHandler implant = ImplantHandlerImpl.findAndCreateFor(TestClassImplant.class);
        Map<String, Object> bulk = new HashMap<>();
        bulk.put("CONF_STRING", "Modified");
        bulk.put("CONF_BOOLEAN", true);
        bulk.put("CONF_INT", 2);

        // Act: Change all available config values in bulk
        implant.setConfig(bulk);
        ClassFile specimen = implant.loadFreshConfiguredSpecimen();     // Main subject
        Class<?> loadedImplantClass = runner.load(specimen);
        String implantInitReturnValue = runner.runMethod(loadedImplantClass, "init", String.class);

        // Assert
        assertEquals("Modified config value is used when the implant class runs.",
                "CONF_STRING=\"Modified\";CONF_BOOLEAN=true;CONF_INT=2;", implantInitReturnValue);
    }

    @Test
    public void testSetConfig_PartialBulkConfOverride_AddOnlyAffectedField() throws IOException, ClassNotFoundException, ImplantConfigException, ImplantException {
        // Arrange
        ImplantHandler implant = ImplantHandlerImpl.findAndCreateFor(TestClassImplant.class);
        Map<String, Object> bulk = new HashMap<>();
        bulk.put("CONF_BOOLEAN", true);

        // Act
        implant.setConfig(bulk);
        ClassFile specimen = implant.loadFreshConfiguredSpecimen();
        Class<?> loadedImplantClass = runner.load(specimen);
        String implantInitReturnValue = runner.runMethod(loadedImplantClass, "init", String.class);

        // Assert
        assertEquals("Modified config value is used when the implant class runs.",
                "CONF_STRING=\"Original\";CONF_BOOLEAN=true;CONF_INT=1;", implantInitReturnValue);
    }

    @Test(expected = ImplantConfigException.class)
    public void testSetConfig_InvalidDataType_ThrowException() throws IOException, ClassNotFoundException, ImplantConfigException, ImplantException {
        // Arrange
        ImplantHandler implant = ImplantHandlerImpl.findAndCreateFor(TestClassImplant.class);

        // Act + Assert
        implant.setConfig("CONF_BOOLEAN", 2);
        implant.loadFreshConfiguredSpecimen();  // Should fail
    }

    @Test
    public void testSetConfig_StringValues_ConvertFromString() throws IOException, ClassNotFoundException, ImplantConfigException, ImplantException {
        // Arrange
        ImplantHandler implant = ImplantHandlerImpl.findAndCreateFor(TestClassImplant.class);

        // Act
        implant.setConfig("CONF_BOOLEAN", "true");
        implant.setConfig("CONF_INT", "2");
        ClassFile specimen = implant.loadFreshConfiguredSpecimen();
        Class<?> loadedImplantClass = runner.load(specimen);
        String implantInitReturnValue = runner.runMethod(loadedImplantClass, "init", String.class);

        // Assert
        assertEquals("Boolean config value was converted properly.",
                "CONF_STRING=\"Original\";CONF_BOOLEAN=true;CONF_INT=2;", implantInitReturnValue);
    }

    @Test(expected = ImplantConfigException.class)
    public void testSetConfig_InvalidBooleanString_ThrowException() throws IOException, ClassNotFoundException, ImplantConfigException, ImplantException {
        // Arrange
        ImplantHandler implant = ImplantHandlerImpl.findAndCreateFor(TestClassImplant.class);

        // Act + Assert
        implant.setConfig("CONF_BOOLEAN", "yes");
        implant.loadFreshConfiguredSpecimen();  // Should fail
    }

    @Test(expected = ImplantConfigException.class)
    public void testSetConfig_InvalidIntString_ThrowException() throws IOException, ClassNotFoundException, ImplantConfigException, ImplantException {
        // arrange
        ImplantHandler implant = ImplantHandlerImpl.findAndCreateFor(TestClassImplant.class);

        // Act + Assert
        implant.setConfig("CONF_INT", "1.0");
        implant.loadFreshConfiguredSpecimen();  // Should fail
    }
}
