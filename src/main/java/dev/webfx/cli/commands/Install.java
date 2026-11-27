package dev.webfx.cli.commands;

import dev.webfx.cli.exceptions.CliException;
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
import java.util.Arrays;
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
                Install.GraalVmLatest.class,
                Install.Wix.class,
                Install.Inno.class,
                Install.VsTools.class,
                Install.UbuntuTools.class,
                Install.XcodeTools.class
        })
public final class Install extends CommonSubcommand {

    @Command(name = "graalvm", description = "Install or upgrade GraalVM Community Edition, latest LTS release (without JavaFX).")
    static class GraalVm extends CommonSubcommand implements Runnable {

        @Override
        public void run() {
            installGraalVm(false);
        }
    }

    @Command(name = "graalvm-latest", description = "Install or upgrade GraalVM Community Edition, latest non-LTS release (ex: 25.6, without JavaFX)."
        + " It replaces the GraalVM installed by 'webfx install graalvm' (and vice versa).")
    static class GraalVmLatest extends CommonSubcommand implements Runnable {

        @Override
        public void run() {
            installGraalVm(true);
        }
    }

    // GraalVM Community Edition, releases of github.com/graalvm/graalvm-ce-builds. It has no JavaFX in its JDK (unlike
    // Liberica NIK Full, which conflicts with the OpenJFX jars that the native-desktop profile puts on the classpath:
    // the executable then crashes at startup, see dev.webfx.cli.core.GraalVm).
    // API documentation: https://docs.github.com/en/rest/releases/releases#list-releases
    private final static String GRAALVM_CE_RELEASES_API_URL = "https://api.github.com/repos/graalvm/graalvm-ce-builds/releases?per_page=100";

    /**
     * Installs the latest LTS or non-LTS release of GraalVM Community Edition in the WebFX hidden folder (used by native
     * builds when GRAALVM_HOME is not set, see dev.webfx.cli.core.GraalVm). Both share that folder, so installing one
     * replaces the other.
     */
    private static void installGraalVm(boolean latestNonLts) {
        String osToken = null, packageType = null;
        switch (OperatingSystem.getOsFamily()) {
            case WINDOWS: osToken = "windows"; packageType = "zip"; break;
            case MAC_OS: osToken = "macos"; packageType = "tar.gz"; break;
            case LINUX: osToken = "linux"; packageType = "tar.gz"; break;
        }
        String osArch = System.getProperty("os.arch");
        String archToken = "aarch64".equalsIgnoreCase(osArch) || "arm64".equalsIgnoreCase(osArch) ? "aarch64" : "x64";
        Logger.log("Checking for update on " + GRAALVM_CE_RELEASES_API_URL + " (latest " + (latestNonLts ? "non-LTS" : "LTS") + " release)");
        String vmUrl = findGraalVmDownloadUrl(downloadPage(GRAALVM_CE_RELEASES_API_URL), latestNonLts, osToken, archToken, packageType);
        if (vmUrl == null) {
            Logger.log("No GraalVM found for your system!");
            return;
        }
        installGraalVmArchive(vmUrl);
        Path graalVmHome = WebFXHiddenFolder.getGraalVmHome();
        Logger.log("GraalVM home: " + graalVmHome);
        String graalVmHomeEnv = System.getenv("GRAALVM_HOME");
        if (graalVmHomeEnv != null && !graalVmHomeEnv.isBlank() && graalVmHome != null && !Path.of(graalVmHomeEnv.trim()).equals(graalVmHome))
            Logger.log("Note: native builds use GRAALVM_HOME first (" + graalVmHomeEnv.trim() + "). To build with this GraalVM, set GRAALVM_HOME to the path above or remove it.");
    }

