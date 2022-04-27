package dev.webfx.tool.buildtool.cli;

import picocli.CommandLine.Command;

/**
 * @author Bruno Salmon
 */
@Command(name = "watch", description = "Enter watch mode.")
final class Watch extends CommonSubcommand implements Runnable {

    @Override
    public void run() {
    }
}
