package dev.webfx.tool.cli.commands;

import dev.webfx.lib.reusablestream.ReusableStream;
import dev.webfx.tool.cli.WebFx;
import dev.webfx.tool.cli.core.Logger;
import dev.webfx.tool.cli.util.os.OperatingSystem;
import dev.webfx.tool.cli.util.process.ProcessCall;
import dev.webfx.tool.cli.util.splitfiles.SplitFiles;
import picocli.CommandLine.Command;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.*;
import java.util.Comparator;
import java.util.Spliterators;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * @author Bruno Salmon
 */
@Command(name = "bump", description = "Bump to a new version if available.",
        subcommands = {
                Bump.Cli.class,
                Bump.GraalVm.class
        })
public final class Bump extends CommonSubcommand {

    @Command(name = "cli", description = "Bump the CLI to a new version if available.")
    static class Cli extends CommonSubcommand implements Runnable {

        @Override
        public void run() {
            Path cliJarPath = getCliJarPath();
            if (cliJarPath != null) {
                Path cliRepositoryPath = getCliRepositoryPath(cliJarPath);
                new ProcessCall("git pull")
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
                                new ProcessCall("mvn package")
                                        .setWorkingDirectory(cliRepositoryPath)
                                        .setResultLineFilter(line -> line.contains("BUILD SUCCESS"))
                                        .setLogLineFilter(line -> line.startsWith("[ERROR]"))
                                        .setLogsCall(false, false)
                                        .executeAndWait()
                                        .onLastResultLine(mvnResultLine -> {
                                            //Logger.log("Maven result line: " + mvnResultLine);
                                            if (mvnResultLine != null) {
                                                Logger.log("New version: " +
                                                        new ProcessCall("java -jar " + cliJarPath.toAbsolutePath() + " --version")
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


    @Command(name = "graalvm", description = "Bump GraalVM to a new version if available.")
    static class GraalVm extends CommonSubcommand implements Runnable {

        private final static String GITHUB_GRAAL_RELEASE_PAGE_URL = "https://github.com/gluonhq/graal/releases/latest";

        @Override
        public void run() {
            String osToken = null;
            switch (OperatingSystem.getOsFamily()) {
                case WINDOWS: osToken = "windows"; break;
                case MAC_OS: osToken = "darwin"; break;
                case LINUX: osToken = "linux"; break;
            }
            Logger.log("Checking for update on " + GITHUB_GRAAL_RELEASE_PAGE_URL);
            String pageContent = downloadPage(GITHUB_GRAAL_RELEASE_PAGE_URL);
            Pattern pattern = Pattern.compile("href=\"(.*/gluonhq/graal/releases/download/.*-java17-" + osToken + "-.*.zip)\"", 1);
            Matcher matcher = pattern.matcher(pageContent);
            if (!matcher.find()) {
                Logger.log("No GraalVM found for your system!");
                return;
            }

            String vmUrl = matcher.group(1);
            if (vmUrl.startsWith("/"))
                vmUrl = "https://github.com" + vmUrl;

            Path cliRepositoryPath = getCliRepositoryPath();
            Path hiddenVmFolder = cliRepositoryPath.resolve(".graalvm");
            String vmDownloadFileName = vmUrl.substring(vmUrl.lastIndexOf('/') + 1);
            Path vmDownloadFilePath = hiddenVmFolder.resolve(vmDownloadFileName);
            String vmName = vmDownloadFileName.substring(0, vmDownloadFileName.lastIndexOf('.')); // removing .zip extension
            Path vmTagFilePath = hiddenVmFolder.resolve(vmName + ".tag");

            // Downloading the archive file
            if (Files.exists(vmTagFilePath)) {
                Logger.log("Already up-to-date (" + vmName + ")");
                return;
            }

            // Deleting all files to clear up space
            if (Files.exists(hiddenVmFolder)) {
                Logger.log("New version available!: " + vmName);
                ReusableStream.create(() -> SplitFiles.uncheckedWalk(hiddenVmFolder))
                        .sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
            } else
                Logger.log("Latest version: " + vmName);

            // Downloading the GraalVM file
            Logger.log("Downloading " + vmUrl);
            downloadFile(vmUrl, vmDownloadFilePath);

            // Uncompressing the zip archive
            Logger.log("Uncompressing " + vmDownloadFilePath.getFileName());
            unzip(vmDownloadFilePath, hiddenVmFolder);

            // Deleting the archive files to free space
            vmDownloadFilePath.toFile().delete();

            // Recreating the tag file (used to know which graalvm version is installed)
            try {
                vmTagFilePath.toFile().createNewFile();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static String downloadPage(String fileUrl) {
        try (BufferedInputStream in = new BufferedInputStream(new URL(fileUrl).openStream())) {
            return new String(in.readAllBytes());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void downloadFile(String fileUrl, Path filePath) {
        filePath.getParent().toFile().mkdirs();
        try (BufferedInputStream in = new BufferedInputStream(new URL(fileUrl).openStream())) {
            Files.copy(in, filePath);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void unzip(Path archivePath, Path destinationFolder) {
        try (FileSystem zipFs = FileSystems.newFileSystem(archivePath);
             Stream<Path> walk = Files.walk(zipFs.getPath("/"))) {
            walk.filter(path -> !path.toString().equals("/"))
                    .forEach(path -> unzipFile(path, destinationFolder.resolve(path.toString().substring(1))));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void unzipFile(Path srcPath, Path dstPath) {
        try {
            Files.copy(srcPath, dstPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
            // Some files are executable files, so setting the executable flag
            dstPath.toFile().setExecutable(true); // Doing it for all files (not beautiful but simple)
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static Path getGraalVmHome() {
        Path cliRepositoryPath = getCliRepositoryPath();
        Path hiddenVmFolder = cliRepositoryPath.resolve(".graalvm");
        Path binPath = ReusableStream.create(() -> Files.exists(hiddenVmFolder) ? SplitFiles.uncheckedWalk(hiddenVmFolder) : Spliterators.emptySpliterator())
                .filter(path -> path.toFile().isDirectory() && "bin".equals(path.getFileName().toString()))
                .findFirst()
                .orElse(null);
        return binPath == null ? null : binPath.getParent();
    }

    private static Path getCliCodePath() {
        String jarLocation = Bump.class.getProtectionDomain().getCodeSource().getLocation().getPath();
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

    private static Path getCliRepositoryPath() {
        return getCliRepositoryPath(getCliCodePath());
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