    /**
     * Selects the archive of the latest LTS or non-LTS release in the release list of graalvm-ce-builds. Each asset URL
     * contains the release tag and the file name, ex:
     * <ul>
     *     <li>.../download/jdk-25.0.2/graalvm-community-jdk-25.0.2_windows-x64_bin.zip: GraalVM for JDK 25, LTS like JDK 25</li>
     *     <li>.../download/graal-25.6.5.1/graalvm-community-jdk-25i6-25.0.5.1_windows-x64_bin.zip: innovation release 25.6 (non-LTS)</li>
     *     <li>.../download/jdk-24.0.2/graalvm-community-jdk-24.0.2_windows-x64_bin.zip: GraalVM for JDK 24 (non-LTS like JDK 24)</li>
     * </ul>
     * The release with the highest version (from its tag) wins. The .sha256 and .sig files are excluded by requiring the
     * archive extension at the end.
     *
     * @return the download URL, or null if no release matches
     */
    static String findGraalVmDownloadUrl(String releasesJson, boolean latestNonLts, String osToken, String archToken, String packageType) {
        Pattern assetPattern = Pattern.compile("\"browser_download_url\"\\s*:\\s*\"([^\"]*/download/([^\"/]+)/(graalvm-community-jdk-[^\"/]*_"
            + Pattern.quote(osToken + "-" + archToken) + "_bin\\." + Pattern.quote(packageType) + "))\"");
        Pattern innovationPattern = Pattern.compile("graalvm-community-jdk-\\d+i\\d+-.*"); // ex: jdk-25i6-
        Pattern jdkMajorPattern = Pattern.compile("graalvm-community-jdk-(\\d+)");
        Pattern versionPattern = Pattern.compile("\\d+(\\.\\d+)*");
        String bestUrl = null;
        int[] bestVersion = null;
        Matcher matcher = assetPattern.matcher(releasesJson);
        while (matcher.find()) {
            String url = matcher.group(1), tag = matcher.group(2), fileName = matcher.group(3);
            Matcher jdkMajorMatcher = jdkMajorPattern.matcher(fileName);
            Matcher versionMatcher = versionPattern.matcher(tag);
            if (!jdkMajorMatcher.lookingAt() || !versionMatcher.find())
                continue;
            boolean lts = !innovationPattern.matcher(fileName).matches() && isLtsJdkVersion(Integer.parseInt(jdkMajorMatcher.group(1)));
            if (lts == latestNonLts)
                continue;
            int[] version = Stream.of(versionMatcher.group().split("\\.")).mapToInt(Integer::parseInt).toArray();
            if (bestVersion == null || Arrays.compare(version, bestVersion) > 0) {
                bestUrl = url;
                bestVersion = version;
            }
        }
        return bestUrl;
    }

    /**
     * JDK LTS versions: 8, 11, 17, and since then every two years (every fourth release): 21, 25, 29...
     */
    private static boolean isLtsJdkVersion(int major) {
        return major == 8 || major == 11 || major == 17 || major >= 21 && (major - 21) % 4 == 0;
    }

    private static void installGraalVmArchive(String vmUrl) {
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

        // Deleting all files to clear up space (also another GraalVM installed there before)
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
                            , "--addProductLang",  "En-us"
                            , "--add", "Microsoft.VisualStudio.Component.VC.Tools.x86.x64" // => installs cl.exe (called by native-image)
                            , "--add",  "Microsoft.VisualStudio.Component.Windows10SDK.19041" // => if not installed, native-image raises this error: launcher.c(28): fatal error C1083: Cannot open include file: 'stdio.h': No such file or directory
                    )
                    .executeAndWait();
        }
    }

    @Command(name = "ubuntu-tools", description = "Install or upgrade the dev tools required by GraalVM native-image for JavaFX (Ubuntu).")
    static class UbuntuTools extends CommonSubcommand implements Runnable {

        @Override
        public void run() {
            if (OperatingSystem.getOsFamily() != OsFamily.LINUX)
                throw new CliException("This command is to be executed on Ubuntu machines only.");

            ProcessCall.executeBashCommand("sudo apt install build-essential zlib1g-dev g++ libasound2-dev libavcodec-dev libavformat-dev libavutil-dev libgl-dev libgtk-3-dev libpango1.0-dev libxtst-dev");
        }
    }

    @Command(name = "xcode", description = "Check your Xcode installation is correct, otherwise redirect to the Apple website (macOS).")
    static class XcodeTools extends CommonSubcommand implements Runnable {

        @Override
        public void run() {
            if (OperatingSystem.getOsFamily() != OsFamily.MAC_OS)
                throw new CliException("This command is to be executed on Macs only.");

            
            checkOrFixXcodePath(true);
        }
    }

    static boolean checkOrFixXcodePath(boolean install) {
        // Checking the Xcode path on macOS (a wrong path can cause native build failures)
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
