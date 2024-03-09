package dev.webfx.cli.commands;

import dev.webfx.cli.core.CliException;
import dev.webfx.cli.core.DevProjectModule;
import dev.webfx.cli.core.Logger;
import dev.webfx.cli.core.MavenUtil;
import dev.webfx.cli.util.os.OperatingSystem;
import dev.webfx.cli.util.process.ProcessCall;
import picocli.CommandLine;
import picocli.CommandLine.Command;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * @author Bruno Salmon
 */
@Command(name = "run", description = "Run a WebFX application.")
public final class Run extends CommonSubcommand implements Runnable {

    @CommandLine.Option(names = {"-g", "--gwt"}, description = "Runs the GWT app")
    private boolean gwt;

    @CommandLine.Option(names = {"-j", "--j2cl"}, description = "Runs the J2CL app")
    private boolean j2cl;

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

    @CommandLine.Option(names= {"-l", "--locate"}, description = "Just prints the location of the expected executable file (no run)")
    boolean locate;

    @CommandLine.Option(names= {"-s", "--show"}, description = "Just shows the executable file in the file browser (no run)")
    boolean show;

    @CommandLine.Option(names= {"-b", "--build"}, description = "(Re)build the application before running it")
    boolean build;

    @CommandLine.Option(names= {"--AppImage"}, description = "Takes the AppImage as executable (Linux)")
    boolean appImage;

    @CommandLine.Option(names= {"--deb"}, description = "Takes the deb package as executable (Linux)")
    boolean deb;

    @CommandLine.Option(names= {"--rpm"}, description = "Takes the rpm package as executable (Linux)")
    boolean rpm;

    @CommandLine.Option(names= {"--open"}, description = "Runs the executable via 'open' (macOS)")
    boolean open;

    /*@Option(names= {"-p", "--port"}, description = "Port of the web server.")
    int port;*/

    @Override
    public void run() {
        if (mobile) {
            if (OperatingSystem.isMacOs())
                ios = true;
            else
                android = true;
        }
        execute(new BuildRunCommon(build, true, gwt, j2cl, fatjar, openJfxDesktop, gluonDesktop, android, ios, locate, show, appImage, deb, rpm, open), getWorkspace());
    }

    static void execute(BuildRunCommon brc, CommandWorkspace workspace) {
        if (brc.build)
            Build.execute(brc, workspace); // Build will call executeNoBuild() at the end of the build
        else
            executeNoBuild(brc, workspace);
    }

    static void executeNoBuild(BuildRunCommon brc, CommandWorkspace workspace) {
        DevProjectModule executableModule = brc.findExecutableModule(workspace);
        if (executableModule != null) // null with --locate or --show
            brc.getExecutableFilePath(executableModule).forEach(path -> executeFile(path, brc.open));
    }

    private static void executeFile(Path executableFilePath, boolean usesOpen) {
        try {
            String fileName = executableFilePath.getFileName().toString();
            String pathName = executableFilePath.toString();
            if (!Files.exists(executableFilePath))
                Logger.log("Can't execute nonexistent file " + ProcessCall.toShellLogCommandToken(executableFilePath));
            else if (fileName.endsWith(".jar"))
                ProcessCall.executeCommandTokens("java", "-jar", pathName);
            else if (fileName.endsWith(".html"))
                if (OperatingSystem.isWindows())
                    ProcessCall.executePowershellCommand(". " + ProcessCall.toShellLogCommandToken(executableFilePath));
                else
                    ProcessCall.executeCommandTokens("open", pathName);
            else if (fileName.endsWith(".apk") || fileName.endsWith(".ipa")) {
                boolean android = fileName.endsWith(".apk");
                Path gluonModulePath = executableFilePath.getParent();
                while (gluonModulePath != null && !Files.exists(gluonModulePath.resolve("pom.xml")))
                    gluonModulePath = gluonModulePath.getParent();
                if (gluonModulePath != null)
                    MavenUtil.invokeMavenGoal("-P gluon-" + (android ? "android" : "ios") + " gluonfx:install gluonfx:nativerun"
                            , new ProcessCall().setWorkingDirectory(gluonModulePath));
            } else if (fileName.endsWith(".deb")) {
                int exitCode = ProcessCall.executeCommandTokens("sudo", "apt", "install", pathName);
                if (exitCode == 0 && fileName.contains("_")) {
                    String commandName = fileName.substring(0, fileName.lastIndexOf('_'));
                    Logger.log("\nIn addition to the desktop icon, you can now type '" + commandName + "' in the terminal to launch the application.\nUse 'sudo apt remove " + commandName.toLowerCase() + "' to uninstall the application.");
                }
            } else { // Everything else should be an executable file that we can call directly
                int exitCode;
                if (usesOpen && !OperatingSystem.isWindows())
                    exitCode = ProcessCall.executeCommandTokens("open", pathName);
                else
                    exitCode = ProcessCall.executeCommandTokens(pathName);
                if (exitCode != 0 && fileName.endsWith(".AppImage"))
                    Logger.log("\nYou can install FUSE with 'sudo apt install fuse'");
            }
        } catch (Exception e) {
            throw new CliException(e.getMessage());
        }
    }

}
