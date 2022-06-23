package dev.webfx.tool.cli.commands;

import dev.webfx.lib.reusablestream.ReusableStream;
import dev.webfx.tool.cli.WebFx;
import dev.webfx.tool.cli.core.Logger;
import dev.webfx.tool.cli.core.WebFxCliException;
import dev.webfx.tool.cli.util.os.OperatingSystem;
import dev.webfx.tool.cli.util.os.OsFamily;
import dev.webfx.tool.cli.util.process.ProcessCall;
import dev.webfx.tool.cli.util.splitfiles.SplitFiles;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.utils.IOUtils;
import picocli.CommandLine.Command;

import java.io.*;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.*;
import java.util.Comparator;
import java.util.Spliterators;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

/**
 * @author Bruno Salmon
 */
@Command(name = "bump", description = "Bump to a new version if available.",
        subcommands = {
                Bump.Cli.class,
                Bump.GraalVm.class,
                Bump.Wix.class,
                Bump.VsTools.class
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


    @Command(name = "graalvm", description = "Install GraalVM or bump it to a newer version if available.")
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
            Pattern pattern = Pattern.compile("href=\"(.*/gluonhq/graal/releases/download/.*-java17-" + osToken + "-\\S*)\"", 1);
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
            boolean isZip = vmDownloadFileName.endsWith(".zip");
            boolean isTarGz = vmDownloadFileName.endsWith(".tar.gz");
            String vmName = vmDownloadFileName.substring(0, vmDownloadFileName.lastIndexOf('.')); // removing .zip or .gz extension
            if (isTarGz)
                vmName = vmName.substring(0, vmName.lastIndexOf('.')); // removing .tar extension
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
            if (!isZip && !isTarGz)
                Logger.log("Unknown compression format. Please manually uncompress " + vmDownloadFilePath);
            else {
                Logger.log("Uncompressing " + vmDownloadFilePath.getFileName());

                if (isZip)
                    unzip(vmDownloadFilePath, hiddenVmFolder);
                else
                    uncompressTarGz(vmDownloadFilePath, hiddenVmFolder);

                // Deleting the archive files to free space
                vmDownloadFilePath.toFile().delete();
            }

            // Recreating the tag file (used to know which graalvm version is installed)
            try {
                vmTagFilePath.toFile().createNewFile();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Command(name = "wix", description = "Install WiX Toolset or bump it to a newer version if available.")
    static class Wix extends CommonSubcommand implements Runnable {

        private final static String GITHUB_WIX_RELEASE_PAGE_URL = "https://github.com/wixtoolset/wix3/releases";

        @Override
        public void run() {
            if (OperatingSystem.getOsFamily() != OsFamily.WINDOWS)
                throw new WebFxCliException("This command is to be executed on Windows machines only.");

            Logger.log("Checking for update on " + GITHUB_WIX_RELEASE_PAGE_URL);
            String pageContent = downloadPage(GITHUB_WIX_RELEASE_PAGE_URL);
            Pattern pattern = Pattern.compile("href=\"(.*wix.*\\.exe)\"", 1);
            Matcher matcher = pattern.matcher(pageContent);
            if (!matcher.find()) {
                Logger.log("No WiX version found!");
                return;
            }

            String wixUrl = matcher.group(1);
            if (wixUrl.startsWith("/"))
                wixUrl = "https://github.com" + wixUrl;

            Path cliRepositoryPath = getCliRepositoryPath();
            Path hiddenWixFolder = cliRepositoryPath.resolve(".wix");
            String wixDownloadFileName = wixUrl.substring(wixUrl.lastIndexOf('/') + 1);
            Path wixDownloadFilePath = hiddenWixFolder.resolve(wixDownloadFileName);
            String wixName = wixDownloadFileName.substring(0, wixDownloadFileName.lastIndexOf('.')); // removing .zip or .gz extension
            Path wixTagFilePath = wixDownloadFilePath;

            // Downloading the archive file
            if (Files.exists(wixTagFilePath)) {
                Logger.log("Already up-to-date (" + wixName + ")");
                return;
            }

            // Deleting all files to clear up space
            if (Files.exists(hiddenWixFolder)) {
                Logger.log("New version available!: " + wixName);
                ReusableStream.create(() -> SplitFiles.uncheckedWalk(hiddenWixFolder))
                        .sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
            } else
                Logger.log("Latest version: " + wixName);

            // Downloading the GraalVM file
            Logger.log("Downloading " + wixUrl);
            downloadFile(wixUrl, wixDownloadFilePath);

            new ProcessCall()
                    .setWorkingDirectory(hiddenWixFolder)
                    .setCommand("Start-Process powershell -Verb runAs 'Enable-WindowsOptionalFeature -Online -FeatureName \"NetFx3\"' -Wait; " + wixDownloadFileName)
                    .executeAndWait();
        }
    }

    @Command(name = "vstools", description = "Download and start the Visual Studio Build Tools installer.")
    static class VsTools extends CommonSubcommand implements Runnable {

        private final static String VS_BUILD_TOOLS_URL = "https://aka.ms/vs/17/release/vs_buildtools.exe";

        @Override
        public void run() {
            if (OperatingSystem.getOsFamily() != OsFamily.WINDOWS)
                throw new WebFxCliException("This command is to be executed on Windows machines only.");

            Path cliRepositoryPath = getCliRepositoryPath();
            Path hiddenVsFolder = cliRepositoryPath.resolve(".vs");
            String vsUrl = VS_BUILD_TOOLS_URL;
            String vsDownloadFileName = vsUrl.substring(vsUrl.lastIndexOf('/') + 1);
            Path vsDownloadFilePath = hiddenVsFolder.resolve(vsDownloadFileName);

            // Downloading the installation wizard
            if (!Files.exists(vsDownloadFilePath)) {
                Logger.log("Downloading " + vsUrl);
                downloadFile(vsUrl, vsDownloadFilePath);
            }

            new ProcessCall()
                    .setWorkingDirectory(hiddenVsFolder)
                    .setCommand(vsDownloadFileName + " --passive" + // Display the installer user interface to show progress bars but in automatic mode (doesn't interact with the user)
                            " --addProductLang En-us" + // Mentioned in Gluon doc
                            " --add Microsoft.VisualStudio.Component.VC.Tools.x86.x64" + // Not mentioned in Gluon doc => install cl.exe (called by Gluon toolchain)
                            " --add Microsoft.VisualStudio.Component.Windows10SDK.19041" + // Mentioned in Gluon doc => if not installed, the Gluon toolchain raises this error: launcher.c(28): fatal error C1083: Cannot open include file: 'stdio.h': No such file or directory
                            " --add Microsoft.VisualStudio.Component.VC.CLI.Support" + // Mentioned in Gluon doc (but not sure what it is for)
                            " --add Microsoft.VisualStudio.ComponentGroup.VC.Tools.142.x86.x64" + // Mentioned in Gluon doc (but not sure what it is for)
                            " --add Microsoft.Component.VC.Runtime.UCRTSDK" // Mentioned in Gluon doc (but not sure what it is for)

                    )
                    .executeAndWait();
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

    private static void uncompressTarGz(Path archivePath, Path destinationFolder) {
        try (FileInputStream fis = new FileInputStream(archivePath.toFile());
             GZIPInputStream gzipInputStream = new GZIPInputStream(new BufferedInputStream(fis));
             TarArchiveInputStream tis = new TarArchiveInputStream(gzipInputStream)) {
            TarArchiveEntry tarEntry;
            while ((tarEntry = tis.getNextTarEntry()) != null) {
                if (tarEntry.isFile()) {
                    Path outputPath = destinationFolder.resolve(tarEntry.getName());
                    File outputFile = outputPath.toFile();
                    outputFile.getParentFile().mkdirs();
                    if (tarEntry.isSymbolicLink())
                        Files.createSymbolicLink(outputPath, outputPath.getParent().resolve(tarEntry.getLinkName()));
                    else {
                        IOUtils.copy(tis, new FileOutputStream(outputFile));
                        // Some files are executable files, so setting the executable flag
                        outputFile.setExecutable(true); // Doing it for all files (not beautiful but simple)
                    }
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
