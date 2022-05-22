package dev.webfx.tool.cli;

import dev.webfx.tool.cli.core.WebFxCliException;
import dev.webfx.tool.cli.subcommands.*;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help;
import picocli.CommandLine.Help.ColorScheme;
import picocli.CommandLine.IHelpFactory;
import picocli.CommandLine.Model.ArgSpec;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.PositionalParamSpec;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Properties;
import java.util.stream.Collectors;

/**
 * @author Bruno Salmon
 */
@Command(name = "webfx", description = " __        __   _     _______  __\n" +
        " \\ \\      / ___| |__ |  ___\\ \\/ /\n" +
        "  \\ \\ /\\ / / _ | '_ \\| |_   \\  /\n" +
        "   \\ V  V |  __| |_) |  _|  /  \\\n" +
        "    \\_/\\_/ \\___|_.__/|_|   /_/\\_\\\n",
        subcommands = {
                Init.class,
                Create.class,
                Build.class,
                Run.class,
                Update.class,
                Bump.class,
                //STream.class,
                //Rename.class,
                //Move.class,
                //Conf.class,
                //Shell.class,
                //Watch.class,
        },
        mixinStandardHelpOptions = true,
        versionProvider = WebFx.DevVersionProvider.class)
public final class WebFx extends CommonCommand {

    public static void main(String... args) {
        System.exit(executeCommand(args));
    }

    public static int executeCommand(String... args) {
        return new CommandLine(new WebFx())
                .setHelpFactory(new HelpFactory())
                .setExecutionExceptionHandler((ex, commandLine, parseResult) -> {
                    // Removing the stack trace if this is raised by the cli tool (just showing the short message)
                    if (ex instanceof WebFxCliException)
                        ex.setStackTrace(new StackTraceElement[0]);
                    throw ex;
                })
                .execute(args);
    }

    private static class HelpFactory implements IHelpFactory {
        @Override
        public Help create(CommandSpec commandSpec, ColorScheme colorScheme) {
            return new Help(commandSpec, colorScheme) {
                @Override
                public String parameterList(java.util.List<PositionalParamSpec> positionalParams) {
                    return super.parameterList(positionalParams.stream().filter(this::isNotKeywordParameter).collect(Collectors.toList()));
                }

                boolean isNotKeywordParameter(PositionalParamSpec param) {
                    return !Keywords.Keyword.class.isAssignableFrom(param.auxiliaryTypes()[0]);
                }

                @Override
                protected Ansi.Text createDetailedSynopsisPositionalsText(Collection<ArgSpec> done) {
                    Ansi.Text positionalParamText = ansi().new Text(0);
                    java.util.List<PositionalParamSpec> positionals = new ArrayList<>(commandSpec.positionalParameters()); // iterate in declaration order
                    if (hasAtFileParameter()) {
                        positionals.add(0, AT_FILE_POSITIONAL_PARAM);
                        AT_FILE_POSITIONAL_PARAM.messages(commandSpec.usageMessage().messages());
                    }
                    positionals.removeAll(done);
                    for (PositionalParamSpec positionalParam : positionals) {
                        positionalParamText = concatPositionalText(" ", positionalParamText, colorScheme, positionalParam, parameterLabelRenderer());
                    }
                    return positionalParamText;
                }

                Ansi.Text concatPositionalText(String prefix, Ansi.Text text, ColorScheme colorScheme, PositionalParamSpec positionalParam, IParamLabelRenderer parameterLabelRenderer) {
                    if (!positionalParam.hidden()) {
                        Ansi.Text label = parameterLabelRenderer.renderParameterLabel(positionalParam, colorScheme.ansi(),
                                // Testing if the parameter is a keyword parameter
                                isNotKeywordParameter(positionalParam) ?
                                        colorScheme.parameterStyles() : // no => will appear with standard parameter color
                                        colorScheme.commandStyles()     // yes => will appear as white (like for commands)
                        );
                        text = text.concat(prefix).concat(label);
                    }
                    return text;
                }
            };
        }
    }

    public static class DevVersionProvider implements CommandLine.IVersionProvider {
        @Override
        public String[] getVersion() throws Exception {
            return new String[] { WebFx.getVersion() };
        }
    }

    public static String getVersion() {
        try (InputStream pis = WebFx.class.getClassLoader().getResourceAsStream("dev/webfx/tool/cli/version/dev/version.ini")) {
            Properties devVersionProperties = new Properties();
            devVersionProperties.load(pis);
            return devVersionProperties.getProperty("version") + " ~ " + devVersionProperties.getProperty("build.timestamp") + " GMT";
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}