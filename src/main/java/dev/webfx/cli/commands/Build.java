package dev.webfx.cli.commands;

import dev.webfx.cli.core.DevProjectModule;
import dev.webfx.cli.core.MavenUtil;
import dev.webfx.cli.core.WebFXHiddenFolder;
import dev.webfx.cli.util.os.OperatingSystem;
import dev.webfx.cli.util.process.ProcessCall;
import picocli.CommandLine;
import picocli.CommandLine.Command;

import java.nio.file.Path;

/**
 * @author Bruno Salmon
 */
@Command(name = "build", description = "Build a WebFX application.")
public final class Build extends CommonSubcommand implements Runnable {

    @CommandLine.Option(names= {"-c", "--clean"}, description = "Clean the target folder before the build")
    boolean clean;

    @CommandLine.Option(names = {"-g", "--gwt"}, description = "Includes the GWT compilation")
    private boolean gwt;

    @CommandLine.Option(names = {"--j2cl"}, description = "Includes the J2CL compilation")
    private boolean j2cl;

    @CommandLine.Option(names = {"-t", "--teavm"}, description = "Includes the TeaVM compilation")
    private boolean teavm;

    @CommandLine.Option(names = {"-j", "--javascript"}, description = "Includes JavaScript module in the TeaVM compilation")
    private boolean javascript;

    @CommandLine.Option(names = {"-w", "--wasm"}, description = "Includes the Wasm module in the TeaVM compilation")
    private boolean wasm;

    @CommandLine.Option(names = {"-f", "--openjfx-fatjar"}, description = "Creates a fat jar for the OpenJFX version")
    private boolean fatjar;

    @CommandLine.Option(names = {"-k", "--openjfx-desktop"}, description = "Includes the OpenJFX desktop build")
    private boolean openJfxDesktop;

    @CommandLine.Option(names = {"-d", "--gluon-desktop"}, description = "Includes the Gluon native desktop build")
    private boolean gluonDesktop;

    @CommandLine.Option(names = {"-m", "--gluon-mobile"}, description = "Same as --gluon-android on Linux, --gluon-ios on macOS")
    private boolean mobile;

    @CommandLine.Option(names = {"-a", "--gluon-android"}, description = "Includes the Gluon native Android build")
    private boolean android;

    @CommandLine.Option(names = {"-i", "--gluon-ios"}, description = "Includes the Gluon native iOS build")
    private boolean ios;

    @CommandLine.Option(names= {"-l", "--locate"}, description = "Just prints the location of the expected executable file (no build)")
    boolean locate;

    @CommandLine.Option(names= {"-s", "--show"}, description = "Just shows the executable file in the file browser (no build)")
    boolean show;

    @CommandLine.Option(names= {"-r", "--run"}, description = "Runs the application after the build")
    boolean run;

    @CommandLine.Option(names= {"--AppImage"}, description = "Takes the AppImage as executable (Linux)")
    boolean appImage;

    @CommandLine.Option(names= {"--deb"}, description = "Takes the deb package as executable (Linux)")
    boolean deb;

    @CommandLine.Option(names= {"--rpm"}, description = "Takes the rpm package as executable (Linux)")
    boolean rpm;

    @CommandLine.Option(names= {"--open"}, description = "Runs the executable via 'open' (macOS)")
    boolean open;

    @Override
    public void run() {
        if (mobile) {
            if (OperatingSystem.isMacOs())
                ios = true;
            else
                android = true;
        }
        execute(new BuildRunCommon(clean, true, run, gwt, j2cl, teavm, javascript, wasm, fatjar, openJfxDesktop, gluonDesktop, android, ios, locate, show, appImage, deb, rpm, open), getWorkspace());
    }

