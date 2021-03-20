package dev.webfx.buildtool.cli;

import dev.webfx.buildtool.Logger;
import dev.webfx.buildtool.RootModule;
import picocli.CommandLine;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

import java.util.List;

import static picocli.CommandLine.Help.Ansi.Style.*;

/**
 * @author Bruno Salmon
 */
class CommonCommand {

    private final static CommandLine.Help.ColorScheme COLOR_SCHEME = CommandLine.Help.defaultColorScheme(CommandLine.Help.Ansi.AUTO);

    enum LogLevel {
        error(fg_red, bold),
        warning(fg_yellow, bold),
        info(COLOR_SCHEME.commandStyles()), // bold white
        verbose(fg_white); // Actually gray

        final List<CommandLine.Help.Ansi.IStyle> styles;

        LogLevel(CommandLine.Help.Ansi.IStyle... styles) {
            this(List.of(styles));
        }

        LogLevel(List<CommandLine.Help.Ansi.IStyle> styles) {
            this.styles = styles;
        }
    }

    @ParentCommand
    protected CommonCommand parentCommand;

    @Option(names={"--log"}, description="Change the log level.")
    private CommonSubcommand.LogLevel logLevel;

    public LogLevel getLogLevel() {
        return logLevel != null ? logLevel : parentCommand != null ? parentCommand.getLogLevel() : LogLevel.info;
    }

    public String getRootDirectory() {
        return rootDirectory != null ? rootDirectory : parentCommand != null ? parentCommand.getRootDirectory() : "";
    }

    protected void setUpLogger() {
        Logger.setLogConsumer(message -> {
            CommonSubcommand.LogLevel messageLogLevel =
                    message.startsWith("ERROR:")   ? CommonSubcommand.LogLevel.error :
                    message.startsWith("WARNING:") ? CommonSubcommand.LogLevel.warning :
                    message.startsWith("VERBOSE:") ? CommonSubcommand.LogLevel.verbose :
                    CommonSubcommand.LogLevel.info;
            if (messageLogLevel.ordinal() <= getLogLevel().ordinal())
                System.out.println(COLOR_SCHEME.apply(message, messageLogLevel.styles));
        });
    }

    @Option(names = {"-d", "--directory"}, description = "Directory of the webfx.xml project.")
    private String rootDirectory;

    protected RootModule getRootModule() {
        return new RootModule(getRootDirectory(),
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
    }
}
