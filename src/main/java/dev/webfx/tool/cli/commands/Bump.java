package dev.webfx.tool.cli.commands;

import dev.webfx.lib.reusablestream.ReusableStream;
import dev.webfx.tool.cli.WebFx;
import dev.webfx.tool.cli.core.Logger;
import dev.webfx.tool.cli.util.os.OperatingSystem;
import dev.webfx.tool.cli.util.process.ProcessCall;
import dev.webfx.tool.cli.util.splitfiles.SplitFiles;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.utils.IOUtils;
import picocli.CommandLine.Command;

import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Spliterators;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

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
            Pattern pattern = Pattern.compile("href=\"(.*/gluonhq/graal/releases/download/.*-java17-" + osToken + "-.*.zip)\"", 1);
            Matcher matcher = pattern.matcher(downloadPage(GITHUB_GRAAL_RELEASE_PAGE_URL));
            if (!matcher.find())
                return;

            String vmUrl = matcher.group(1);
            if (vmUrl.startsWith("/"))
                vmUrl = "https://github.com" + vmUrl;

            Path cliRepositoryPath = getCliRepositoryPath();
            Path hiddenVmFolder = cliRepositoryPath.resolve(".graalvm");
            String vmArchiveFileName = vmUrl.substring(vmUrl.lastIndexOf('/') + 1);
            Path vmArchivePath = hiddenVmFolder.resolve(vmArchiveFileName);

            // Downloading the archive file
            if (Files.exists(vmArchivePath)) {
                Logger.log("Already up-to-date (" + vmArchiveFileName.substring(0, vmArchiveFileName.lastIndexOf('.')) + ")");
                return;
            }

            // Deleting all files to clear up space
            if (Files.exists(hiddenVmFolder))
                ReusableStream.create(() -> SplitFiles.uncheckedWalk(hiddenVmFolder))
                        .sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);

            // Downloading the GraalVM file
            downloadFile(vmUrl, vmArchivePath);

            // Uncompressing the archive
            Logger.log("Uncompressing " + vmArchivePath.getFileName());

            if (vmArchivePath.endsWith(".tar.gz"))
                uncompressTarGz(vmArchivePath, hiddenVmFolder);
            else
                uncompressZip(vmArchivePath, hiddenVmFolder);

            // Deleting the archive files to free space
            vmArchivePath.toFile().delete();

            // Recreating a file with the same name but empty (will be used for version checking on next pass)
            try {
                vmArchivePath.toFile().createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static String downloadPage(String fileUrl) {
        Logger.log("Downloading page " + fileUrl);
        try (BufferedInputStream in = new BufferedInputStream(new URL(fileUrl).openStream());
             ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            IOUtils.copy(in, os);
            return os.toString();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void downloadFile(String fileUrl, Path filePath) {
        filePath.getParent().toFile().mkdirs();
        Logger.log("Downloading file " + fileUrl);
        try (BufferedInputStream in = new BufferedInputStream(new URL(fileUrl).openStream());
             FileOutputStream os = new FileOutputStream(filePath.toFile())) {
            IOUtils.copy(in, os);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void uncompressZip(Path archivePath, Path destinationFolder) {
        try (FileInputStream fis = new FileInputStream(archivePath.toFile());
             ZipInputStream zipInputStream = new ZipInputStream(new BufferedInputStream(fis));
             ) {
            ZipEntry zipEntry;
            while ((zipEntry = zipInputStream.getNextEntry()) != null) {
                //Logger.log(zipEntry.getName());
                if (!zipEntry.isDirectory()) {
                    File outputFile = destinationFolder.resolve(zipEntry.getName()).toFile();
                    outputFile.getParentFile().mkdirs();
                    IOUtils.copy(zipInputStream, new FileOutputStream(outputFile));
                    if (outputFile.getParentFile().getName().equals("bin"))
                        outputFile.setExecutable(true);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void uncompressTarGz(Path archivePath, Path destinationFolder) {
        try (FileInputStream fis = new FileInputStream(archivePath.toFile());
             GZIPInputStream gzipInputStream = new GZIPInputStream(new BufferedInputStream(fis));
             TarArchiveInputStream tis = new TarArchiveInputStream(gzipInputStream)) {
            TarArchiveEntry tarEntry;
            while ((tarEntry = tis.getNextTarEntry()) != null) {
                //System.out.println(" tar entry- " + tarEntry.getName());
                if (tarEntry.isFile()) {
                    File outputFile = destinationFolder.resolve(tarEntry.getName()).toFile();
                    outputFile.getParentFile().mkdirs();
                    IOUtils.copy(tis, new FileOutputStream(outputFile));
                    if (outputFile.getParentFile().getName().equals("bin"))
                        outputFile.setExecutable(true);
                }
            }
        } catch (Exception e) {
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
