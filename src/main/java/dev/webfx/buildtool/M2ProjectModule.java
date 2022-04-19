package dev.webfx.buildtool;

import dev.webfx.buildtool.modulefiles.M2MavenPomModuleFile;
import dev.webfx.buildtool.modulefiles.M2WebFxModuleFile;
import dev.webfx.buildtool.util.process.ProcessUtil;
import dev.webfx.tools.util.reusablestream.ReusableStream;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * @author Bruno Salmon
 */
public class M2ProjectModule extends ProjectModuleImpl {

    private final static Path M2_LOCAL_REPOSITORY = Path.of(ProcessUtil.executeAndReturnLastOutputLine("mvn -N help:evaluate -Dexpression=settings.localRepository -q -DforceStdout"));// Path.of(System.getProperty("user.home"), ".m2", "repository");

    private final Path m2ProjectHomeDirectory;

    private M2MavenPomModuleFile mavenPomModuleFile;
    private M2WebFxModuleFile webFxModuleFile;
    private Boolean hasJavaSourceDirectory;
    private Path javaSourceDirectory;

    public M2ProjectModule(String name, ProjectModule parentModule) {
        this(name, parentModule.getGroupId(), name, parentModule.getVersion(), parentModule);
    }

    public M2ProjectModule(Module descriptor, ProjectModule parentModule) {
        this(descriptor.getName(), descriptor.getGroupId(), descriptor.getArtifactId(), descriptor.getVersion(), parentModule);
    }

    public M2ProjectModule(String name, String groupId, String artifactId, String version, ProjectModule parentModule) {
        super(name, parentModule);
        if (artifactId == null)
            artifactId = name;
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
        m2ProjectHomeDirectory = M2_LOCAL_REPOSITORY.resolve(groupId.replace('.', '/')).resolve(artifactId).resolve(version);
    }

    public Path getM2ProjectHomeDirectory() {
        return m2ProjectHomeDirectory;
    }

    public Path getM2ArtifactSubPath(String suffix) {
        return m2ProjectHomeDirectory.resolve(getArtifactId() + '-' + getVersion() + suffix);
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

    @Override
    public boolean hasJavaSourceDirectory() {
        if (hasJavaSourceDirectory == null)
            hasJavaSourceDirectory = getJavaSourceDirectory() != null;
        return hasJavaSourceDirectory;
    }

    @Override
    public Path getJavaSourceDirectory() {
        if (javaSourceDirectory == null && !isAggregate()) {
            try {
                Path m2SourcesJarPath = getM2ArtifactSubPath("-sources.jar");
                if (!Files.exists(m2SourcesJarPath))
                    downloadArtifactClassifier("jar:sources");
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
        return null; // Should never be called as for M2 projects, the modules are taken from the pom, not from webfx.xml (so the <subdirectories-modules/> directive is never executed)
    }

    public void downloadArtifactClassifier(String classifier) {
        ProcessUtil.execute("mvn -N dependency:get -Dartifact=" + getGroupId() + ":" + getArtifactId() + ":" + getVersion() + ":" + classifier, line -> line.startsWith("Downloading"));
    }

}
