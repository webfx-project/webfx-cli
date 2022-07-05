package dev.webfx.tool.cli.commands;

import picocli.CommandLine.Option;

/**
 * @author Bruno Salmon
 */
abstract class CommonSubcommand extends CommonCommand {

    @Option(names={"-H", "--help"}, usageHelp = true, description="Show this help message and exit.")
    private boolean help;

}
