package dev.webfx.tool.cli.commands;

import dev.webfx.lib.reusablestream.ReusableStream;
import dev.webfx.tool.cli.core.*;
import dev.webfx.tool.cli.util.os.OperatingSystem;
import dev.webfx.tool.cli.util.process.ProcessCall;
import picocli.CommandLine;
import picocli.CommandLine.Command;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @author Bruno Salmon
 */
@Command(name = "run", description = "Run a WebFX application.")
public final class Run extends CommonSubcommand implements Runnable {

    @CommandLine.Option(names = {"--gwt"}, description = "Runs the GWT app")
    private boolean gwt;

    @CommandLine.Option(names = {"--openjfx-fatjar"}, description = "Runs the OpenJFX fat jar")
    private boolean fatjar;

    @CommandLine.Option(names = {"--openjfx-desktop"}, description = "Runs the OpenJFX desktop app")
    private boolean openJfxDesktop;

    @CommandLine.Option(names = {"--gluon-desktop"}, description = "Runs the Gluon native desktop app")
    private boolean gluonDesktop;

    @CommandLine.Option(names = {"--gluon-mobile"}, description = "Same as --gluon-android on Linux, --gluon-ios on macOS")
    private boolean mobile;

    @CommandLine.Option(names = {"--gluon-android"}, description = "Runs the Gluon native Android app")
    private boolean android;

    @CommandLine.Option(names = {"--gluon-ios"}, description = "Runs the Gluon native iOS app")
    private boolean ios;

    @CommandLine.Option(names= {"-l", "--locate"}, description = "Just prints the location of the expected executable file")
    boolean locate;

    @CommandLine.Option(names= {"-r", "--reveal"}, description = "Just reveals the executable file in the file browser")
    boolean reveal;

    /*@Option(names= {"-b", "--build"}, description = "Build before running")
    boolean build;

    @Option(names= {"-p", "--port"}, description = "Port of the web server.")
    int port;*/

    @Override
    public void run() {
        ReusableStream<DevProjectModule> executableModules = findExecutableModules(false);
        if (executableModules.isEmpty()) {
            executableModules = findExecutableModules(true);
            if (executableModules.isEmpty())
                throw new CliException("No executable module found");
            log("NOTE: No executable module under " + getWorkingModule() + " so searching over the whole repository");
        }
        if (locate || reveal) {
            if (locate)
                executableModules.map(this::getExecutableFilePath).filter(Objects::nonNull).forEach(this::log);
            if (reveal)
                executableModules.map(this::getExecutableFilePath).filter(Objects::nonNull).forEach(Run::revealFile);
            return;
        }
        if (executableModules.count() > 1)
            throw new CliException("Ambiguous executable modules. Please add one of the following options:\n" + executableModules.map(m -> "-m " + m.getName()).collect(Collectors.joining("\n")));
        DevProjectModule module = executableModules.findFirst().orElse(getWorkingDevProjectModule());
        executeFile(getExecutableFilePath(module));
    }

    private ReusableStream<DevProjectModule> findExecutableModules(boolean topRoot) {
        DevProjectModule startingModule = topRoot ? getTopRootModule() : getWorkingDevProjectModule();
        boolean openJfx = fatjar || openJfxDesktop;
        boolean gluon = gluonDesktop || android || ios;
        ReusableStream<ProjectModule> executableModules = startingModule
                .getThisAndChildrenModulesInDepth()
                .filter(ProjectModule::isExecutable);
        if (gwt || openJfx || gluon)
                executableModules = executableModules
                        .filter(m -> gwt && m.isExecutable(Platform.GWT) || openJfx && m.getTarget().hasTag(TargetTag.OPENJFX) || gluon && m.getTarget().hasTag(TargetTag.GLUON));
        return executableModules
                .map(DevProjectModule.class::cast)
                ;
    }

    private Path getExecutableFilePath(DevProjectModule module) {
        Path targetPath = module.getHomeDirectory().resolve("target");
        if (module.isExecutable(Platform.JRE) && module.getTarget().hasTag(TargetTag.OPENJFX))
            return targetPath.resolve( module.getName() + "-" + module.getVersion() + "-fat.jar");
        if (module.isExecutable(Platform.GWT))
            return targetPath.resolve(module.getName() + "-" + module.getVersion() + "/" + module.getName().replace('-', '_') + "/index.html");
        return null;
    }

    private void executeFile(Path executableFilePath) {
        if (executableFilePath == null)
            throw new CliException("Unknown executable file location");
        String fileName = executableFilePath.getFileName().toString();
        if (!Files.exists(executableFilePath))
            throw new CliException("The file " + executableFilePath + " does not exist");
        else if (fileName.endsWith(".jar"))
            ProcessCall.execute("java -jar " + ProcessCall.encodeUnbreakableToken(executableFilePath));
        else if (fileName.endsWith(".html"))
            ProcessCall.execute((OperatingSystem.isWindows() ? "start " : "open ") + executableFilePath);
        else
            throw new CliException("Unsupported execution file " + executableFilePath);
    }

    private static void revealFile(Path filePath) {
        if (!Files.exists(filePath))
            throw new CliException("The file " + filePath + " does not exist");
        if (OperatingSystem.isMacOs())
            ProcessCall.execute("open --reveal " + ProcessCall.encodeUnbreakableToken(filePath));
        else if (OperatingSystem.isLinux())
            ProcessCall.execute("nautilus " + ProcessCall.encodeUnbreakableToken(filePath));
        else if (OperatingSystem.isWindows())
            ProcessCall.execute("explorer /select," + ProcessCall.encodeUnbreakableToken(filePath));
    }

}
