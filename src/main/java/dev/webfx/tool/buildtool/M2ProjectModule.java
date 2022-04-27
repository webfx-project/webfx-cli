package dev.webfx.tool.buildtool;

import dev.webfx.tool.buildtool.modulefiles.M2MavenPomModuleFile;
import dev.webfx.tool.buildtool.modulefiles.M2WebFxModuleFile;
import dev.webfx.tool.buildtool.util.process.ProcessUtil;
import dev.webfx.lib.reusablestream.ReusableStream;
import org.apache.maven.shared.invoker.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

/**
 * @author Bruno Salmon
 */
public class M2ProjectModule extends ProjectModuleImpl {

    private final static boolean USE_MAVEN_INVOKER = false; // if false, just using shell invocation
    private final static boolean ASK_MAVEN_LOCAL_REPOSITORY = false; // if false, we will use the default path: ${user.home}/.m2/repository
    private final static Path M2_LOCAL_REPOSITORY = ASK_MAVEN_LOCAL_REPOSITORY ?
            // Maven invocation (advantage: returns the correct path 100% sure / disadvantage: takes a few seconds to execute)
            Path.of(ProcessUtil.executeAndReturnLastOutputLine("mvn -N help:evaluate -Dexpression=settings.localRepository -q -DforceStdout"))
            // Otherwise, getting the standard path  (advantage: immediate / disadvantage: not 100% sure (the developer may have changed the default Maven settings)
            : Path.of(System.getProperty("user.home"), ".m2", "repository");

    private static Invoker MAVEN_INVOKER; // Will be initialised later if needed

    private final Path m2ProjectHomeDirectory;

    private M2MavenPomModuleFile mavenPomModuleFile;
    private M2WebFxModuleFile webFxModuleFile;
    private Boolean hasJavaSourceDirectory;
    private Path javaSourceDirectory;

    public M2ProjectModule(String name, M2ProjectModule parentModule) {
        this(name, parentModule.getGroupId(), name, parentModule.getVersion(), parentModule);
    }

    public M2ProjectModule(Module descriptor, M2ProjectModule parentModule) {
        this(descriptor.getName(), descriptor.getGroupId(), descriptor.getArtifactId(), descriptor.getVersion(), parentModule);
    }

    public M2ProjectModule(String name, String groupId, String artifactId, String version, M2ProjectModule parentModule) {
        super(name, parentModule);
        if (artifactId == null)
            artifactId = name;
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
        m2ProjectHomeDirectory = M2_LOCAL_REPOSITORY.resolve(groupId.replace('.', '/')).resolve(artifactId).resolve(version);
    }

    public Path getM2ArtifactSubPath(String suffix) {
        return m2ProjectHomeDirectory.resolve(getArtifactId() + '-' + getVersion() + suffix);
    }

    @Override
    public M2ProjectModule fetchParentModule() {
        return (M2ProjectModule) super.fetchParentModule();
    }

    public boolean isWebFxModuleFileExpected() { // Should be overridden in M2RootModule
        return fetchParentModule().isWebFxModuleFileExpected();
    }

    @Override
    public M2MavenPomModuleFile getMavenModuleFile() {
        if (mavenPomModuleFile == null)
            mavenPomModuleFile = new M2MavenPomModuleFile(this);
        return mavenPomModuleFile;
    }

    @Override
    public M2WebFxModuleFile getWebFxModuleFile() {
        if (webFxModuleFile == null)
            webFxModuleFile = new M2WebFxModuleFile(this);
        return webFxModuleFile;
    }

    public M2WebFxModuleFile getWebFxModuleFileWithExportSnapshotContainingThisModule() {
        M2ProjectModule moduleWithExport = this;
        while ((moduleWithExport.getParentModule() != null && (moduleWithExport = moduleWithExport.fetchParentModule()) != null)) {
            if (moduleWithExport.getWebFxModuleFile().lookupExportedSnapshotProjectElement(this) != null)
                return moduleWithExport.getWebFxModuleFile();
        }
        return null;
    }

