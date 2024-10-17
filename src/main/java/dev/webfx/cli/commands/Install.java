package dev.webfx.cli.commands;

import dev.webfx.cli.core.CliException;
import dev.webfx.cli.core.Logger;
import dev.webfx.cli.core.WebFXHiddenFolder;
import dev.webfx.cli.util.os.OperatingSystem;
import dev.webfx.cli.util.os.OsFamily;
import dev.webfx.cli.util.process.ProcessCall;
import dev.webfx.cli.util.splitfiles.SplitFiles;
import dev.webfx.lib.reusablestream.ReusableStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.utils.IOUtils;
import picocli.CommandLine.Command;

import java.io.*;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.*;
import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

/**
 * @author Bruno Salmon
 */
@Command(name = "install", description = "Install or upgrade a third-party software.",
        subcommands = {
                Install.GraalVm.class,
                Install.Wix.class,
                Install.Inno.class,
                Install.VsTools.class,
                Install.UbuntuTools.class,
                Install.XcodeTools.class
        })
public final class Install extends CommonSubcommand {

    private static final boolean LATEST_VERSION = false; // Can't make the latest version work at the moment

    @Command(name = "graalvm", description = "Install or upgrade GraalVM.")
    static class GraalVm extends CommonSubcommand implements Runnable {

        // To see all releases: https://api.github.com/repos/gluonhq/graal/releases

        private final static String GITHUB_GRAAL_RELEASE_PAGE_URL = LATEST_VERSION ?
            "https://api.github.com/repos/gluonhq/graal/releases/latest" :
            "https://api.github.com/repos/gluonhq/graal/releases/68947997"; // This is gluon-22.1.0.1-Final

