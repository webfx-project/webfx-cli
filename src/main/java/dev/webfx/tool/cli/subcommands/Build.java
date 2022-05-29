package dev.webfx.tool.cli.subcommands;

import dev.webfx.tool.cli.core.MavenCaller;
import dev.webfx.tool.cli.util.process.ProcessCall;
import picocli.CommandLine;
import picocli.CommandLine.Command;

/**
 * @author Bruno Salmon
 */
@Command(name = "build", description = "Invoke Maven build.")
public final class Build extends CommonSubcommand implements Runnable {

    @CommandLine.Option(names = {"--openjfx-fatjar"}, description = "Creates a fat jar for the OpenJFX version")
    private boolean fatjar;

    @CommandLine.Option(names = {"--gwt-compile"}, description = "Includes the GWT compilation")
    private boolean gwt;

    @CommandLine.Option(names = {"--gluon-desktop"}, description = "Includes the Gluon native desktop compilation")
    private boolean desktop;


    @Override
    public void run() {
        if (!fatjar && !gwt && !desktop)
            fatjar = gwt = true;
        MavenCaller.invokeMavenGoal(desktop ? "install " : "package " +
                (fatjar ? "-P openjfx-fatjar " : "") +
                (gwt ? "-P gwt-compile " : "")
                , new ProcessCall().setWorkingDirectory(getProjectDirectoryPath()));
        if (desktop)
            MavenCaller.invokeMavenGoal("-X -P 'gluon-desktop' gluonfx:build gluonfx:package"
                    , new ProcessCall().setWorkingDirectory(getWorkingDevProjectModule().getHomeDirectory()));
    }
}