    @Override
    public boolean usesJavaPackage(String javaPackage) {
        // If the sources are already present, we can skip this section and just do a sources analyse to compute the requested usage.
        if (javaSourceDirectory == null) { // But if they are absent, we try to compute the usage without downloading the sources (if possible with the export snapshot).
            // If this module is an aggregate module, we don't expect any sources, so we return false
            if (isAggregate())
                return false;
            Module moduleDeclaringThisPackage = getRootModule().searchJavaPackageModule(javaPackage, this);
            if (moduleDeclaringThisPackage == this)
                return true;
            if (moduleDeclaringThisPackage != null && getDirectModules().filter(m -> m == moduleDeclaringThisPackage).isEmpty())
                return false;
            Boolean computedUsage = getModuleRegistry().doExportSnapshotsTellIfModuleIsUsingPackageOrClass(this, javaPackage);
            if (computedUsage != null)
                return computedUsage;
        }
        return super.usesJavaPackage(javaPackage);
    }

    @Override
    public boolean usesJavaClass(String javaClass) {
        if (javaSourceDirectory == null) {
            if (isAggregate())
                return false;
            Boolean computedUsage = getModuleRegistry().doExportSnapshotsTellIfModuleIsUsingPackageOrClass(this, javaClass);
            if (computedUsage != null)
                return computedUsage;
        }
        return super.usesJavaClass(javaClass);
    }

    @Override
    public boolean hasJavaSourceDirectory() {
        if (hasJavaSourceDirectory == null)
            hasJavaSourceDirectory = getJavaSourceDirectory() != null;
        return hasJavaSourceDirectory;
    }

    @Override
    public Path getJavaSourceDirectory() {
        if (javaSourceDirectory == null) { // Not yet evaluated (first and last time call)
            // Path to the source artifact in the local maven repository
            Path m2SourcesJarPath = getM2ArtifactSubPath("-sources.jar");
            // See what we do if the source artifact is not there:
            if (!Files.exists(m2SourcesJarPath)) {
                // No java source directory for aggregate projects (which are just parent modules with children modules but no sources)
                // Also we don't expect a source directory for parent modules such as webfx-parent or webfx-stack-parent
                if (isAggregate() || getName().endsWith("-parent"))
                    return null;
                // For all other cases, we try to download the source artifact
                downloadArtifactClassifier("jar:sources");
                // If there is none, we return null
                if (!Files.exists(m2SourcesJarPath))
                    return null;
            }
            // At this point the source jar should be there, and the source directory corresponds to the root of this jar
            try {
                javaSourceDirectory = FileSystems.newFileSystem(m2SourcesJarPath).getPath("/");
            } catch (IOException e) {
                //e.printStackTrace();
            }
        }
        return javaSourceDirectory;
    }

    public M2ProjectModule getOrCreateChildProjectModule(String name) {
        return getModuleRegistry().getOrCreateM2ProjectModule(name, this);
    }

    @Override
    public ReusableStream<String> getSubdirectoriesChildrenModules() {
        // Should never be called as for M2 projects, the modules are taken from the pom, not from webfx.xml
        // (so the <subdirectories-modules/> directive is never executed)
        throw new UnsupportedOperationException("getSubdirectoriesChildrenModules() should never be called on M2 project");
    }

    public void downloadArtifactClassifier(String classifier) {
        invokeMavenGoal("dependency:get -N -Dtransitive=false -Dartifact=" + getGroupId() + ":" + getArtifactId() + ":" + getVersion() + ":" + classifier);
    }

    private static void invokeMavenGoal(String goal) {
        if (!USE_MAVEN_INVOKER) {
            ProcessUtil.execute("mvn " + goal, line -> line.startsWith("Downloading"));
        } else {
            Logger.log("Invoking maven goal: " + goal);
            InvocationRequest request = new DefaultInvocationRequest();
            request.setGoals(Collections.singletonList(goal));
            if (MAVEN_INVOKER == null) {
                MAVEN_INVOKER = new DefaultInvoker();
                String mavenHome = System.getProperty("maven.home");
                if (mavenHome == null)
                    // Invoking mvn -version through the shell to get the maven home (takes about 300ms)
                    mavenHome = ProcessUtil.executeAndReturnLastMatchingLine("mvn -version", line -> line.startsWith("Maven home:")).substring(11).trim();
                MAVEN_INVOKER.setMavenHome(new File(mavenHome));
            }
            try {
                MAVEN_INVOKER.execute(request);
            } catch (MavenInvocationException e) {
                throw new RuntimeException(e);
            }
        }
    }

}
