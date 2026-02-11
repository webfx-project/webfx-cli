package dev.webfx.cli.commands;

import picocli.CommandLine;

/**
 * @author Bruno Salmon
 */
abstract class FollowedByUpdateSubCommand extends CommonSubcommand {

    @CommandLine.Option(names = {"-s", "--skipUpdate"}, description = "Skip `webfx update` after creation.")
    boolean skipUpdate;

    protected void runUpdateIfNotSkipped() { // This method can be called subclasses
        if (!skipUpdate)
            new Update().run();
    }
}
