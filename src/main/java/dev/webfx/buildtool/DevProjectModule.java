package dev.webfx.buildtool;

import dev.webfx.buildtool.modulefiles.*;
import dev.webfx.buildtool.util.splitfiles.SplitFiles;
import dev.webfx.tools.util.reusablestream.ReusableStream;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * @author Bruno Salmon
 */
public class DevProjectModule extends ProjectModuleImpl {

    private final Path homeDirectory;
    private Boolean hasSourceDirectory;
    private Boolean hasJavaSourceDirectory;
    private DevJavaModuleInfoFile javaModuleInfoFile;
    private DevWebFxModuleFile webFxModuleFile;
    private DevMavenPomModuleFile mavenPomModuleFile;

    private DevGwtModuleFile gwtModuleFile;
    private DevGwtHtmlFile gwtHtmlFile;

    /************************
     ***** Constructors *****
     ************************/

    public DevProjectModule(Path homeDirectory, ProjectModule parentModule) {
        super(homeDirectory.toAbsolutePath().getFileName().toString(), parentModule);
        this.homeDirectory = homeDirectory;
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
    public Path getJavaSourceDirectory() {
        return homeDirectory != null ? homeDirectory.resolve("src/main/java/") : null;
    }

    public Path getResourcesDirectory() {
        return homeDirectory != null ? homeDirectory.resolve("src/main/resources/") : null;
    }

    private Path getMetaInfJavaServicesDirectory() {
        return getResourcesDirectory().resolve("META-INF/services/");
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
    public boolean hasJavaSourceDirectory() {
        if (hasJavaSourceDirectory == null)
            hasJavaSourceDirectory = hasSourceDirectory() && pathExists(getJavaSourceDirectory());
        return hasJavaSourceDirectory;
    }

    public DevJavaModuleInfoFile getJavaModuleFile() {
        if (javaModuleInfoFile == null)
            javaModuleInfoFile = new DevJavaModuleInfoFile(this);
        return javaModuleInfoFile;
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

    public static ReusableStream<DevProjectModule> filterDevProjectModules(ReusableStream<Module> modules) {
        return modules
                .filter(m -> m instanceof DevProjectModule)
                .map(m -> (DevProjectModule) m);
    }

}