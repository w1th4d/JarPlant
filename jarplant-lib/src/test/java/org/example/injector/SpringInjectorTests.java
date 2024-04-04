package org.example.injector;

import org.junit.Ignore;
import org.junit.Test;

public class SpringInjectorTests {

    // infect

    @Test
    @Ignore
    public void testInfect_SpringJar_Success() {
    }

    @Test
    @Ignore
    public void testInfect_SpringBootJar_Success() {
    }

    @Test
    @Ignore
    public void testInfect_NotAJar_Untouched() {
    }

    @Test
    @Ignore
    public void testInfect_EmptyJar_Untouched() {
    }

    @Test
    @Ignore
    public void testInfect_JarWithoutClasses_Untouched() {
    }

    @Test
    @Ignore
    public void testInfect_JarWithoutManifest_Success() {
        // Success or fail could be debatable
    }

    @Test
    @Ignore
    public void testInfect_VersionedJar_AllVersionsModified() {
    }

    @Test
    @Ignore
    public void testInfect_SignedClasses_SignedClassesUntouched() {
    }

    @Test
    @Ignore
    public void testInfect_NoSpringConfig_Untouched() {
    }

    @Test
    @Ignore
    public void testInfect_SeveralSpringConfigs_AllInfected() {
    }

    @Test
    @Ignore
    public void testInfect_ComponentScanningEnabled_ConfigUntouched() {
    }

    @Test
    @Ignore
    public void testInfect_ComponentScanningDisabled_ConfigModified() {
    }

    @Test
    @Ignore
    public void testInfect_ValidJar_AddedSpringComponent() {
    }

    @Test
    @Ignore
    // Corresponds to the standard debugging info produce by javac (lines + source)
    public void testInfect_StandardDebuggingInfo_Success() {
    }

    @Test
    @Ignore
    // Corresponds to javac -g:lines
    public void testInfect_LinesDebuggingInfo_Success() {
    }

    @Test
    @Ignore
    // Corresponds to javac -g:vars
    public void testInfect_VarsDebuggingInfo_Success() {
    }

    @Test
    @Ignore
    // Corresponds to javac -g:source
    public void testInfect_CodeDebuggingInfo_Success() {
    }

    @Test
    @Ignore
    // Corresponds to javac -g:none
    public void testInfect_NoDebuggingInfo_Success() {
    }

    @Test
    @Ignore
    public void testInfect_AlreadyInfectedJar_Untouched() {
        // TODO Implement infection detection
    }

    // addBeanToSpringConfig

    @Test
    @Ignore
    public void testAddBeanToSpringConfig_ExistingConfigWithBeans_AddedBean() {
    }

    @Test
    @Ignore
    public void testAddBeanToSpringConfig_ExistingConfigWithoutBeans_AddedBean() {
    }

    @Test
    @Ignore
    public void testAddBeanToSpringConfig_ImplantConfigWithBeans_AddedBean() {
    }

    @Test
    @Ignore
    public void testAddBeanToSpringConfig_ImplantConfigWithoutBeans_Untouched() {
    }

    // copyAllMethodAnnotations

    @Test
    @Ignore
    public void testCopyAllMethodAnnotations_OnlyBeanAnnotation_Copied() {
    }

    @Test
    @Ignore
    public void testCopyAllMethodAnnotations_SeveralAnnotations_AllCopied() {
    }

    @Test
    @Ignore
    public void testCopyAllMethodAnnotations_NoAnnotations_Fine() {
    }
}
