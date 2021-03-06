package com.jayway.maven.plugins.android.common;

import com.jayway.maven.plugins.android.AndroidNdk;

import org.apache.maven.artifact.*;
import org.apache.maven.artifact.factory.DefaultArtifactFactory;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.testing.SilentLog;
import org.apache.maven.plugin.testing.stubs.ArtifactStub;
import org.apache.maven.project.MavenProject;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.internal.impl.DefaultRepositorySystem;
import org.eclipse.aether.repository.RemoteRepository;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.Collections;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * @author Johan Lindquist
 */
public class NativeHelperTest {

    @Rule
    public TemporaryFolder apklibDir = new TemporaryFolder();

    private NativeHelper nativeHelper;

    @Before
    public void setupNativeHelper() {
        MavenProject project = new MavenProject();
        project.setDependencyArtifacts(Collections.<Artifact>emptySet());

        ArtifactStub apklib = new ArtifactStub() {
            @Override
            public String getId() {
                return getArtifactId();
            }
        };
        apklib.setArtifactId("some-apklib");
        apklib.setGroupId("group");
        apklib.setType(AndroidExtension.APKLIB);
        project.addAttachedArtifact(apklib);

        nativeHelper = new NativeHelper(project, Collections.<RemoteRepository>emptyList(),
                MavenRepositorySystemUtils.newSession(), new DefaultRepositorySystem(),
                new DefaultArtifactFactory(), new SilentLog());
    }

    @Test
    public void invalidVersions()
    {
        String[] versions = {"r4", "r5", "r5b", "r5c", "r6", "r6b"};

        for (int i = 0; i < versions.length; i++) {
            String version = versions[i];
            try {
                NativeHelper.validateNDKVersion(7,version);
                Assert.fail("Version should fail: " + version);
            } catch (MojoExecutionException e) {
            }
        }
    }

    @Test
    public void validVersions()
    {
        String[] versions = {"r7", "r8a", "r8z", "r10", "r19b", "r25", "r100", "r100b"};

        for (int i = 0; i < versions.length; i++) {
            String version = versions[i];
            try {
                NativeHelper.validateNDKVersion(7, version);
            } catch (MojoExecutionException e) {
                Assert.fail("Version should not fail: " + version);
            }
        }
    }

    @Test
    public void shouldNotIncludeLibsFolderAsNativeDependenciesSourceWhenNoNativeLibsInside() throws Exception {
        new File(apklibDir.getRoot(), "some-apklib/libs").mkdirs();
        new File(apklibDir.getRoot(), "some-apklib/libs/some.jar").createNewFile();

        Set<Artifact> nativeDependencies = nativeHelper.getNativeDependenciesArtifacts(apklibDir.getRoot(), true);

        assertTrue("Included JARs as native dependencies, but shouldn't", nativeDependencies.isEmpty());
    }

    @Test
    public void shouldIncludeLibsFolderAsNativeDependenciesSourceWhenNativeLibsInside() throws Exception {
        new File(apklibDir.getRoot(), "some-apklib/libs").mkdirs();
        new File(apklibDir.getRoot(), "some-apklib/libs/some.jar").createNewFile();
        new File(apklibDir.getRoot(), "some-apklib/libs/some.so").createNewFile();

        Set<Artifact> nativeDependencies = nativeHelper.getNativeDependenciesArtifacts(apklibDir.getRoot(), true);

        assertFalse("Excluded native dependencies, but shouldn't", nativeDependencies.isEmpty());
    }

    @Test
    public void architectureResolutionForPlainArchitectureClassifier() throws Exception
    {
        for (String ndkArchitecture : AndroidNdk.NDK_ARCHITECTURES)
        {
            Artifact artifact = new DefaultArtifact("acme", "acme", "1.0", "runtime", "so", ndkArchitecture, null);
            String architecture = NativeHelper.extractArchitectureFromArtifact(artifact, "armeabi");
            assertNotNull("unexpected null architecture", architecture);
            assertEquals("unexpected architecture", ndkArchitecture, architecture);
        }

    }

    @Test
    public void architectureResolutionForMixedArchitectureClassifier() throws Exception
    {
        for (String ndkArchitecture : AndroidNdk.NDK_ARCHITECTURES)
        {
            Artifact artifact = new DefaultArtifact("acme", "acme", "1.0", "runtime", "so", ndkArchitecture + "-acme", null);
            String architecture = NativeHelper.extractArchitectureFromArtifact(artifact, "armeabi");
            assertNotNull("unexpected null architecture", architecture);
            assertEquals("unexpected architecture", ndkArchitecture, architecture);
        }
    }

    @Test
    public void architectureResolutionForDefaultLegacyArchitectureClassifier() throws Exception
    {
        Artifact artifact = new DefaultArtifact("acme", "acme", "1.0", "runtime", "so", "acme", null);
        String architecture = NativeHelper.extractArchitectureFromArtifact(artifact, "armeabi");
        assertNotNull("unexpected null architecture", architecture);
        assertEquals("unexpected architecture", "armeabi", architecture);
    }

    @Test
    public void artifactHasHardwareArchitecture() throws Exception
    {
        for (String ndkArchitecture : AndroidNdk.NDK_ARCHITECTURES)
       {
           Artifact artifact = new DefaultArtifact("acme", "acme", "1.0", "runtime", "so", ndkArchitecture, null);
           boolean value = NativeHelper.artifactHasHardwareArchitecture(artifact, ndkArchitecture, "armeabi");
           assertTrue("unexpected value", value);
       }
    }

    @Test
    public void artifactHasHardwareArchitectureWithClassifier() throws Exception
    {
       for (String ndkArchitecture : AndroidNdk.NDK_ARCHITECTURES)
       {
           Artifact artifact = new DefaultArtifact("acme", "acme", "1.0", "runtime", "so", ndkArchitecture + "-acme", null);
           boolean value = NativeHelper.artifactHasHardwareArchitecture(artifact, ndkArchitecture, "armeabi");
           assertTrue("unexpected value", value);
       }
    }

    @Test
    public void artifactHasHardwareArchitectureWithDefaultLegacyClassifier() throws Exception
    {
        Artifact artifact = new DefaultArtifact("acme", "acme", "1.0", "runtime", "so", "acme", null);
        boolean value = NativeHelper.artifactHasHardwareArchitecture(artifact, "armeabi", "armeabi");
        assertTrue("unexpected value", value);
    }

    @Test
    public void artifactHasHardwareArchitectureNotNativeLibrary() throws Exception
    {
        Artifact artifact = new DefaultArtifact("acme", "acme", "1.0", "runtime", "jar", "armeabi", null);
        boolean value = NativeHelper.artifactHasHardwareArchitecture(artifact, "armeabi", "armeabi");
        assertFalse("unexpected value", value);
    }

}
