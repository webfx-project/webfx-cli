package dev.webfx.tool.cli.commands;

import dev.webfx.lib.reusablestream.ReusableStream;
import dev.webfx.tool.cli.core.*;
import dev.webfx.tool.cli.modulefiles.DevMavenPomModuleFile;
import dev.webfx.tool.cli.util.os.OperatingSystem;
import dev.webfx.tool.cli.util.process.ProcessCall;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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
    private final boolean raiseExceptionIfEmpty;
    private final boolean returnGluonModuleOnly;

    public BuildRunCommon(boolean gwt, boolean fatjar, boolean openJfxDesktop, boolean gluonDesktop, boolean android, boolean ios, boolean locate, boolean reveal, boolean raiseExceptionIfEmpty, boolean returnGluonModuleOnly) {
        this.gwt = gwt;
        this.fatjar = fatjar;
        this.openJfxDesktop = openJfxDesktop;
        this.gluonDesktop = gluonDesktop;
        this.android = android;
        this.ios = ios;
        this.locate = locate;
        this.reveal = reveal;
        this.raiseExceptionIfEmpty = raiseExceptionIfEmpty;
        this.returnGluonModuleOnly = returnGluonModuleOnly;
    }

    public void findAndConsumeExecutableModule(DevProjectModule workingModule, DevProjectModule topRootModule, Consumer<DevProjectModule> executableModuleConsumer) {
        ReusableStream<DevProjectModule> executableModules = findExecutableModules(workingModule);
        if (executableModules.isEmpty()) {
            executableModules = findExecutableModules(topRootModule);
            if (!executableModules.isEmpty())
                Logger.log("NOTE: No executable module under " + workingModule + " so searching over the whole repository");
            else if (raiseExceptionIfEmpty)
                throw new CliException("No executable module found");
        }
        if (locate || reveal) {
            if (locate)
                executableModules.flatMap(this::getExecutableFilePath).forEach(Logger::log);
            if (reveal)
                executableModules.flatMap(this::getExecutableFilePath).forEach(BuildRunCommon::revealFile);
            return;
        }
        if (returnGluonModuleOnly)
            executableModules = executableModules.filter(m -> m.getTarget().hasTag(TargetTag.GLUON));
        if (executableModules.count() > 1)
            throw new CliException("Ambiguous executable modules. Please add one of the following options:\n" + executableModules.map(m -> "-m " + m.getName()).collect(Collectors.joining("\n")));
        DevProjectModule executableModule = executableModules.findFirst().orElse(null);
        executableModuleConsumer.accept(executableModule);
    }


    ReusableStream<DevProjectModule> findExecutableModules(DevProjectModule startingModule) {
        boolean openJfx = fatjar || openJfxDesktop;
        boolean gluon = gluonDesktop || android || ios;
        ReusableStream<ProjectModule> executableModules = startingModule
                .getThisAndChildrenModulesInDepth()
                .filter(ProjectModule::isExecutable)
                .filter(m -> m.isExecutable(Platform.GWT) ? gwt : m.getTarget().hasTag(TargetTag.OPENJFX) ? openJfx : !m.getTarget().hasTag(TargetTag.GLUON) || gluon);
        return executableModules
                .map(DevProjectModule.class::cast)
                ;
    }

    private ReusableStream<Path> getExecutableFilePath(DevProjectModule module) {
        List<Path> executablePaths = new ArrayList<>();

        Path targetPath = module.getHomeDirectory().resolve("target");
        if (module.isExecutable(Platform.GWT)) {
            if (gwt)
                executablePaths.add(targetPath.resolve(module.getName() + "-" + module.getVersion() + "/" + module.getName().replace('-', '_') + "/index.html"));
        } else if (module.isExecutable(Platform.JRE)) {
            if (module.getTarget().hasTag(TargetTag.OPENJFX)) {
                if (fatjar)
                    executablePaths.add(targetPath.resolve(module.getName() + "-" + module.getVersion() + "-fat.jar"));
            } else if (module.getTarget().hasTag(TargetTag.GLUON)) {
                String applicationName = DevMavenPomModuleFile.getApplicationName(module);
                switch (OperatingSystem.getOsFamily()) {
                    case LINUX:
                        if (gluonDesktop)
                            executablePaths.add(targetPath.resolve("gluonfx/x86_64-linux/" + applicationName));
                        if (android)
                            executablePaths.add(targetPath.resolve("gluonfx/aarch64-android/gvm/" + applicationName + ".apk"));
                        break;
                    case WINDOWS:
                        if (gluonDesktop)
                            executablePaths.add(targetPath.resolve("gluonfx/x86_64-windows/" + applicationName + ".exe"));
                        break;
                }
            }
        }

        return ReusableStream.fromIterable(executablePaths);
    }

    void executeModule(DevProjectModule module) {
        getExecutableFilePath(module).forEach(BuildRunCommon::executeFile);
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
                ProcessCall.executePowershellCommand(". " + ProcessCall.toShellLogCommandToken(executableFilePath.toString()));
            else
                ProcessCall.executeCommandTokens("open", executableFilePath.toString());
        else if (fileName.endsWith(".apk")) {
            Path ancestor = executableFilePath.getParent();
            while (ancestor != null && !Files.exists(ancestor.resolve("pom.xml")))
                ancestor = ancestor.getParent();
            if (ancestor != null)
                MavenCaller.invokeMavenGoal("-Pandroid gluonfx:install gluonfx:nativerun"
                        , new ProcessCall().setWorkingDirectory(ancestor));
        } else // Everything else should be an executable file that we can call directly
            ProcessCall.executeCommandTokens(executableFilePath.toString());
    }

    private static void revealFile(Path filePath) {
        if (!Files.exists(filePath))
            Logger.log("Can't reveal file " + filePath + " as it does not exist");
        else if (OperatingSystem.isMacOs())
            ProcessCall.executeCommandTokens("open", "--reveal", filePath.toString());
        else if (OperatingSystem.isLinux())
            ProcessCall.executeCommandTokens("nautilus", filePath.toString());
        else if (OperatingSystem.isWindows())
            ProcessCall.executeCommandTokens("explorer", "/select," + ProcessCall.toShellLogCommandToken(filePath.toString()));
    }

}
