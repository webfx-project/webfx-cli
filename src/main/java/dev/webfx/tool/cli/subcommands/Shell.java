package dev.webfx.tool.cli.subcommands;

import picocli.CommandLine.Command;

/**
 * @author Bruno Salmon
 */
@Command(name = "shell", description = "Enter shell mode.")
public final class Shell extends CommonSubcommand implements Runnable {

    @Override
    public void run() {
    }
}
