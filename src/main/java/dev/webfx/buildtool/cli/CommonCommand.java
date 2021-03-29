package dev.webfx.buildtool.cli;

import dev.webfx.buildtool.*;
import dev.webfx.buildtool.Module;
import picocli.CommandLine;
import picocli.CommandLine.Help.Ansi.IStyle;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static picocli.CommandLine.Help.Ansi.Style.*;

/**
 * @author Bruno Salmon
 */
class CommonCommand {

    private final static CommandLine.Help.ColorScheme COLOR_SCHEME = CommandLine.Help.defaultColorScheme(CommandLine.Help.Ansi.AUTO);

    private final static List<IStyle> MODULE_STYLES = List.of(fg_cyan);
    private final static List<IStyle> FILE_STYLES = List.of(fg_green);
    private final static List<IStyle> PACKAGE_STYLES = List.of(fg_magenta);
    private final static List<IStyle> DEPENDENCY_STYLES = List.of(fg_yellow);

    enum LogLevel {
        error(fg_red, bold),
        warning(fg_yellow, bold),
        info(COLOR_SCHEME.commandStyles()), // bold white
        verbose(fg_white); // Actually gray

        final List<IStyle> styles;

        LogLevel(IStyle... styles) {
            this(List.of(styles));
        }

        LogLevel(List<IStyle> styles) {
            this.styles = styles;
        }
    }

    @ParentCommand
    protected CommonCommand parentCommand;

    @Option(names={"--log"}, description="Change the log level.")
    private LogLevel logLevel;

    public LogLevel getLogLevel() {
        return logLevel != null ? logLevel : parentCommand != null ? parentCommand.getLogLevel() : null;
    }

    @Option(names = {"-d", "--directory"}, description = "Directory of the webfx.xml project.")
    private String projectDirectory;

    public String getProjectDirectory() {
        return projectDirectory != null ? projectDirectory : parentCommand != null ? parentCommand.getProjectDirectory() : "";
    }

    @Option(names = {"-m", "--module"}, description = "Name of the working module.")
    private String moduleName;

    public String getModuleName() {
        return moduleName != null || parentCommand == null ? moduleName : parentCommand.getModuleName();
    }

    protected void setUpLogger() {
        Logger.setLogConsumer(object -> {
            String message = null;
            List<IStyle> styles = null;

            if (object instanceof Module)
                styles = MODULE_STYLES;
            else if (object instanceof JavaFile)
                styles = FILE_STYLES;
            else if (object instanceof ModuleDependency)
                styles = DEPENDENCY_STYLES;
            else {
                message = object.toString();
                if (message.startsWith("PACKAGE:")) {
                    styles = PACKAGE_STYLES;
                    message = message.substring(8).trim();
                } else {
                    LogLevel messageLogLevel =
                            message.startsWith("ERROR:")   ? LogLevel.error :
                            message.startsWith("WARNING:") ? LogLevel.warning :
                            message.startsWith("INFO:")    ? LogLevel.info :
                            message.startsWith("VERBOSE:") ? LogLevel.verbose :
                            null;
                    if (messageLogLevel == null)
                        styles = LogLevel.info.styles;
                    else {
                        LogLevel logLevel = getLogLevel();
                        if (messageLogLevel.ordinal() <= (logLevel == null ? LogLevel.error : logLevel).ordinal())
                            styles = messageLogLevel.styles;
                    }
                }
            }

            if (styles != null) {
                if (message == null)
                    message = object.toString();
                System.out.println(COLOR_SCHEME.apply(message, styles));
            }
        });
    }

    protected void log(Object o) {
        Logger.log(o);
    }

    private ModuleRegistry moduleRegistry;
    private Path projectDirectoryPath;
    private Path topRootDirectoryPath;
    private Path workspaceDirectoryPath;
    private Module moduleProject;

    public Path getProjectDirectoryPath() {
        if (projectDirectoryPath == null)
            projectDirectoryPath = Path.of(getProjectDirectory()).toAbsolutePath();
        return projectDirectoryPath;
    }

    public Path getTopRootDirectoryPath() {
        if (topRootDirectoryPath == null)
            topRootDirectoryPath = getTopRootDirectory(getProjectDirectoryPath());
        return topRootDirectoryPath;
    }

    public Path getWorkspaceDirectoryPath() {
        if (workspaceDirectoryPath == null)
            workspaceDirectoryPath = getTopRootDirectoryPath() == null ? getProjectDirectoryPath() : getTopRootDirectoryPath().getParent();
        return workspaceDirectoryPath;
    }

    protected ModuleRegistry getModuleRegistry() {
        if (moduleRegistry == null)
            moduleRegistry = new ModuleRegistry(getWorkspaceDirectoryPath(),
                    "webfx",
                    "webfx-platform",
                    "webfx-lib-javacupruntime",
                    "webfx-lib-odometer",
                    "webfx-lib-enzo",
                    "webfx-lib-medusa",
                    "webfx-lib-reusablestream",
                    "webfx-extras",
                    "webfx-extras-flexbox",
                    "webfx-extras-materialdesign",
                    "webfx-extras-webtext",
                    "webfx-extras-visual",
                    "webfx-extras-visual-charts",
                    "webfx-extras-visual-grid",
                    "webfx-extras-cell",
                    "webfx-stack-platform",
                    "webfx-framework"
            );
        return moduleRegistry;
    }

    private static Path getTopRootDirectory(Path projectDirectory) {
        if (!hasProjectFile(projectDirectory))
            return null;
        Path topRootDirectory = projectDirectory;
        while (true) {
            Path parent = topRootDirectory.getParent();
            if (!hasProjectFile(parent))
                return topRootDirectory;
            topRootDirectory = parent;
        }
    }

    private static boolean hasProjectFile(Path projectDirectory) {
        return Files.exists(projectDirectory.resolve("webfx.xml")) || Files.exists(projectDirectory.resolve("pom.xml"));
    }

    protected Module getWorkingModule() {
        if (moduleProject == null) {
            String moduleName = getModuleName();
            if (moduleName == null)
                return getModuleRegistry().getOrCreateProjectModule(projectDirectoryPath);
            ProjectModule topProjectModule = getModuleRegistry().getOrCreateProjectModule(topRootDirectoryPath);
            if (moduleName.equals("top") || moduleName.equals(topProjectModule.getName()) )
                return topProjectModule;
            moduleProject = ((RootModule) topProjectModule).findModule(moduleName, false);
        }
        return moduleProject;
    }

    protected ProjectModule getWorkingProjectModule() {
        Module workingModule = getWorkingModule();
        if (workingModule instanceof ProjectModule)
            return (ProjectModule) workingModule;
        throw new BuildException(workingModule.getName() + " is not a project module.");
    }
}