    static void execute(BuildRunCommon brc, CommandWorkspace workspace) {
        DevProjectModule gluonModule = brc.findGluonModule(workspace);

        // Don't build if --locate or --show options are used
        if (brc.locate || brc.show)
            return;

        if (gluonModule != null && OperatingSystem.isMacOs()) {
            if (!Install.checkOrFixXcodePathForGluon(false))
                return;
        }

/*
        if (!fatjar && !gwt && !openJfxDesktop && !gluon)
            throw new CommandLine.ParameterException(new CommandLine(this), "Missing required build option");
*/
        String command = "mvn " +
                (brc.clean ? "clean " : "") +
                (gluonModule != null || brc.j2cl ? "install " : "package ") + // for Gluon & J2CL: 1) install 2) later build (see below)
                (brc.fatjar ? "-P openjfx-fatjar " : "") +
                (brc.openJfxDesktop ? "-P openjfx-desktop " : "") +
                (brc.gwt ? "-P gwt-compile " : "") +
                (brc.teavm && brc.javascript ? "-P teavm-js " : "") +
                (brc.teavm && brc.wasm ? "-P teavm-wasm " : "");
        ProcessCall processCall = new ProcessCall();
        if (brc.openJfxDesktop && OperatingSystem.isWindows()) { // Ensuring WiX and Inno is in the environment path (usually not done by the installer)
            String innoResultLine = new ProcessCall()
                    .setCommandTokens("reg", "query", "HKEY_LOCAL_MACHINE\\SOFTWARE\\Classes\\InnoSetupScriptFile", "/f", "\"Inno Setup*.exe\"", "/s")
                    .setResultLineFilter(line -> line.contains("Inno Setup"))
                    .executeAndWait()
                    .getLastResultLine();
            if (innoResultLine != null) {
                innoResultLine = innoResultLine.substring(innoResultLine.indexOf("REG_SZ") + 6, innoResultLine.lastIndexOf('\\')).trim();
                if (innoResultLine.startsWith("\""))
                    innoResultLine = innoResultLine.substring(1);
            }
            processCall.setPowershellCommand("$env:PATH += \";$env:WIX\\bin" + (innoResultLine != null ? ";" + innoResultLine : "") + "\"; " + command);
        } else
            processCall.setCommand(command);
        int exitCode = processCall
                .setWorkingDirectory(workspace.getTopRootModule().getHomeDirectory())
                .executeAndWait()
                .getExitCode();
        // Subsequent build (after mvn install on all modules) for J2CL executed under application-j2cl module.
        // This is to minimise the java language limitations coming from Javac 8 call by Vertispan plugin. Javac 8 is
        // called only on the reactor modules, so we limit the reactor to application-j2cl module.
        if (brc.j2cl && exitCode == 0) {
            exitCode = new ProcessCall()
                    .setCommand("mvn package -P j2cl")
                    .setWorkingDirectory(brc.findExecutableModule(workspace).getHomeDirectory())
                    .executeAndWait()
                    .getExitCode();
        }
        // Subsequent build for Gluon (after mvn install on all modules)
        if (gluonModule != null && exitCode == 0) {
            if (brc.gluonDesktop) {
                if (OperatingSystem.isWindows()) {
                    int[] gluonWindowsExitCode = {0};
                    exitCode = new ProcessCall()
                            .setCommand("reg query HKEY_CURRENT_USER\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\UFH\\SHC /f Microsoft.VisualStudio.DevShell.dll")
                            .setResultLineFilter(line -> line.contains("Microsoft.VisualStudio.DevShell.dll"))
                            .executeAndWait()
                            .onLastResultLine(resultLine -> {
                                Path graalVmHome = WebFXHiddenFolder.getGraalVmHome();
                                gluonWindowsExitCode[0] = new ProcessCall()
                                        .setPowershellCommand(
                                                (resultLine == null ? "" : resultLine.substring(resultLine.indexOf("&{Import-Module") + 2, resultLine.lastIndexOf('}')).replaceAll("\"\"\"", "'") + " -DevCmdArguments '-arch=x64'; ") +
                                                        (graalVmHome == null ? "" : "$env:GRAALVM_HOME = '" + graalVmHome + "'; ") +
                                                        "mvn -P gluon-desktop gluonfx:build gluonfx:package")
                                        .setWorkingDirectory(gluonModule.getHomeDirectory())
                                        .executeAndWait()
                                        .getExitCode();
                            }).getExitCode();
                    if (exitCode == 0)
                        exitCode = gluonWindowsExitCode[0];
                } else
                    exitCode = invokeGluonGoal("gluon-desktop", gluonModule);
            }
            if (brc.android)
                exitCode = invokeGluonGoal("gluon-android", gluonModule);
            if (brc.ios)
                exitCode = invokeGluonGoal("gluon-ios", gluonModule);
        }
        if (brc.run && exitCode == 0)
            Run.executeNoBuild(brc, workspace);
    }

    private static int invokeGluonGoal(String gluonProfile, DevProjectModule gluonModule) {
        return MavenUtil.invokeMavenGoal("-P " + gluonProfile + " gluonfx:build gluonfx:package"
                , new ProcessCall()
                        .setWorkingDirectory(gluonModule.getHomeDirectory())
        );
    }
}
