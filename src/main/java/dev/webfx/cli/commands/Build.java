package dev.webfx.cli.commands;

import dev.webfx.cli.core.DevProjectModule;
import dev.webfx.cli.core.MavenCaller;
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

    @CommandLine.Option(names = {"-g", "--gwt"}, description = "Includes the GWT compilation")
    private boolean gwt;

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

    @CommandLine.Option(names= {"-l", "--locate"}, description = "Just prints the location of the expected executable file")
    boolean locate;

    @CommandLine.Option(names= {"-r", "--reveal"}, description = "Just reveals the executable file in the file browser")
    boolean reveal;

    @Override
    public void run() {
        if (mobile) {
            if (OperatingSystem.isMacOs())
                ios = true;
            else
                android = true;
        }
        new BuildRunCommon(gwt, fatjar, openJfxDesktop, gluonDesktop, android, ios, locate, reveal, false, true)
                .findAndConsumeExecutableModule(workspace.getWorkingDevProjectModule(), workspace.getTopRootModule(),
                        this::build);
    }

    private void build(DevProjectModule gluonModule) {
/*
        if (!fatjar && !gwt && !openJfxDesktop && !gluon)
            throw new CommandLine.ParameterException(new CommandLine(this), "Missing required build option");
*/
        String command = "mvn " +
                (gluonModule != null ? "install " : "package ") +
                (fatjar ? "-P openjfx-fatjar " : "") +
                (openJfxDesktop ? "-P openjfx-desktop " : "") +
                (gwt ? "-P gwt-compile " : "");
        ProcessCall processCall = new ProcessCall();
        if (openJfxDesktop && OperatingSystem.isWindows()) { // Ensuring WiX and Inno is in the environment path (usually not done by the installer)
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
        processCall
                .setWorkingDirectory(workspace.getTopRootModule().getHomeDirectory())
                .executeAndWait();
        if (gluonModule != null) {
            if (gluonDesktop) {
                if (OperatingSystem.isWindows()) {
                    new ProcessCall()
                            .setCommand("reg query HKEY_CURRENT_USER\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\UFH\\SHC /f Microsoft.VisualStudio.DevShell.dll")
                            .setResultLineFilter(line -> line.contains("Microsoft.VisualStudio.DevShell.dll"))
                            .executeAndWait()
                            .onLastResultLine(resultLine -> {
                                Path graalVmHome = Install.getGraalVmHome();
                                new ProcessCall()
                                        .setPowershellCommand(
                                                (resultLine == null ? "" : resultLine.substring(resultLine.indexOf("&{Import-Module") + 2, resultLine.lastIndexOf('}')).replaceAll("\"\"\"", "'") + " -DevCmdArguments '-arch=x64'; ") +
                                                        (graalVmHome == null ? "" : "$env:GRAALVM_HOME = '" + graalVmHome + "'; ") +
                                                        "mvn -P gluon-desktop gluonfx:build gluonfx:package")
                                        .setWorkingDirectory(gluonModule.getHomeDirectory())
                                        .executeAndWait();
                            });
                } else
                    invokeGluonGoal("gluon-desktop", gluonModule);
            }
            if (android)
                invokeGluonGoal("gluon-android", gluonModule);
            if (ios)
                invokeGluonGoal("gluon-ios", gluonModule);
        }
    }

    private void invokeGluonGoal(String gluonProfile, DevProjectModule gluonModule) {
        MavenCaller.invokeMavenGoal("-P " + gluonProfile + " gluonfx:build gluonfx:package"
                , new ProcessCall()
                        .setWorkingDirectory(gluonModule.getHomeDirectory())
        );
    }
}