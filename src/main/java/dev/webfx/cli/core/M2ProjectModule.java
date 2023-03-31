package dev.webfx.cli.core;

import dev.webfx.cli.modulefiles.M2MavenPomModuleFile;
import dev.webfx.cli.modulefiles.M2WebFxModuleFile;
import dev.webfx.lib.reusablestream.ReusableStream;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * @author Bruno Salmon
 */
public class M2ProjectModule extends ProjectModuleImpl {

    private final Path m2ProjectHomeDirectory;

    private M2MavenPomModuleFile mavenPomModuleFile;
    private M2WebFxModuleFile webFxModuleFile;
    private Boolean hasSourceDirectory;
    private Path sourceDirectory;

    public M2ProjectModule(String name, M2ProjectModule parentModule) {
        this(name, parentModule.getGroupId(), name, parentModule.getVersion(), null, parentModule);
    }

    public M2ProjectModule(Module descriptor, M2ProjectModule parentModule) {
        this(descriptor.getName(), descriptor.getGroupId(), descriptor.getArtifactId(), descriptor.getVersion(), descriptor.getType(), parentModule);
    }

    public M2ProjectModule(String name, String groupId, String artifactId, String version, String type, M2ProjectModule parentModule) {
        super(name, parentModule);
        if (artifactId == null)
            artifactId = name;
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
        this.type = type;
        m2ProjectHomeDirectory = MavenUtil.M2_LOCAL_REPOSITORY.resolve(groupId.replace('.', '/')).resolve(artifactId).resolve(version);
    }

    public Path getM2ProjectHomeDirectory() {
        return m2ProjectHomeDirectory;
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

    Boolean tryEvaluateUsesJavaPackageWithoutDownloadingSources(String javaPackage) {
        // If the sources are already present, we can skip this section and just do a sources analyse to compute the requested usage.
        if (sourceDirectory != null)
            return null;
        // But if they are absent, we try to compute the usage without downloading the sources (if possible with the export snapshot).
        // If this module is an aggregate module, we don't expect any sources, so we return false
        if (isAggregate())
            return false;
        // If the package is declared in this module, then yes, it uses it
        Module moduleDeclaringThisPackage = getRootModule().searchJavaPackageModule(javaPackage, this);
        if (moduleDeclaringThisPackage == this)
            return true;
        // If the package is declared in another module that is not listed in the direct dependencies (according to export snapshot), then it means this module doesn't use it
        if (moduleDeclaringThisPackage != null && getMainJavaSourceRootAnalyzer().getDirectModules().filter(m -> m == moduleDeclaringThisPackage).isEmpty())
            return false;
        // For all other cases, we check if the info resides in the export snapshot
        return getModuleRegistry().doExportSnapshotsTellIfModuleIsUsingPackageOrClass(this, javaPackage);
    }

    Boolean tryEvaluateUsesJavaClassWithoutDownloadingSources(String javaClass) {
        // If the sources are already present, we can skip this section and just do a sources analyse to compute the requested usage.
        if (sourceDirectory != null)
            return null;
        // But if they are absent, we try to compute the usage without downloading the sources (if possible with the export snapshot).
        // If this module is an aggregate module, we don't expect any sources, so we return false
        if (isAggregate())
            return false;
        // For all other cases, we check if the info resides in the export snapshot
        return getModuleRegistry().doExportSnapshotsTellIfModuleIsUsingPackageOrClass(this, javaClass);
    }

    @Override
    public boolean hasSourceDirectory() {
        if (hasSourceDirectory == null)
            hasSourceDirectory = getSourceDirectory() != null;
        return hasSourceDirectory;
    }

    @Override
    public Path getSourceDirectory() {
        if (sourceDirectory == null) { // Not yet evaluated (first and last time call)
            // Path to the source artifact in the local maven repository
            Path m2SourcesJarPath = getM2ArtifactSubPath("-sources.jar");
            // See what we do if the source artifact is not there:
            if (!Files.exists(m2SourcesJarPath)) {
                // No source directory for aggregate projects (which are just parent modules with children modules but no sources)
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
                sourceDirectory = FileSystems.newFileSystem(m2SourcesJarPath).getPath("/");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return sourceDirectory;
    }

    @Override
    public boolean hasMainJavaSourceDirectory() {
        return hasSourceDirectory();
    }

    @Override
    public Path getMainJavaSourceDirectory() {
        // Same as source directory (there is no main/java subdirectory in the -sources.jar artifact)
        return getSourceDirectory();
    }

    @Override
    public boolean hasTestJavaSourceDirectory() {
        return false;
    }

    @Override
    public Path getTestJavaSourceDirectory() {
        return null;
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
        MavenUtil.downloadArtifact(getGroupId(), getArtifactId(), getVersion(), classifier);
    }

}
