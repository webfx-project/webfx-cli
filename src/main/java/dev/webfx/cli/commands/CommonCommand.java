package dev.webfx.cli.commands;

import dev.webfx.cli.core.JavaFile;
import dev.webfx.cli.core.Logger;
import dev.webfx.cli.core.Module;
import dev.webfx.cli.core.ModuleDependency;
import picocli.CommandLine;
import picocli.CommandLine.Help.Ansi.IStyle;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

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

    private CommandWorkspace workspace;

    public CommandWorkspace getWorkspace() {
        if (workspace == null)
            workspace = new CommandWorkspace(getProjectDirectory(), getModuleName());
        return workspace;
    }

    protected void setUpLogger() {
        Logger.setLogTransformer(object -> {
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
                return COLOR_SCHEME.apply(message, styles);
            }

            return null;
        });
    }

    protected static void log(Object o) {
        Logger.log(o);
    }

}
