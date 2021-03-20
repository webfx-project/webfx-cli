package dev.webfx.buildtool.cli;

import picocli.CommandLine.Command;

/**
 * @author Bruno Salmon
 */
@Command(name = "analyze", description = "Analyze modules dependencies.")
final class Analyze extends CommonSubcommand implements Runnable {

    @Override
    public void run() {
    }
}
