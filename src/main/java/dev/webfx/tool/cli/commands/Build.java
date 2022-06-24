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

    @CommandLine.Option(names = {"--openjfx-desktop"}, description = "Includes the OpenJFX desktop build")
    private boolean ojfxDesktop;

    @CommandLine.Option(names = {"--gluon-desktop"}, description = "Includes the Gluon native desktop build")
    private boolean gluonDesktop;

    @CommandLine.Option(names = {"--gluon-mobile"}, description = "Includes the Gluon native mobile build")
    private boolean mobile;

    @CommandLine.Option(names = {"--gluon-android"}, description = "Includes the Gluon native android build")
    private boolean android;

    @CommandLine.Option(names = {"--gluon-ios"}, description = "Includes the Gluon native isOS build")
    private boolean ios;

    @Override
    public void run() {
        if (mobile) {
            if (OperatingSystem.isMacOs())
                ios = true;
            else
                android = true;
        }
        boolean gluon = gluonDesktop || android || ios;
        if (!fatjar && !gwt && !ojfxDesktop && !gluon)
            fatjar = gwt = true;
        MavenCaller.invokeMavenGoal(gluon ? "install " : "package " +
                        (fatjar ? "-P openjfx-fatjar " : "") +
                        (ojfxDesktop ? "-P openjfx-desktop " : "") +
                        (gwt ? "-P gwt-compile " : "")
                , new ProcessCall().setWorkingDirectory(getProjectDirectoryPath()));
        if (gluonDesktop) {
            if (OperatingSystem.isWindows())
                new ProcessCall()
                        .setCommand("Import-Module '" + System.getenv("ProgramFiles(X86)") + "\\Microsoft Visual Studio\\2022\\BuildTools\\Common7\\Tools\\Microsoft.VisualStudio.DevShell.dll'" +
                                "; Enter-VsDevShell 3a7e2912 -DevCmdArguments '-arch=x64'" +
                                "; $env:GRAALVM_HOME = '" + Bump.getGraalVmHome() + "'" +
                                "; mvn -D'plugin.gluonfx.param.target'=host gluonfx:build gluonfx:package")
                        .setPowershellCommand(true)
                        .executeAndWait();
            else
                invokeGluonGoal("gluon-desktop");
        }
        if (android)
            invokeGluonGoal("gluon-android");
        if (ios)
            invokeGluonGoal("gluon-ios");
    }

    private void invokeGluonGoal(String gluonProfile) {
        MavenCaller.invokeMavenGoal("-P " + gluonProfile + " gluonfx:build gluonfx:package"
                , new ProcessCall()
                        .setWorkingDirectory(getWorkingDevProjectModule().getHomeDirectory())
        );
    }
}
