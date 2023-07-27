package dev.webfx.cli.core;

import dev.webfx.cli.modulefiles.*;
import dev.webfx.cli.util.splitfiles.SplitFiles;
import dev.webfx.lib.reusablestream.ReusableStream;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * @author Bruno Salmon
 */
public class DevProjectModule extends ProjectModuleImpl {

    private final Path homeDirectory;
    private Boolean hasSourceDirectory, hasMainJavaSourceDirectory, hasTestJavaSourceDirectory;
    private DevJavaModuleInfoFile mainJavaModuleInfoFile;
    private DevWebFxModuleFile webFxModuleFile;
    private DevMavenPomModuleFile mavenPomModuleFile;

    private DevGwtModuleFile gwtModuleFile;
    private DevGwtHtmlFile gwtHtmlFile;

    /************************
     ***** Constructors *****
     ************************/

    public DevProjectModule(Path homeDirectory, ProjectModule parentModule) {
        // The module name is the name of the home directory, unless a different name is specified in webfx.xml
        super(homeDirectory.toAbsolutePath().getFileName().toString(), parentModule);
        this.homeDirectory = homeDirectory;
        // When specified, the module name specified in webfx.xml is finally preferred over the name of the home directory
        String webFxName = getWebFxModuleFile().getName();
        if (webFxName != null)
            this.name = webFxName;
    }

    /*************************
     ***** Basic getters *****
     *************************/

    public DevWebFxModuleFile getWebFxModuleFile() {
        if (webFxModuleFile == null)
            webFxModuleFile = new DevWebFxModuleFile(this);
        return webFxModuleFile;
    }

    public DevMavenPomModuleFile getMavenModuleFile() {
        if (mavenPomModuleFile == null)
            mavenPomModuleFile = new DevMavenPomModuleFile(this);
        return mavenPomModuleFile;
    }


    public Path getHomeDirectory() {
        return homeDirectory;
    }

    public Path getSourceDirectory() {
        return homeDirectory != null ? homeDirectory.resolve("src") : null;
    }

    @Override
    public Path getMainJavaSourceDirectory() {
        return homeDirectory != null ? homeDirectory.resolve("src/main/java/") : null;
    }

    @Override
    public Path getTestJavaSourceDirectory() {
        return homeDirectory != null ? homeDirectory.resolve("src/test/java/") : null;
    }

    public Path getMainResourcesDirectory() {
        return homeDirectory != null ? homeDirectory.resolve("src/main/resources/") : null;
    }

    public Path getMetaInfJavaServicesDirectory() {
        return getMainResourcesDirectory().resolve("META-INF/services/");
    }

    public DevGwtModuleFile getGwtModuleFile() {
        if (gwtModuleFile == null)
            gwtModuleFile = new DevGwtModuleFile(this);
        return gwtModuleFile;
    }

    public DevGwtHtmlFile getGwtHtmlFile() {
        if (gwtHtmlFile == null)
            gwtHtmlFile = new DevGwtHtmlFile(this);
        return gwtHtmlFile;
    }

    public DevProjectModule getOrCreateChildProjectModule(String name) {
        return getOrCreateDevProjectModule(homeDirectory.resolve(name).normalize(), this);
    }

    DevProjectModule getOrCreateDevProjectModule(Path homeDirectory, DevProjectModule parentModule) {
        return getModuleRegistry().getOrCreateDevProjectModule(homeDirectory, parentModule);
    }

    public boolean hasSourceDirectory() {
        if (hasSourceDirectory == null)
            hasSourceDirectory = pathExists(getSourceDirectory());
        return hasSourceDirectory;
    }

    private static boolean pathExists(Path path) {
        return path != null && Files.exists(path);
    }

    @Override
    public boolean hasMainJavaSourceDirectory() {
        if (hasMainJavaSourceDirectory == null)
            hasMainJavaSourceDirectory = hasSourceDirectory() && pathExists(getMainJavaSourceDirectory());
        return hasMainJavaSourceDirectory;
    }

    public boolean hasTestJavaSourceDirectory() {
        if (hasTestJavaSourceDirectory == null)
            hasTestJavaSourceDirectory = hasSourceDirectory() && pathExists(getTestJavaSourceDirectory());
        return hasTestJavaSourceDirectory;
    }

    public DevJavaModuleInfoFile getMainJavaModuleFile() {
        if (mainJavaModuleInfoFile == null)
            mainJavaModuleInfoFile = new DevJavaModuleInfoFile(this);
        return mainJavaModuleInfoFile;
    }

    @Override
    public ReusableStream<String> getSubdirectoriesChildrenModules() {
        return ReusableStream.create(() -> SplitFiles.uncheckedWalk(getHomeDirectory(), 1))
                .filter(Files::isDirectory)
                .filter(path -> !path.equals(getHomeDirectory()))
                .filter(path -> Files.exists(path.resolve("webfx.xml")) || Files.exists(path.resolve("pom.xml")))
                .map(path -> path.toFile().getName())
                .sorted()
                .cache();
    }

    public DevRootModule getRootModule() {
        return (DevRootModule) super.getRootModule();
    }


    boolean isModuleUnderRootHomeDirectory(Module module) {
        Path homeDirectory = module instanceof DevProjectModule ? ((DevProjectModule) module).getHomeDirectory() : null;
        return homeDirectory != null && homeDirectory.startsWith(getRootModule().getHomeDirectory());
    }

    public void rename(String newName) {
        name = newName;
        artifactId = null;
    }

}