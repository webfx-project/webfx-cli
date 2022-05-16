package dev.webfx.tool.cli.subcommands;

import dev.webfx.tool.cli.core.MavenCaller;
import picocli.CommandLine.Command;

/**
 * @author Bruno Salmon
 */
@Command(name = "build", description = "Invoke Maven build.")
public final class Build extends CommonSubcommand implements Runnable {

    @Override
    public void run() {
        MavenCaller.invokeMavenGoal("install -Pgwt-compile");
    }
}
