package dev.webfx.cli.commands;

import dev.webfx.cli.core.*;
import dev.webfx.cli.exceptions.CliException;
import dev.webfx.cli.modulefiles.DevMavenPomModuleFile;
import dev.webfx.cli.util.os.OperatingSystem;
import dev.webfx.cli.util.process.ProcessCall;

import javax.lang.model.SourceVersion;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

/**
 * Native desktop build per Java package ({@code webfx build -n --per-package}): the package of the main class goes
 * into the executable, the other packages of the project become shared libraries (DLLs on Windows), built with
 * GraalVM Native Image layers (experimental):
 * <ul>
 *     <li>by default one base layer with all these packages, and the executable on top of it;</li>
 *     <li>with {@code --layer-per-package} one layer per package (packages depending on each other share a layer),
 *     chained in dependency order, and the executable on top of the last one.</li>
 * </ul>
 *
 * <p>Each layer is built by a separate call of the native-maven-plugin in the native module (profile
 * native-desktop-layer of pom_native_executable.xml). So all layers get the same classpath, as Native Image requires.
 * The layers (.nil files) and their shared libraries are written in the target folder, next to the executable.
 */
final class NativeLayersBuild {

    static final String LAYER_PROFILE = "native-desktop-layer";
    private static final String LAYER_EXECUTION_GOAL = "native:compile-no-fork@build-native-layer";
    private static final String DEFAULT_MAIN_CLASS = "dev.webfx.platform.boot.ApplicationBooter";
    private static final String UNLOCK_EXPERIMENTAL_OPTIONS = "-H:+UnlockExperimentalVMOptions";

    private record Layer(String name, List<String> packages) { }

    static int execute(DevProjectModule nativeModule, CommandWorkspace workspace, boolean layerPerPackage) {
        Path graalVmHome = GraalVm.getNativeDesktopGraalVmHome();
        warnAboutLayersSupport(GraalVm.getVersion(graalVmHome));

        Path moduleHome = nativeModule.getHomeDirectory();
        Path targetDirectory = moduleHome.resolve("target");

        // 1) Runtime classpath and main class of the native module, as Maven resolves them
        Path classpathFile = targetDirectory.resolve("webfx-native-classpath.txt");
        int exitCode = MavenUtil.invokeMavenGoal("-q dependency:build-classpath -Dmdep.includeScope=runtime -Dmdep.outputFile=target/" + classpathFile.getFileName(),
            new ProcessCall().setWorkingDirectory(moduleHome));
        if (exitCode != 0 || !Files.exists(classpathFile))
            throw new CliException("Couldn't get the classpath of " + nativeModule.getName() + " (mvn dependency:build-classpath)");
        String mainClass = evaluateMainClass(moduleHome);

        // 2) The packages of the project on that classpath (the classes of the native module, and the jars built by
        // the modules of this workspace), with their dependencies between each other
        Map<String, Set<String>> packageDependencies = new TreeMap<>();
        for (Path entry : getProjectClasspathEntries(nativeModule, workspace, classpathFile))
            try {
                JavaPackages.readPackageDependencies(entry)
                    .forEach((pkg, dependencies) -> packageDependencies.computeIfAbsent(pkg, k -> new TreeSet<>()).addAll(dependencies));
            } catch (IOException e) {
                throw new CliException("Couldn't read the classes of " + entry + ": " + e.getMessage());
            }
        String mainPackage = JavaPackages.packageOfClassName(mainClass);
        if (packageDependencies.remove(mainPackage) != null)
            Logger.log("Package of the main class " + mainClass + " (goes into the executable): " + mainPackage);
        else
            Logger.log("Main class " + mainClass + " (goes into the executable with the libraries not used by the layers)");
        if (packageDependencies.isEmpty())
            throw new CliException("No project package found for the Native Image layers of " + nativeModule.getName());

        // 3) Layers
        String applicationName = DevMavenPomModuleFile.getApplicationName(nativeModule);
        List<Layer> layers = new ArrayList<>();
        if (layerPerPackage)
            for (List<String> packages : JavaPackages.sortDependenciesFirst(packageDependencies))
                layers.add(new Layer(toLayerName(packages.size() == 1 ? packages.get(0) : packages.get(0) + "-and-" + (packages.size() - 1) + "-more"), packages));
        else
            layers.add(new Layer(toLayerName(applicationName + "-base"), new ArrayList<>(packageDependencies.keySet())));
        if (layers.size() > 1)
            Logger.warning("GraalVM supports so far only one layer below the executable (base layer + application). With "
                           + layers.size() + " chained layers, the build will probably fail from the second layer on.");

        String previousLayerFile = null;
        for (int i = 0; i < layers.size(); i++) {
            Layer layer = layers.get(i);
            // Only the packages of the project (plus what they reach), not module=java.base as in the GraalVM examples:
            // the whole java.base module in a layer exhausts the builder heap (OutOfMemoryError with -J-Xmx3g)
            StringBuilder layerCreate = new StringBuilder(layer.name() + ".nil");
            layer.packages().forEach(pkg -> layerCreate.append(",package=").append(pkg));
            // The shared library of a layer must be named lib<name> (required by GraalVM 25.6: "Shared layer library
            // image name ... needs to start with 'lib'"), ex: libEpinephelus-base.dll for the layer Epinephelus-base.nil
            String libraryName = "lib" + layer.name();
            Logger.log("Native Image layer " + (i + 1) + "/" + layers.size() + ": " + layer.name() + " => " + libraryName
                       + (layer.packages().size() == 1 ? " (package " : " (packages ") + String.join(", ", layer.packages()) + ")");
            exitCode = invokeLayerBuild(nativeModule, libraryName, ".",
                UNLOCK_EXPERIMENTAL_OPTIONS + " -H:LayerCreate=" + layerCreate + (previousLayerFile == null ? "" : " -H:LayerUse=" + previousLayerFile));
            if (exitCode != 0) {
                Logger.log("Native Image layer " + layer.name() + " failed (see above)");
                return exitCode;
            }
            previousLayerFile = "target/" + layer.name() + ".nil";
        }

        // 4) The executable on top of the last layer
        Logger.log("Native Image executable " + applicationName + " on top of " + previousLayerFile);
        exitCode = invokeLayerBuild(nativeModule, applicationName, mainClass, UNLOCK_EXPERIMENTAL_OPTIONS + " -H:LayerUse=" + previousLayerFile);
        if (exitCode == 0)
            Logger.log("The executable needs the shared libraries of the layers next to it (target folder)"
                       + (OperatingSystem.isWindows() ? "" : OperatingSystem.isMacOs() ? ", or in DYLD_LIBRARY_PATH" : ", or in LD_LIBRARY_PATH"));
        return exitCode;
    }

