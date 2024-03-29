package dev.webfx.cli.commands;

import dev.webfx.cli.WebFxCLI;
import dev.webfx.cli.core.Logger;
import dev.webfx.cli.util.process.ProcessCall;
import dev.webfx.cli.util.splitfiles.SplitFiles;
import dev.webfx.lib.reusablestream.ReusableStream;
import picocli.CommandLine;
import picocli.CommandLine.Command;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * @author Bruno Salmon
 */
@Command(name = "bump", description = "Bump to a new version if available.",
        subcommands = {
                Bump.Cli.class,
        })
public final class Bump extends CommonSubcommand {

    @Command(name = "cli", description = "Upgrade the CLI to a new version if available.")
    static class Cli extends CommonSubcommand implements Runnable {

        @CommandLine.Option(names = {"-b", "--branch"}, description = "Switch to another branch")
        private String branch;

        private boolean switchedBranch;
        private final Path cliJarPath = getCliJarPath();
        private final Path cliRepositoryPath = walkUpToCliRepositoryPath(cliJarPath);
        private final Path targetPath = cliRepositoryPath == null ? null : cliRepositoryPath.resolve("target");
        private final Path buildInProcessLockPath = targetPath == null ? null : targetPath.resolve("build.lock");

        @Override
        public void run() {
            if (cliRepositoryPath != null) {
                if (branch == null)
                    gitPullAndBuild(true);
                else
                    gitCheckoutBranchAndBuild();
            }
        }

        private void gitCheckoutBranchAndBuild() {
            newCliProcessCall("git", "checkout", "-B", branch, "origin/" + branch)
                    .setResultLineFilter(line -> removeSpaceAndDashToLowerCase(line).contains("switchedto"))
                    .setLogLineFilter(line -> line.toLowerCase().contains("error"))
                    .executeAndWait()
                    .onLastResultLine(gitSwitchedToLine -> gitPullAndBuild(!(switchedBranch = gitSwitchedToLine != null)));
        }

        private void gitPullAndBuild(boolean skipBuildIfUpToDate) {
            newCliProcessCall("git", "pull", "-r")
                    .setResultLineFilter(line -> removeSpaceAndDashToLowerCase(line).contains("uptodate"))
                    .setLogLineFilter(line -> line.toLowerCase().contains("error"))
                    .executeAndWait()
                    .onLastResultLine(gitUpToDateLine -> {
                        if (!skipBuildIfUpToDate || gitUpToDateLine == null || buildInProcessLockPath != null && Files.exists(buildInProcessLockPath))
                            buildAndExit();
                        else
                            Logger.log("You have the latest version of the CLI");
                    });
        }

        private void buildAndExit() {
            if (switchedBranch)
                Logger.log("Building " + branch + " branch");
            else {
                Logger.log("A new version is available!");
                Logger.log("Old version: " + WebFxCLI.getVersion());
            }
            if (buildInProcessLockPath != null && Files.exists(buildInProcessLockPath))
                Logger.warning("Last build failed. Trying to build it again...");
            // Cleaning the target folder except the cli jar (using `mvn clean` is failing on Windows because the cli jar
            // is locked as it is the CLI executable)
            ReusableStream.create(() -> SplitFiles.uncheckedWalk(targetPath))
                    .filter(p -> !p.equals(cliJarPath))
                    .forEach(p -> {
                        try {
                            Files.delete(p);
                        } catch (IOException ignored) { }
                    });
            // We create a build lock file that we will delete after a successful build. So if next time we detect its
            // presence, this is a sign that the last build has not completed.
            if (buildInProcessLockPath != null)
                try {
                    Files.createFile(buildInProcessLockPath);
                } catch (IOException ignored) { }
            // We invoke the build through `mvn -U package`
            newCliProcessCall("mvn", "-U", "package") // -U is to ensure we get the latest SNAPSHOT versions (in particular webfx-platform) to prevent build errors
                    .setResultLineFilter(line -> line.contains("BUILD SUCCESS"))
                    .setLogLineFilter(line -> line.startsWith("[ERROR]"))
                    .executeAndWait()
                    .onLastResultLine(mvnResultLine -> {
                        if (mvnResultLine != null) {
                            // If we arrive here, this means the build was successful, so we can delete the build lock file.
                            if (buildInProcessLockPath != null)
                                try {
                                    Files.delete(buildInProcessLockPath);
                                } catch (IOException ignored) { }
                            // We log the new version of the cli
                            Logger.log((switchedBranch ? "CLI version of " + branch + " branch: " : "New version: ") +
                                    new ProcessCall("java", "-jar", cliJarPath.toAbsolutePath().toString(), "--version")
                                            .setLogLineFilter(line -> false)
                                            .setLogsCalling(false)
                                            .setLogsCallDuration(false)
                                            .executeAndWait()
                                            .getLastResultLine()
                            );
                            // We exit now because otherwise it's very likely we will get a runtime
                            // exception due to the fat jar update.
                            System.exit(0);
                        } else { // Build failed
                            Logger.warning("The build failed! You can try again with `webfx bump cli` or execute the following commands in your terminal:");
                            Logger.log("cd " + cliRepositoryPath);
                            Logger.log("git pull");
                            Logger.log("mvn -U package");
                        }
                    });
        }

        private ProcessCall newCliProcessCall(String... commandTokens) {
            return new ProcessCall(commandTokens)
                    .setWorkingDirectory(cliRepositoryPath)
                    .setLogsCalling(false)
                    .setLogsCallDuration(false);
        }

    }

    private static Path getCliCodePath() {
        String jarLocation = Install.class.getProtectionDomain().getCodeSource().getLocation().getPath();
        jarLocation = jarLocation.replaceAll("%20", " ");
        // Removing the starting / on Windows machines (ex: "/C:/Users/..." will become "C:/Users/...")
        if (jarLocation.startsWith("/") && Character.isLetter(jarLocation.charAt(1)) && jarLocation.charAt(2) == ':')
            jarLocation = jarLocation.substring(1);
        return Path.of(jarLocation);
    }

    private static Path getCliJarPath() {
        Path cliCodePath = getCliCodePath();
        return cliCodePath.toString().endsWith(".jar") ? cliCodePath : null;
    }

    private static Path walkUpToCliRepositoryPath(Path insidePath) {
        while (insidePath != null) {
            switch (insidePath.getFileName().toString()) {
                case "webfx-cli":
                    return insidePath;
                case "target":
                    return insidePath.getParent();
            }
            insidePath = insidePath.getParent();
        }
        return null;
    }

    private static String removeSpaceAndDashToLowerCase(String line) {
        return line.replaceAll(" ", "").replaceAll("-", "").toLowerCase();
    }

}
