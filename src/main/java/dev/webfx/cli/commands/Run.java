package dev.webfx.cli.commands;

import dev.webfx.cli.util.os.OperatingSystem;
import picocli.CommandLine;
import picocli.CommandLine.Command;

/**
 * @author Bruno Salmon
 */
@Command(name = "run", description = "Run a WebFX application.")
public final class Run extends CommonSubcommand implements Runnable {

    @CommandLine.Option(names = {"-g", "--gwt"}, description = "Runs the GWT app")
    private boolean gwt;

    @CommandLine.Option(names = {"-f", "--openjfx-fatjar"}, description = "Runs the OpenJFX fat jar")
    private boolean fatjar;

    @CommandLine.Option(names = {"-k", "--openjfx-desktop"}, description = "Runs the OpenJFX desktop app")
    private boolean openJfxDesktop;

    @CommandLine.Option(names = {"-d", "--gluon-desktop"}, description = "Runs the Gluon native desktop app")
    private boolean gluonDesktop;

    @CommandLine.Option(names = {"-m", "--gluon-mobile"}, description = "Same as --gluon-android on Linux, --gluon-ios on macOS")
    private boolean mobile;

    @CommandLine.Option(names = {"-a", "--gluon-android"}, description = "Runs the Gluon native Android app")
    private boolean android;

    @CommandLine.Option(names = {"-i", "--gluon-ios"}, description = "Runs the Gluon native iOS app")
    private boolean ios;

    @CommandLine.Option(names= {"-l", "--locate"}, description = "Just prints the location of the expected executable file")
    boolean locate;

    @CommandLine.Option(names= {"-r", "--reveal"}, description = "Just reveals the executable file in the file browser")
    boolean reveal;

    /*@Option(names= {"-b", "--build"}, description = "Build before running")
    boolean build;

    @Option(names= {"-p", "--port"}, description = "Port of the web server.")
    int port;*/

    @Override
    public void run() {
        if (mobile) {
            if (OperatingSystem.isMacOs())
                ios = true;
            else
                android = true;
        }
        BuildRunCommon buildRunCommon = new BuildRunCommon(gwt, fatjar, openJfxDesktop, gluonDesktop, android, ios, locate, reveal, true, false);
        buildRunCommon.findAndConsumeExecutableModule(workspace.getWorkingDevProjectModule(), workspace.getTopRootModule(), buildRunCommon::executeModule);
    }

}
