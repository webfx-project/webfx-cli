package dev.webfx.tool.cli.subcommands;

import dev.webfx.tool.cli.WebFx;
import dev.webfx.tool.cli.core.Logger;
import dev.webfx.tool.cli.util.process.ProcessCall;
import picocli.CommandLine.Command;

import java.io.File;
import java.nio.file.Path;

/**
 * @author Bruno Salmon
 */
@Command(name = "bump", description = "Bump to a new version if available.",
        subcommands = {
                Bump.Cli.class
        })
public final class Bump extends CommonSubcommand {

    @Command(name = "cli", description = "Bump the CLI to a new version if available.")
    static class Cli extends CommonSubcommand implements Runnable {

        @Override
        public void run() {
            String jarLocation = getClass().getProtectionDomain().getCodeSource().getLocation().getPath();
            if (jarLocation.endsWith(".jar")) {
                jarLocation = jarLocation.replaceAll("%20", " ");
                Path jarPath = Path.of(jarLocation);
                File cliRepo = jarPath.getParent().getParent().toFile();
                new ProcessCall("git pull")
                        .setWorkingDirectory(cliRepo)
                        .setResultLineFilter(line -> line.contains("up-to-date"))
                        .setLogLineFilter(line -> line.contains("up-to-date") || line.toLowerCase().contains("error"))
                        .setLogsCall(false, false)
                        .executeAndWait()
                        .onLastResultLine(gitResultLine -> {
                            //Logger.log("Git result line: " + gitResultLine);
                            if (gitResultLine == null) {
                                Logger.log("A new version is available!");
                                Logger.log("Old version: " + WebFx.getVersion());
                                new ProcessCall("mvn package")
                                        .setWorkingDirectory(cliRepo)
                                        .setResultLineFilter(line -> line.contains("BUILD SUCCESS"))
                                        .setLogLineFilter(line -> line.startsWith("[ERROR]"))
                                        .setLogsCall(false, false)
                                        .executeAndWait()
                                        .onLastResultLine(mvnResultLine -> {
                                            //Logger.log("Maven result line: " + mvnResultLine);
                                            if (mvnResultLine != null) {
                                                Logger.log("New version: " +
                                                        new ProcessCall("java -jar " + jarPath.toAbsolutePath() + " --version")
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
}
