package dev.webfx.tool.cli.commands;

import dev.webfx.tool.cli.core.DevProjectModule;
import dev.webfx.tool.cli.core.MavenCaller;
import dev.webfx.tool.cli.util.os.OperatingSystem;
import dev.webfx.tool.cli.util.process.ProcessCall;
import picocli.CommandLine;
import picocli.CommandLine.Command;

import java.nio.file.Path;

/**
 * @author Bruno Salmon
 */
@Command(name = "build", description = "Invoke Maven build.")
public final class Build extends CommonSubcommand implements Runnable {

    @CommandLine.Option(names = {"--gwt"}, description = "Includes the GWT compilation")
    private boolean gwt;

    @CommandLine.Option(names = {"--openjfx-fatjar"}, description = "Creates a fat jar for the OpenJFX version")
    private boolean fatjar;

    @CommandLine.Option(names = {"--openjfx-desktop"}, description = "Includes the OpenJFX desktop build")
    private boolean openJfxDesktop;

    @CommandLine.Option(names = {"--gluon-desktop"}, description = "Includes the Gluon native desktop build")
    private boolean gluonDesktop;

    @CommandLine.Option(names = {"--gluon-mobile"}, description = "Same as --gluon-android on Linux, --gluon-ios on macOS")
    private boolean mobile;

    @CommandLine.Option(names = {"--gluon-android"}, description = "Includes the Gluon native Android build")
    private boolean android;

    @CommandLine.Option(names = {"--gluon-ios"}, description = "Includes the Gluon native iOS build")
    private boolean ios;

    @Override
    public void run() {
        if (mobile) {
            if (OperatingSystem.isMacOs())
                ios = true;
            else
                android = true;
        }
        new BuildRunCommon(gwt, fatjar, openJfxDesktop, gluonDesktop, android, ios, false, false)
                .findAndConsumeExecutableModule(getWorkingDevProjectModule(), getTopRootModule(),
                        this::build);
    }

    private void build(DevProjectModule executableModule) {
        boolean gluon = gluonDesktop || android || ios;
        if (!fatjar && !gwt && !openJfxDesktop && !gluon)
            throw new CommandLine.ParameterException(new CommandLine(this), "Missing required build option");
        String command = "mvn " +
                (gluon ? "install " : "package ") +
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
                .setWorkingDirectory(getTopRootModule().getHomeDirectory())
                .executeAndWait();
        if (gluonDesktop) {
            if (OperatingSystem.isWindows()) {
                new ProcessCall()
                        .setCommand("reg query HKEY_CURRENT_USER\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\UFH\\SHC /f Microsoft.VisualStudio.DevShell.dll")
                        .setResultLineFilter(line -> line.contains("Microsoft.VisualStudio.DevShell.dll"))
                        .executeAndWait()
                        .onLastResultLine(resultLine -> {
                            Path graalVmHome = Bump.getGraalVmHome();
                            new ProcessCall()
                                    .setPowershellCommand(
                                            (resultLine == null ? "" : resultLine.substring(resultLine.indexOf("&{Import-Module") + 2, resultLine.lastIndexOf('}')).replaceAll("\"\"\"", "'") + " -DevCmdArguments '-arch=x64'; ") +
                                            (graalVmHome == null ? "" : "$env:GRAALVM_HOME = '" + graalVmHome + "'; ") +
                                            "mvn -P gluon-desktop gluonfx:build gluonfx:package")
                                    .setWorkingDirectory(executableModule.getHomeDirectory())
                                    .executeAndWait();
                        });
            } else
                invokeGluonGoal("gluon-desktop", executableModule);
        }
        if (android)
            invokeGluonGoal("gluon-android", executableModule);
        if (ios)
            invokeGluonGoal("gluon-ios", executableModule);
    }

    private void invokeGluonGoal(String gluonProfile, DevProjectModule workingModule) {
        MavenCaller.invokeMavenGoal("-P " + gluonProfile + " gluonfx:build gluonfx:package"
                , new ProcessCall()
                        .setWorkingDirectory(workingModule.getHomeDirectory())
        );
    }
}
