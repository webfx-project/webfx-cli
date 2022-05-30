package dev.webfx.tool.cli.commands;

import dev.webfx.lib.reusablestream.ReusableStream;
import dev.webfx.tool.cli.WebFx;
import dev.webfx.tool.cli.core.Logger;
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
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;
import java.util.Spliterators;
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

        private final static String LINUX_AMD64_GRAALVM_URL = "https://github.com/gluonhq/graal/releases/download/gluon-22.0.0.3-Final/graalvm-svm-java17-linux-gluon-22.0.0.3-Final.zip";
        private final static String LINUX_AARCH64_GRAALVM_URL = "https://github.com/graalvm/graalvm-ce-builds/releases/download/vm-22.1.0/graalvm-ce-java17-linux-aarch64-22.1.0.tar.gz";
        private final static String MACOS_AMD64_GRAALVM_URL = "https://github.com/graalvm/graalvm-ce-builds/releases/download/vm-22.1.0/graalvm-ce-java17-darwin-amd64-22.1.0.tar.gz";
        private final static String MACOS_AAECH64M1_GRAALVM_URL = "https://github.com/graalvm/graalvm-ce-builds/releases/download/vm-22.1.0/graalvm-ce-java17-darwin-aarch64-22.1.0.tar.gz";
        private final static String WINDOWS_AMD64_GRAALVM_URL = "https://github.com/graalvm/graalvm-ce-builds/releases/download/vm-22.1.0/graalvm-ce-java17-windows-amd64-22.1.0.zip";

        @Override
        public void run() {
            Path cliRepositoryPath = getCliRepositoryPath();
            Path hiddenVmFolder = cliRepositoryPath.resolve(".graalvm");
            String vmUrl = LINUX_AMD64_GRAALVM_URL;
            String vmArchiveFileName = vmUrl.substring(vmUrl.lastIndexOf('/') + 1);
            Path vmArchivePath = hiddenVmFolder.resolve(vmArchiveFileName);

            // Downloading the archive file
            if (!Files.exists(vmArchivePath)) {
                hiddenVmFolder.toFile().mkdirs();
                Logger.log("Downloading " + vmUrl);
                try (BufferedInputStream in = new BufferedInputStream(new URL(LINUX_AMD64_GRAALVM_URL).openStream());
                     FileOutputStream fileOutputStream = new FileOutputStream(vmArchivePath.toFile())) {
                    IOUtils.copy(in, fileOutputStream);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

            // Uncompressing the archive
            Logger.log("Uncompressing " + vmArchivePath.getFileName());

            if (vmArchivePath.endsWith(".tar.gz"))
                uncompressTarGz(vmArchivePath, hiddenVmFolder);
            else
                uncompressZip(vmArchivePath, hiddenVmFolder);

            //vmArchivePath.toFile().delete();
        }
    }

    private static void uncompressZip(Path archivePath, Path destinationFolder) {
        try (FileInputStream fis = new FileInputStream(archivePath.toFile());
             ZipInputStream zipInputStream = new ZipInputStream(new BufferedInputStream(fis));
             ) {
            ZipEntry zipEntry;
            while ((zipEntry = zipInputStream.getNextEntry()) != null) {
                System.out.println("zip entry: " + zipEntry.getName());
                if (!zipEntry.isDirectory()) {
                    File outputFile = destinationFolder.resolve(zipEntry.getName()).toFile();
                    outputFile.getParentFile().mkdirs();
                    if (outputFile.getParentFile().getName().equals("bin")) {
                        Set<PosixFilePermission> ownerWritable = PosixFilePermissions.fromString("rwxr-xr-x");
                        FileAttribute<?> permissions = PosixFilePermissions.asFileAttribute(ownerWritable);
                        Files.createFile(outputFile.toPath(), permissions);
                    }
                    IOUtils.copy(zipInputStream, new FileOutputStream(outputFile));
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
                    if (outputFile.getParentFile().getName().equals("bin")) {
                        Set<PosixFilePermission> ownerWritable = PosixFilePermissions.fromString("rwxr-xr-x");
                        FileAttribute<?> permissions = PosixFilePermissions.asFileAttribute(ownerWritable);
                        Files.createFile(outputFile.toPath(), permissions);
                    }
                    IOUtils.copy(tis, new FileOutputStream(outputFile));
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static Path getGraalVmHome() {
        Path cliRepositoryPath = getCliRepositoryPath();
        Path hiddenVmFolder = cliRepositoryPath.resolve(".graalvm");
        return ReusableStream.create(() -> Files.exists(hiddenVmFolder) ? SplitFiles.uncheckedWalk(hiddenVmFolder) : Spliterators.emptySpliterator())
                .filter(path -> !path.equals(hiddenVmFolder) && path.toFile().isDirectory())
                .findFirst()
                .orElse(null);
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
        return cliCodePath.endsWith(".jar") ? cliCodePath : null;
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
