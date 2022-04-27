package dev.webfx.tool.buildtool.cli;

import picocli.CommandLine.Command;

/**
 * @author Bruno Salmon
 */
@Command(name = "shell", description = "Enter shell mode.")
final class Shell extends CommonSubcommand implements Runnable {

    @Override
    public void run() {
    }
}