    private static void warnAboutLayersSupport(String graalVmVersion) {
        String graalVm = "GraalVM " + (graalVmVersion == null ? "(unknown version)" : graalVmVersion);
        if (!OperatingSystem.isLinux() && !GraalVm.isVersionAtLeast(graalVmVersion, 25, 1))
            Logger.warning("Native Image layers are only available on Linux with " + graalVm + " (Windows: experimental from GraalVM 25.1, see webfx install graalvm-latest)."
                           + " The build continues anyway, but it will probably fail or produce an executable that doesn't work yet.");
        else
            Logger.warning("Native Image layers are an experimental GraalVM feature (" + graalVm + ").");
    }

    /**
     * @return the value of the webfx.mainClass property of the native module (normally inherited from webfx-parent)
     */
    private static String evaluateMainClass(Path moduleHome) {
        ProcessCall processCall = new ProcessCall()
            .setWorkingDirectory(moduleHome)
            // Only the class name (JVM warnings may come in between on stderr)
            .setResultLineFilter(line -> SourceVersion.isName(line.trim()));
        MavenUtil.invokeMavenGoal("-q help:evaluate -Dexpression=webfx.mainClass -DforceStdout", processCall);
        String mainClass = processCall.getLastResultLine();
        if (mainClass != null)
            mainClass = mainClass.trim();
        if (processCall.getExitCode() != 0 || mainClass == null || !SourceVersion.isName(mainClass)) {
            Logger.warning("Couldn't evaluate webfx.mainClass, assuming " + DEFAULT_MAIN_CLASS);
            mainClass = DEFAULT_MAIN_CLASS;
        }
        return mainClass;
    }

    /**
     * @return the classes directory of the native module, and the classpath entries that are jars built by the modules
     *         of this workspace (recognised by their file name, ex: epinephelus-client-1.0.0-SNAPSHOT.jar)
     */
    private static List<Path> getProjectClasspathEntries(DevProjectModule nativeModule, CommandWorkspace workspace, Path classpathFile) {
        Set<String> workspaceJarNames = new HashSet<>();
        workspace.getTopRootModule().getThisAndChildrenModulesInDepth()
            .filter(DevProjectModule.class::isInstance)
            .map(DevProjectModule.class::cast)
            .forEach(m -> {
                Path target = m.getHomeDirectory().resolve("target");
                if (Files.isDirectory(target))
                    try (Stream<Path> files = Files.list(target)) {
                        files.map(p -> p.getFileName().toString())
                            .filter(name -> name.endsWith(".jar") && !name.matches(".*-(sources|javadoc|tests|fat)\\.jar"))
                            .forEach(workspaceJarNames::add);
                    } catch (IOException ignored) {
                    }
            });
        List<Path> entries = new ArrayList<>();
        entries.add(nativeModule.getHomeDirectory().resolve("target/classes"));
        try {
            for (String entry : Files.readString(classpathFile).trim().split(File.pathSeparator)) {
                if (entry.isBlank())
                    continue;
                Path path = Path.of(entry.trim());
                if (workspaceJarNames.contains(path.getFileName().toString()))
                    entries.add(path);
            }
        } catch (IOException e) {
            throw new CliException("Couldn't read " + classpathFile + ": " + e.getMessage());
        }
        return entries;
    }

    /**
     * Calls the native-maven-plugin with the profile native-desktop-layer (see pom_native_executable.xml).
     *
     * @param mainClass "." for a layer (no main class)
     * @param layerArgs the native-image arguments for the layer (the plugin splits them at spaces)
     */
    private static int invokeLayerBuild(DevProjectModule nativeModule, String imageName, String mainClass, String layerArgs) {
        String goal = "-P " + LAYER_PROFILE + " " + LAYER_EXECUTION_GOAL
                      + " " + singleQuoted("-Dwebfx.native.layer.imageName=" + imageName)
                      + " " + singleQuoted("-Dwebfx.native.layer.mainClass=" + mainClass)
                      + " " + singleQuoted("-Dwebfx.native.layer.args=" + layerArgs);
        // Through the shell (PowerShell or bash), so that the single-quoted arguments arrive in one piece
        return Build.invokeNativeMavenGoal(nativeModule, goal, true);
    }

    private static String singleQuoted(String argument) {
        if (argument.contains("'"))
            throw new CliException("Unexpected quote in argument " + argument);
        return "'" + argument + "'";
    }

    /**
     * Layer names may only contain letters, digits, dots, underscores and hyphens (native-maven-plugin).
     */
    private static String toLayerName(String name) {
        return name.replaceAll("[^A-Za-z0-9._-]", "_");
    }

}
