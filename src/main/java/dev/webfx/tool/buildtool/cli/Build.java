package dev.webfx.tool.buildtool.cli;

import dev.webfx.tool.buildtool.MavenCaller;
import picocli.CommandLine.Command;

/**
 * @author Bruno Salmon
 */
@Command(name = "build", description = "Invoke Maven build.")
final class Build extends CommonSubcommand implements Runnable {

    @Override
    public void run() {
        MavenCaller.invokeMavenGoal("install -Pgwt-compile");
    }
}
