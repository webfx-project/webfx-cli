package dev.webfx.cli.commands;

import dev.webfx.cli.WebFx;
import dev.webfx.cli.core.Logger;
import dev.webfx.cli.util.process.ProcessCall;
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

        @Override
        public void run() {
            Path cliJarPath = getCliJarPath();
            if (cliJarPath != null) {
                Path cliRepositoryPath = getCliRepositoryPath(cliJarPath);
                new ProcessCall("git", "pull")
                        .setWorkingDirectory(cliRepositoryPath)
                        .setResultLineFilter(line -> removeSpaceAndDash(line).contains("uptodate"))
                        .setLogLineFilter(line -> removeSpaceAndDash(line).contains("uptodate") || line.toLowerCase().contains("error"))
                        .setLogsCall(false, false)
                        .executeAndWait()
                        .onLastResultLine(gitResultLine -> {
                            //Logger.log("Git result line: " + gitResultLine);
                            if (gitResultLine == null) {
                                Logger.log("A new version is available!");
                                Logger.log("Old version: " + WebFx.getVersion());
                                new ProcessCall("mvn", "package")
                                        .setWorkingDirectory(cliRepositoryPath)
                                        .setResultLineFilter(line -> line.contains("BUILD SUCCESS"))
                                        .setLogLineFilter(line -> line.startsWith("[ERROR]"))
                                        .setLogsCall(false, false)
                                        .executeAndWait()
                                        .onLastResultLine(mvnResultLine -> {
                                            //Logger.log("Maven result line: " + mvnResultLine);
                                            if (mvnResultLine != null) {
                                                Logger.log("New version: " +
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
                        });
            }
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

    private static Path getCliRepositoryPath(Path insidePath) {
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

    private static String removeSpaceAndDash(String line) {
        return line.replaceAll(" ", "").replaceAll("-", "");
    }

}
