package dev.webfx.buildtool;

import dev.webfx.buildtool.modulefiles.M2MavenPomModuleFile;
import dev.webfx.buildtool.modulefiles.M2WebFxModuleFile;
import dev.webfx.tools.util.reusablestream.ReusableStream;

import java.nio.file.Path;

/**
 * @author Bruno Salmon
 */
public class M2ProjectModule extends ProjectModuleImpl {

    // TODO replace this with a maven command: mvn help:evaluate -Dexpression=settings.localRepository -q -DforceStdout
    private static final Path M2_LOCAL_REPOSITORY = Path.of(System.getProperty("user.dir"), ".m2", "repository");

    private final Path m2ProjectHomeDirectory;

    private M2MavenPomModuleFile mavenPomModuleFile;
    private M2WebFxModuleFile webFxModuleFile;

    public M2ProjectModule(String name, ProjectModule parentModule) {
        this(name, parentModule.getGroupId(), name, parentModule.getVersion(), parentModule);
    }

    public M2ProjectModule(Module descriptor, ProjectModule parentModule) {
        this(descriptor.getName(), descriptor.getGroupId(), descriptor.getArtifactId(), descriptor.getVersion(), parentModule);
    }

    public M2ProjectModule(String name, String groupId, String artifactId, String version, ProjectModule parentModule) {
        super(name, parentModule);
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
        m2ProjectHomeDirectory = M2_LOCAL_REPOSITORY.resolve(groupId.replace('.', '/')).resolve(artifactId).resolve(version);
        mavenPomModuleFile = new M2MavenPomModuleFile(this);
        webFxModuleFile = new M2WebFxModuleFile(this);
    }

    public Path getM2ProjectHomeDirectory() {
        return m2ProjectHomeDirectory;
    }

    public ProjectModule getOrCreateChildProjectModule(String name) {
        // TODO optimise this with a map
        return getModuleRegistry().getImportedProjectModules().filter(m -> m.getName().equals(name)).findFirst().orElse(null);
    }

    @Override
    public ReusableStream<String> getSubdirectoriesChildrenModules() {
        return null;
    }

    @Override
    public M2MavenPomModuleFile getMavenModuleFile() {
        return mavenPomModuleFile;
    }

    @Override
    public M2WebFxModuleFile getWebFxModuleFile() {
        return webFxModuleFile;
    }

    @Override
    public ReusableStream<String> getDeclaredJavaPackages() {
        return null;
    }

    @Override
    public ReusableStream<JavaFile> getDeclaredJavaFiles() {
        return null;
    }

    @Override
    public ReusableStream<String> getUsedJavaPackages() {
        return null;
    }

    @Override
    public ReusableStream<String> getUsedRequiredJavaServices() {
        return null;
    }

    @Override
    public ReusableStream<String> getUsedOptionalJavaServices() {
        return null;
    }

    @Override
    public ReusableStream<String> getUsedJavaServices() {
        return null;
    }

    @Override
    public ReusableStream<String> getDeclaredJavaServices() {
        return null;
    }

    @Override
    public ReusableStream<Providers> getExecutableProviders() {
        return null;
    }

    @Override
    public ReusableStream<ModuleDependency> getDirectDependencies() {
        return null;
    }

    @Override
    public ReusableStream<ModuleDependency> getTransitiveDependencies() {
        return null;
    }

    @Override
    public ReusableStream<ModuleDependency> getDiscoveredByCodeAnalyzerSourceDependencies() {
        return null;
    }

    @Override
    public ReusableStream<ModuleDependency> getDirectDependenciesWithoutFinalExecutableResolutions() {
        return null;
    }

    @Override
    public ReusableStream<ModuleDependency> getTransitiveDependenciesWithoutImplicitProviders() {
        return null;
    }
}
