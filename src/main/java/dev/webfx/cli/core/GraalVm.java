package dev.webfx.cli.core;

import dev.webfx.cli.exceptions.CliException;
import dev.webfx.cli.util.os.OperatingSystem;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Chooses the GraalVM used by native desktop builds ({@code webfx build -n}, Maven profile native-desktop).
 *
 * <p>Order: the GraalVM in the {@code GRAALVM_HOME} environment variable (if it contains native-image), otherwise the
 * one installed in the WebFX hidden folder by {@code webfx install graalvm} (latest LTS release) or
 * {@code webfx install graalvm-latest} (latest non-LTS release).
 *
 * <p>The chosen GraalVM must not contain JavaFX in its JDK (like Liberica NIK Full does): the native-desktop profile
 * takes JavaFX from the OpenJFX jars on the classpath (webfx-kit-javafxgraphics-openjfx). With JavaFX also in the JDK,
 * native-image links the JDK's JavaFX native code statically into the image while the Java side comes from the jars
 * (another JavaFX version), and the executable crashes at startup (segfault in WinApplication._init on Windows).
 */
public final class GraalVm {

    private GraalVm() {
    }

    /**
     * @return the GraalVM home for native desktop builds, or null if none was found (native-maven-plugin will then
     *         report the missing GraalVM itself)
     * @throws CliException if the GraalVM contains JavaFX (see class comment)
     */
    public static Path getNativeDesktopGraalVmHome() {
        Path home = fromEnvironment();
        String origin = "GRAALVM_HOME";
        if (home == null) {
            home = WebFXHiddenFolder.getGraalVmHome();
            origin = "webfx install graalvm / graalvm-latest";
        }
        if (home == null)
            return null;
        if (containsJavaFx(home))
            throw new CliException("The GraalVM " + home + " (" + origin + ") contains JavaFX in its JDK (e.g. Liberica NIK Full)."
                    + " Native desktop executables built with it crash at startup, because the JavaFX native code comes from the JDK"
                    + " but the JavaFX classes come from the OpenJFX jars. Please use a GraalVM without JavaFX (e.g. GraalVM Community)"
                    + ": set GRAALVM_HOME to it, or run 'webfx install graalvm' to install GraalVM Community.");
        Logger.log("Using GraalVM " + home + " (" + origin + ")");
        return home;
    }

    private static Path fromEnvironment() {
        String env = System.getenv("GRAALVM_HOME");
        if (env == null || env.isBlank())
            return null;
        Path home = Path.of(env.trim());
        String nativeImage = OperatingSystem.isWindows() ? "native-image.cmd" : "native-image";
        return Files.exists(home.resolve("bin").resolve(nativeImage)) ? home : null;
    }

    /**
     * Reads the MODULES line of the JDK release file (present in every JDK, including GraalVM distributions).
     */
    static boolean containsJavaFx(Path home) {
        String modules = readReleaseValue(home, "MODULES");
        return modules != null && modules.contains("javafx.");
    }

    /**
     * @return the GraalVM version of the release file (ex: "25.0.0" or "25.1.3"), or null if unknown
     */
    public static String getVersion(Path home) {
        return home == null ? null : readReleaseValue(home, "GRAALVM_VERSION");
    }

    /**
     * @return true if the version (ex: "25.1.3") is at least major.minor, false if it is older or unknown
     */
    public static boolean isVersionAtLeast(String version, int major, int minor) {
        if (version == null)
            return false;
        String[] tokens = version.split("[.+-]");
        try {
            int vMajor = Integer.parseInt(tokens[0]);
            int vMinor = tokens.length > 1 ? Integer.parseInt(tokens[1]) : 0;
            return vMajor > major || vMajor == major && vMinor >= minor;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static String readReleaseValue(Path home, String key) {
        Path release = home.resolve("release");
        if (!Files.exists(release))
            return null;
        try {
            for (String line : Files.readAllLines(release)) {
                if (line.startsWith(key + "=")) {
                    String value = line.substring(key.length() + 1).trim();
                    if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\""))
                        value = value.substring(1, value.length() - 1);
                    return value;
                }
            }
        } catch (IOException e) {
            return null;
        }
        return null;
    }
}
