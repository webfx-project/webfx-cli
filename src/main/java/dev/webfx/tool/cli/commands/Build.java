package dev.webfx.tool.cli.commands;

import dev.webfx.tool.cli.core.MavenCaller;
import dev.webfx.tool.cli.util.os.OperatingSystem;
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

    @CommandLine.Option(names = {"--gluon-mobile"}, description = "Includes the Gluon native mobile compilation")
    private boolean mobile;

    @CommandLine.Option(names = {"--gluon-android"}, description = "Includes the Gluon native android compilation")
    private boolean android;

    @CommandLine.Option(names = {"--gluon-ios"}, description = "Includes the Gluon native isOS compilation")
    private boolean ios;

    @Override
    public void run() {
        if (mobile) {
            if (OperatingSystem.isMacOs())
                ios = true;
            else
                android = true;
        }
        boolean gluon = desktop || android || ios;
        if (!fatjar && !gwt && !gluon)
            fatjar = gwt = true;
        MavenCaller.invokeMavenGoal(gluon ? "install " : "package " +
                        (fatjar ? "-P openjfx-fatjar " : "") +
                        (gwt ? "-P gwt-compile " : "")
                , new ProcessCall().setWorkingDirectory(getProjectDirectoryPath()));
        if (desktop)
            invokeGluonGoal("gluon-desktop");
        if (android)
            invokeGluonGoal("gluon-android");
        if (ios)
            invokeGluonGoal("gluon-ios");
    }

    private void invokeGluonGoal(String gluonProfile) {
        MavenCaller.invokeMavenGoal("-P '" + gluonProfile + "' gluonfx:build gluonfx:package"
                , new ProcessCall()
                        .setWorkingDirectory(getWorkingDevProjectModule().getHomeDirectory())
        );
    }
}
