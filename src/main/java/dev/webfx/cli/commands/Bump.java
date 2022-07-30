package dev.webfx.cli.commands;

import dev.webfx.cli.WebFX;
import dev.webfx.cli.core.Logger;
import dev.webfx.cli.util.process.ProcessCall;
import picocli.CommandLine;
import picocli.CommandLine.Command;

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

        @Override
        public void run() {
            if (cliRepositoryPath != null)
                if (branch == null)
                    gitPullAndBuild(true);
                else
                    gitCheckoutBranchAndBuild();
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
                        if (!skipBuildIfUpToDate || gitUpToDateLine == null)
                            buildAndExit();
                        else
                            Logger.log("You already have the latest version");
                    });
        }

        private void buildAndExit() {
            if (switchedBranch) {
                Logger.log("Building " + branch + " branch");
            } else {
                Logger.log("A new version is available!");
                Logger.log("Old version: " + WebFX.getVersion());
            }
            newCliProcessCall("mvn", "package")
                    .setResultLineFilter(line -> line.contains("BUILD SUCCESS"))
                    .setLogLineFilter(line -> line.startsWith("[ERROR]"))
                    .executeAndWait()
                    .onLastResultLine(mvnResultLine -> {
                        //Logger.log("Maven result line: " + mvnResultLine);
                        if (mvnResultLine != null) {
                            Logger.log((switchedBranch ? "CLI version of " + branch + " branch: " : "New version: ") +
                                    new ProcessCall("java", "-jar", cliJarPath.toAbsolutePath().toString(), "--version")
                                            .setLogLineFilter(line -> false)
                                            .setLogsCall(false, false)
                                            .executeAndWait()
                                            .getLastResultLine()
                            );
                            // We exit now because otherwise it's very likely we will get a runtime
                            // exception due to the fat jar update.
                            System.exit(0);
                        }
                    });
        }

        private ProcessCall newCliProcessCall(String... commandTokens) {
            return new ProcessCall(commandTokens)
                    .setWorkingDirectory(cliRepositoryPath)
                    .setLogsCall(false, false);
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
