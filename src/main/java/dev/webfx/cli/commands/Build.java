package dev.webfx.cli.commands;

import dev.webfx.cli.core.*;
import dev.webfx.cli.exceptions.CliException;
import dev.webfx.cli.util.os.OperatingSystem;
import dev.webfx.cli.util.process.ProcessCall;
import picocli.CommandLine;
import picocli.CommandLine.Command;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * @author Bruno Salmon
 */
@Command(name = "build", description = "Build a WebFX application.")
public final class Build extends CommonSubcommand implements Runnable {

    @CommandLine.Option(names= {"-c", "--clean"}, description = "Clean the target folder before the build")
    boolean clean;

    //@CommandLine.Option(names = {"--j2cl"}, description = "Includes the J2CL compilation")
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

    @CommandLine.Option(names = {"-n", "--native"}, description = "Includes the GraalVM native desktop build")
    private boolean nativeDesktop;

    @CommandLine.Option(names = {"-e", "--per-package"}, description = "Builds each Java package individually: one jar per package (target/packages)."
        + " With --native, the package of the main class goes into the executable and the other packages of the project into a shared library (GraalVM Native Image layers, experimental)")
    private boolean perPackage;

    @CommandLine.Option(names = {"--layer-per-package"}, description = "With --native: one shared library per Java package (chained Native Image layers) instead of one for all packages."
        + " Implies --per-package. Note: GraalVM supports so far only one layer below the executable")
    private boolean layerPerPackage;

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

    @CommandLine.Option(names= {"-p", "--port"}, description = "Port of the web server.")
    int port = 8080;

    @CommandLine.Option(names= {"--file"}, description = "Runs the webapp via file:// rather than http://")
    boolean file;

    @Override
    public void run() {
        execute(new BuildRunCommon(clean, true, run, j2cl, teavm, javascript, wasm, fatjar, openJfxDesktop, nativeDesktop, perPackage, layerPerPackage, locate, show, appImage, deb, rpm, port, file), getWorkspace());
    }

    static void execute(BuildRunCommon brc, CommandWorkspace workspace) {
        DevProjectModule nativeModule = brc.findNativeModule(workspace);

        // Don't build if --locate or --show options are used
        if (brc.locate || brc.show)
            return;

/*
        if (!fatjar && !openJfxDesktop && !nativeDesktop)
            throw new CommandLine.ParameterException(new CommandLine(this), "Missing required build option");
*/
        String command = "mvn " +
                (brc.clean ? "clean " : "") +
                (nativeModule != null || brc.j2cl ? "install " : "package ") + // for native & J2CL: 1) install 2) later build (see below)
                (brc.fatjar ? "-P openjfx-fatjar " : "") +
                (brc.openJfxDesktop ? "-P openjfx-desktop " : "") +
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
        // One jar per Java package (before the native build, which takes long)
        if (brc.perPackage && exitCode == 0)
            writePackageJars(workspace);
        // Subsequent build for the native desktop image (after mvn install on all modules)
        if (nativeModule != null && exitCode == 0)
            exitCode = brc.perPackage ? NativeLayersBuild.execute(nativeModule, workspace, brc.layerPerPackage) : invokeNativeBuild(nativeModule);
        if (brc.run && exitCode == 0)
            Run.executeNoBuild(brc, workspace);
    }

    /**
     * Splits the compiled classes of each module (target/classes) into one jar per package (target/packages). Skipped:
     * aggregate modules, and modules for the web only (TeaVM, J2CL), which compile the whole program anyway.
     */
    private static void writePackageJars(CommandWorkspace workspace) {
        workspace.getTopRootModule().getThisAndChildrenModulesInDepth()
            .filter(DevProjectModule.class::isInstance)
            .map(DevProjectModule.class::cast)
            .filter(m -> !m.isAggregate())
            .filter(m -> m.getTarget().isAnyPlatformSupported(Platform.JRE))
            .forEach(m -> {
                Path classesDirectory = m.getHomeDirectory().resolve("target/classes");
                if (!Files.isDirectory(classesDirectory))
                    return;
                Path packagesDirectory = m.getHomeDirectory().resolve("target/packages");
                try {
                    List<Path> jars = JavaPackages.writePackageJars(classesDirectory, packagesDirectory);
                    Logger.log(m.getName() + ": " + jars.size() + " jars in " + ProcessCall.toShellLogCommandToken(packagesDirectory));
                } catch (IOException e) {
                    throw new CliException("Couldn't write the package jars of " + m.getName() + ": " + e.getMessage());
                }
            });
    }

    private static int invokeNativeBuild(DevProjectModule nativeModule) {
        return invokeNativeMavenGoal(nativeModule, "-P " + MavenUtil.NATIVE_DESKTOP_PROFILE + " package", false);
    }

    /**
     * Invokes a Maven goal of the native module with the GraalVM chosen by {@link GraalVm} and, on Windows, the MSVC
     * environment (native-image requires it).
     *
     * @param throughShell true to run mvn through the shell (PowerShell or bash) in any case, ex: for single-quoted arguments
     */
    static int invokeNativeMavenGoal(DevProjectModule nativeModule, String goal, boolean throughShell) {
        String devShellCommand = OperatingSystem.isWindows() ? getVisualStudioDevShellCommand() : null;
        String shellPreCommand = devShellCommand != null ? devShellCommand : throughShell ? "" : null;
        return MavenUtil.invokeMavenGoal(goal, new ProcessCall().setWorkingDirectory(nativeModule.getHomeDirectory()), shellPreCommand);
    }

    /**
     * native-image requires the MSVC environment on Windows => we load it through the Visual Studio DevShell when
     * found (if not, native-image will try to locate Visual Studio by itself).
     *
     * @return the PowerShell command loading the Visual Studio DevShell, or null if not found
     */
    private static String getVisualStudioDevShellCommand() {
        String devShellLine = new ProcessCall()
                .setCommand("reg query HKEY_CURRENT_USER\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\UFH\\SHC /f Microsoft.VisualStudio.DevShell.dll")
                .setResultLineFilter(line -> line.contains("Microsoft.VisualStudio.DevShell.dll"))
                .executeAndWait()
                .getLastResultLine();
        if (devShellLine == null)
            return null;
        return devShellLine.substring(devShellLine.indexOf("&{Import-Module") + 2, devShellLine.lastIndexOf('}')).replaceAll("\"\"\"", "'") + " -DevCmdArguments '-arch=x64'";
    }
}
