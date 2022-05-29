package dev.webfx.tool.cli.subcommands;

import dev.webfx.tool.cli.core.MavenCaller;
import picocli.CommandLine;
import picocli.CommandLine.Command;

/**
 * @author Bruno Salmon
 */
@Command(name = "build", description = "Invoke Maven build.")
public final class Build extends CommonSubcommand implements Runnable {

    @CommandLine.Option(names = {"--fatjar"}, description = "Creates a fat jar for the OpenJFX version", defaultValue = "true")
    private boolean fatjar;

    @CommandLine.Option(names = {"--gwt"}, description = "Includes the GWT compilation", defaultValue = "true")
    private boolean gwt;

    @CommandLine.Option(names = {"--desktop"}, description = "Includes the GWT compilation", defaultValue = "false")
    private boolean desktop;


    @Override
    public void run() {
        MavenCaller.invokeMavenGoal("package " +
                (fatjar ? "-P openjfx-fatjar " : "") +
                (gwt ? "-P gwt-compile " : "") +
                (desktop ? "-P gluon-desktop " : "") +
                ""
        );
    }
}
