package dev.webfx.buildtool.cli;

import picocli.CommandLine.Command;

/**
 * @author Bruno Salmon
 */
@Command(name = "build", description = "Invoke Maven build.")
final class Build extends CommonSubcommand implements Runnable {

    @Override
    public void run() {
    }
}
