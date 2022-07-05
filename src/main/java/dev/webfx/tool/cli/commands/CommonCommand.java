package dev.webfx.tool.cli.commands;

import dev.webfx.tool.cli.core.*;
import dev.webfx.tool.cli.core.Module;
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
public class CommonCommand {

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

    @Option(names = {"-D", "--directory"}, description = "Directory of the webfx.xml project.")
    private String projectDirectory;

    public String getProjectDirectory() {
        return projectDirectory != null ? projectDirectory : parentCommand != null ? parentCommand.getProjectDirectory() : "";
    }

    @Option(names = {"-M", "--module"}, description = "Name of the working module.")
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
    private Module workingModule;

    private DevProjectModule topRootModule;

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

    public void setWorkspaceDirectoryPath(Path workspaceDirectoryPath) {
        this.workspaceDirectoryPath = workspaceDirectoryPath;
    }

    protected ModuleRegistry getModuleRegistry() {
        if (moduleRegistry == null)
            moduleRegistry = new ModuleRegistry(getWorkspaceDirectoryPath());
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
        if (workingModule == null) {
            if (getTopRootDirectoryPath() == null)
                throw new CliException("Not a WebFX repository (or any of the parent directories): no webfx.xml");
            String moduleName = getModuleName();
            if (moduleName == null)
                return getModuleRegistry().getOrCreateDevProjectModule(projectDirectoryPath);
            DevProjectModule topProjectModule = getTopRootModule();
            if (moduleName.equals("top") || moduleName.equals(topProjectModule.getName()) )
                return topProjectModule;
            workingModule = topProjectModule.searchRegisteredModule(moduleName, false);
        }
        return workingModule;
    }

    protected ProjectModule getWorkingProjectModule() {
        Module workingModule = getWorkingModule();
        if (workingModule instanceof ProjectModule)
            return (ProjectModule) workingModule;
        throw new CliException(workingModule.getName() + " is not a project module.");
    }

    protected DevProjectModule getWorkingDevProjectModule() {
        Module workingModule = getWorkingModule();
        if (workingModule instanceof DevProjectModule)
            return (DevProjectModule) workingModule;
        throw new CliException(workingModule.getName() + " is not a project module.");
    }

    protected DevProjectModule getTopRootModule() {
        if (topRootModule == null)
            topRootModule = getModuleRegistry().getOrCreateDevProjectModule(topRootDirectoryPath);
        return topRootModule;
    }
}