        @Override
        public void run() {
            String osToken = null;
            switch (OperatingSystem.getOsFamily()) {
                case WINDOWS: osToken = "windows"; break;
                case MAC_OS: osToken = "darwin"; break;
                case LINUX: osToken = "linux"; break;
            }
            String archToken = "aarch64".equalsIgnoreCase(System.getProperty("os.arch")) ? "aarch64" : "amd64";
            Logger.log("Checking for update on " + GITHUB_GRAAL_RELEASE_PAGE_URL);
            String pageContent = downloadPage(GITHUB_GRAAL_RELEASE_PAGE_URL);
            Pattern pattern = Pattern.compile(
                LATEST_VERSION ?
                    "\"browser_download_url\":\"([^\"]*/gluonhq/graal/releases/download/[^\"]*-java[^\"]*-" + osToken + "-" + archToken + "[^\"]*)\"" :
                    "\"browser_download_url\":\"([^\"]*/gluonhq/graal/releases/download/[^\"]*-java17[^\"]*-" + osToken + "[^\"]*)\""
                , 1);
            Matcher matcher = pattern.matcher(pageContent);
            if (!matcher.find()) {
                Logger.log("No GraalVM found for your system!");
                return;
            }

            String vmUrl = matcher.group(1);
            if (vmUrl.startsWith("/"))
                vmUrl = "https://github.com" + vmUrl;

            Path hiddenVmFolder = WebFXHiddenFolder.getCliSubFolder("graalvm");
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

    @Command(name = "wix", description = "Install or upgrade the WiX Toolset (Windows).")
    static class Wix extends CommonSubcommand implements Runnable {

        private final static String GITHUB_WIX_RELEASE_PAGE_URL = "https://github.com/wixtoolset/wix3/releases";

        @Override
        public void run() {
            if (OperatingSystem.getOsFamily() != OsFamily.WINDOWS)
                throw new CliException("This command is to be executed on Windows machines only.");

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

            Path hiddenWixFolder = WebFXHiddenFolder.getCliSubFolder("wix");
            String wixDownloadFileName = wixUrl.substring(wixUrl.lastIndexOf('/') + 1);
            Path wixDownloadFilePath = hiddenWixFolder.resolve(wixDownloadFileName);
            String wixName = wixDownloadFileName.substring(0, wixDownloadFileName.lastIndexOf('.')); // removing .zip or .gz extension

            // Downloading the archive file
            if (Files.exists(wixDownloadFilePath)) {
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
                    .setPowershellCommand("Start-Process powershell -Verb runAs 'Enable-WindowsOptionalFeature -Online -FeatureName NetFx3' -Wait; Start-Process .\\" + wixDownloadFileName + " -Wait")
                    .executeAndWait();
        }
    }

    @Command(name = "inno", description = "Install or upgrade Inno Setup (Windows).")
    static class Inno extends CommonSubcommand implements Runnable {

        private final static String INNO_RELEASE_PAGE_URL = "https://jrsoftware.org/isdl.php";

        @Override
        public void run() {
            if (OperatingSystem.getOsFamily() != OsFamily.WINDOWS)
                throw new CliException("This command is to be executed on Windows machines only.");

            Logger.log("Checking for update on " + INNO_RELEASE_PAGE_URL);
            String pageContent = downloadPage(INNO_RELEASE_PAGE_URL);
            Pattern pattern = Pattern.compile("href=\"(.*is.*\\.exe)\"", 1);
            Matcher matcher = pattern.matcher(pageContent);
            if (!matcher.find()) {
                Logger.log("No Inno Setup version found!");
                return;
            }

            String innoUrl = matcher.group(1);
            if (innoUrl.startsWith("/"))
                innoUrl = "https://jrsoftware.org" + innoUrl;

            Path hiddenInnoFolder = WebFXHiddenFolder.getCliSubFolder("inno");
            String innoDownloadFileName = innoUrl.substring(innoUrl.lastIndexOf('/') + 1);
            Path innoDownloadFilePath = hiddenInnoFolder.resolve(innoDownloadFileName);
            String innoName = innoDownloadFileName.substring(0, innoDownloadFileName.lastIndexOf('.')); // removing .zip or .gz extension

            // Downloading the archive file
            if (Files.exists(innoDownloadFilePath)) {
                Logger.log("Already up-to-date (" + innoName + ")");
                return;
            }

            // Deleting all files to clear up space
            if (Files.exists(hiddenInnoFolder)) {
                Logger.log("New version available!: " + innoName);
                ReusableStream.create(() -> SplitFiles.uncheckedWalk(hiddenInnoFolder))
                        .sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
            } else
                Logger.log("Latest version: " + innoName);

            // Downloading the GraalVM file
            Logger.log("Downloading " + innoUrl);
            downloadFile(innoUrl, innoDownloadFilePath);

            new ProcessCall()
                    .setWorkingDirectory(hiddenInnoFolder)
                    .setCommandTokens(innoDownloadFileName)
                    .executeAndWait();
        }
    }

    @Command(name = "vs-tools", description = "Install or upgrade the Visual Studio Build Tools (Windows).")
    static class VsTools extends CommonSubcommand implements Runnable {

        private final static String VS_BUILD_TOOLS_URL = "https://aka.ms/vs/17/release/vs_buildtools.exe";

        @Override
        public void run() {
            if (OperatingSystem.getOsFamily() != OsFamily.WINDOWS)
                throw new CliException("This command is to be executed on Windows machines only.");

            Path hiddenVsFolder = WebFXHiddenFolder.getCliSubFolder("vstools");
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
                    .setCommandTokens(vsDownloadFileName
                            , "--passive" // Display the installer user interface to show progress bars but in automatic mode (doesn't interact with the user)
                            , "--addProductLang",  "En-us" // Mentioned in Gluon doc
                            , "--add", "Microsoft.VisualStudio.Component.VC.Tools.x86.x64" // Not mentioned in Gluon doc => install cl.exe (called by Gluon toolchain)
                            , "--add",  "Microsoft.VisualStudio.Component.Windows10SDK.19041" // Mentioned in Gluon doc => if not installed, the Gluon toolchain raises this error: launcher.c(28): fatal error C1083: Cannot open include file: 'stdio.h': No such file or directory
                            // , "--add", "Microsoft.VisualStudio.Component.VC.CLI.Support" // Mentioned in Gluon doc (but not sure what it is for)
                            // , "--add", "Microsoft.VisualStudio.ComponentGroup.VC.Tools.142.x86.x64" // Mentioned in Gluon doc (but not sure what it is for)
                            // , "--add", "Microsoft.Component.VC.Runtime.UCRTSDK" // Mentioned in Gluon doc (but not sure what it is for)
                    )
                    .executeAndWait();
        }
    }

    @Command(name = "ubuntu-tools", description = "Install or upgrade the dev tools required by Gluon (Ubuntu).")
    static class UbuntuTools extends CommonSubcommand implements Runnable {

        @Override
        public void run() {
            if (OperatingSystem.getOsFamily() != OsFamily.LINUX)
                throw new CliException("This command is to be executed on Ubuntu machines only.");

            ProcessCall.executeBashCommand("sudo apt install g++ libasound2-dev libavcodec-dev libavformat-dev libavutil-dev libgl-dev libgtk-3-dev libpango1.0-dev libxtst-dev");
        }
    }

    @Command(name = "xcode", description = "Check your Xcode installation is correct, otherwise redirect to the Apple website (macOS).")
    static class XcodeTools extends CommonSubcommand implements Runnable {

        @Override
        public void run() {
            if (OperatingSystem.getOsFamily() != OsFamily.MAC_OS)
                throw new CliException("This command is to be executed on Macs only.");

            //ProcessCall.executeCommandTokens("xcode-select", "--install"); // Installs the command line tools (not enough for Gluon)

            checkOrFixXcodePathForGluon(true);
        }
    }

    static boolean checkOrFixXcodePathForGluon(boolean install) {
        // Fixing wrong Xcode path on macOS causing build failure (see issue https://github.com/gluonhq/substrate/issues/978)
        String xcodePath = new ProcessCall("xcode-select", "-p")
                .setLogsCalling(install)
                .setLogsCallDuration(false)
                .setLogLineFilter(line -> install)
                .executeAndWait()
                .getLastResultLine();
        String expectedXcodePath = "/Applications/Xcode.app/Contents/Developer";
        if (expectedXcodePath.equals(xcodePath)) {
            if (install)
                Logger.log("Well done, your Xcode path is correctly set.");
        } else {
            Logger.log("Your Xcode path is incorrectly set. Get Xcode from https://apps.apple.com/us/app/xcode/id497799835 if not already installed, otherwise try the following command to fix the path:\n% sudo xcode-select -s " + expectedXcodePath);
            return false;
        }
        return true;
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
                    // Fixing security issue detected by CodeQL: Arbitrary file access during archive extraction ("Zip Slip")
                    // => Checking the file will not be outside the destination folder
                    if (!outputPath.normalize().startsWith(destinationFolder))
                        throw new RuntimeException("Bad zip entry");
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
            if (e instanceof RuntimeException)
                throw (RuntimeException) e;
            throw new RuntimeException(e);
        }
    }

}
