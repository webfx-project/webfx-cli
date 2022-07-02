package dev.webfx.tool.cli.commands;

import dev.webfx.lib.reusablestream.ReusableStream;
import dev.webfx.tool.cli.core.*;
import dev.webfx.tool.cli.util.os.OperatingSystem;
import dev.webfx.tool.cli.util.process.ProcessCall;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * @author Bruno Salmon
 */
final class BuildRunCommon {

    private final boolean gwt;
    private final boolean fatjar;
    private final boolean openJfxDesktop;
    private final boolean gluonDesktop;
    private final boolean android;
    private final boolean ios;
    private final boolean locate;
    private final boolean reveal;

    public BuildRunCommon(boolean gwt, boolean fatjar, boolean openJfxDesktop, boolean gluonDesktop, boolean android, boolean ios, boolean locate, boolean reveal) {
        this.gwt = gwt;
        this.fatjar = fatjar;
        this.openJfxDesktop = openJfxDesktop;
        this.gluonDesktop = gluonDesktop;
        this.android = android;
        this.ios = ios;
        this.locate = locate;
        this.reveal = reveal;
    }

    public void findAndConsumeExecutableModule(DevProjectModule workingModule, DevProjectModule topRootModule, Consumer<DevProjectModule> executableModuleConsumer) {
        ReusableStream<DevProjectModule> executableModules = findExecutableModules(workingModule);
        if (executableModules.isEmpty()) {
            executableModules = findExecutableModules(topRootModule);
            if (executableModules.isEmpty())
                throw new CliException("No executable module found");
            Logger.log("NOTE: No executable module under " + workingModule + " so searching over the whole repository");
        }
        if (locate || reveal) {
            if (locate)
                executableModules.map(BuildRunCommon::getExecutableFilePath).filter(Objects::nonNull).forEach(Logger::log);
            if (reveal)
                executableModules.map(BuildRunCommon::getExecutableFilePath).filter(Objects::nonNull).forEach(BuildRunCommon::revealFile);
            return;
        }
        if (executableModules.count() > 1)
            throw new CliException("Ambiguous executable modules. Please add one of the following options:\n" + executableModules.map(m -> "-m " + m.getName()).collect(Collectors.joining("\n")));
        DevProjectModule executableModule = executableModules.findFirst().orElse(workingModule);
        executableModuleConsumer.accept(executableModule);
    }


    ReusableStream<DevProjectModule> findExecutableModules(DevProjectModule startingModule) {
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

    private static Path getExecutableFilePath(DevProjectModule module) {
        Path targetPath = module.getHomeDirectory().resolve("target");
        if (module.isExecutable(Platform.JRE) && module.getTarget().hasTag(TargetTag.OPENJFX))
            return targetPath.resolve( module.getName() + "-" + module.getVersion() + "-fat.jar");
        if (module.isExecutable(Platform.GWT))
            return targetPath.resolve(module.getName() + "-" + module.getVersion() + "/" + module.getName().replace('-', '_') + "/index.html");
        return null;
    }

    static void executeModule(DevProjectModule module) {
        executeFile(getExecutableFilePath(module));
    }

    private static void executeFile(Path executableFilePath) {
        if (executableFilePath == null)
            throw new CliException("Unknown executable file location");
        String fileName = executableFilePath.getFileName().toString();
        if (!Files.exists(executableFilePath))
            throw new CliException("The file " + executableFilePath + " does not exist");
        else if (fileName.endsWith(".jar"))
            ProcessCall.executeCommandTokens("java", "-jar", executableFilePath.toString());
        else if (fileName.endsWith(".html"))
            if (OperatingSystem.isWindows())
                ProcessCall.executePowershellCommand(". " + executableFilePath);
            else
                ProcessCall.executeCommandTokens("open", executableFilePath.toString());
        else
            throw new CliException("Unsupported execution file " + executableFilePath);
    }

    private static void revealFile(Path filePath) {
        if (!Files.exists(filePath))
            throw new CliException("The file " + filePath + " does not exist");
        if (OperatingSystem.isMacOs())
            ProcessCall.executeCommandTokens("open", "--reveal", filePath.toString());
        else if (OperatingSystem.isLinux())
            ProcessCall.executeCommandTokens("nautilus", filePath.toString());
        else if (OperatingSystem.isWindows())
            ProcessCall.executeCommandTokens("explorer", "/select," + ProcessCall.toShellLogCommandToken(filePath.toString()));
    }

}
