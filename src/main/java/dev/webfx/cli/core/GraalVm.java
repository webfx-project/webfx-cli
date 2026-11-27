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
 * one installed by {@code webfx install graalvm} in the WebFX hidden folder.
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
            origin = "webfx install graalvm";
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
        Path release = home.resolve("release");
        if (!Files.exists(release))
            return false;
        try {
            for (String line : Files.readAllLines(release)) {
                if (line.startsWith("MODULES=") && line.contains("javafx."))
                    return true;
            }
        } catch (IOException e) {
            return false;
        }
        return false;
    }
}
