package dev.webfx.cli.core;

import dev.webfx.cli.modulefiles.abstr.GavApi;
import dev.webfx.cli.util.os.OperatingSystem;
import dev.webfx.cli.util.process.ProcessCall;
import dev.webfx.cli.util.stopwatch.StopWatch;
import dev.webfx.cli.util.textfile.TextFileReaderWriter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Bruno Salmon
 */
public final class MavenUtil {

    public static final StopWatch MAVEN_INVOCATION_STOPWATCH = StopWatch.createSystemNanoStopWatch();

    //private final static boolean USE_MAVEN_INVOKER = false; // if false, just using shell invocation
    private final static boolean ASK_MAVEN_LOCAL_REPOSITORY = false; // if false, we will use the default path: ${user.home}/.m2/repository

    final static Path M2_LOCAL_REPOSITORY = ASK_MAVEN_LOCAL_REPOSITORY ?
            // Maven invocation (advantage: returns the correct path 100% sure / disadvantage: takes a few seconds to execute)
            Path.of(new ProcessCall("mvn", "-N", "help:evaluate", "-Dexpression=settings.localRepository", "-q", "-DforceStdout").getLastResultLine())
            // Otherwise, getting the standard path  (advantage: immediate / disadvantage: not 100% sure (the developer may have changed the default Maven settings)
            : Path.of(System.getProperty("user.home"), ".m2", "repository");
    //private static Invoker MAVEN_INVOKER; // Will be initialised later if needed

    private static boolean CLEAN_M2_SNAPSHOTS;

    public static void setCleanM2Snapshots(boolean cleanM2Snapshots) {
        CLEAN_M2_SNAPSHOTS = cleanM2Snapshots;
    }

    public static boolean isCleanM2Snapshots() {
        return CLEAN_M2_SNAPSHOTS;
    }

    public static void cleanM2ModuleSnapshotIfRequested(M2ProjectModule module) {
        if (CLEAN_M2_SNAPSHOTS && module.isSnapshotVersion()) {
            Path m2Path = module.getHomeDirectory();
            if (Files.exists(m2Path))
                try {
                    Files.walk(m2Path)
                            .sorted(Comparator.reverseOrder())
                            .map(Path::toFile)
                            .forEach(File::delete);
                } catch (IOException e) {
                    e.printStackTrace();
                }
        }
    }

    private static final Path NOT_FOUND_ARTIFACTS_PATH = WebFXHiddenFolder.getMavenWorkspace().resolve("NOT_FOUND_ARTIFACTS.txt");
    private static final Set<String> NOT_FOUND_ARTIFACTS = new HashSet<>();

    static {
        String content = TextFileReaderWriter.readCliTextFile(NOT_FOUND_ARTIFACTS_PATH);
        if (content != null)
            NOT_FOUND_ARTIFACTS.addAll(Arrays.asList(content.split("\n")));
    }

    private static void addNotFoundArtifact(String artifact) {
        NOT_FOUND_ARTIFACTS.add(artifact);
        try {
            TextFileReaderWriter.writeTextFileNow(artifact + "\n", NOT_FOUND_ARTIFACTS_PATH);
            Logger.warning(artifact + " was not found in Maven repositories, and is now blacklisted in " + NOT_FOUND_ARTIFACTS_PATH);
        } catch (IOException e) {
            Logger.warning("Couldn't write to file " + NOT_FOUND_ARTIFACTS_PATH.getFileName());
        }
    }

    private static MavenArtifactDownloader MAVEN_ARTIFACT_DOWNLOADER = MavenUtil::downloadArtifactProcess;

    public static void setMavenArtifactDownloader(MavenArtifactDownloader mavenArtifactDownloader) {
        MAVEN_ARTIFACT_DOWNLOADER = mavenArtifactDownloader;
    }

    public static boolean downloadArtifact(String groupId, String artifactId, String version, String classifier) {
        String artifact = groupId + ":" + artifactId + ":" + version + ":" + classifier;
        if (!NOT_FOUND_ARTIFACTS.contains(artifact)) {
            try {
                MAVEN_INVOCATION_STOPWATCH.on();
                return MAVEN_ARTIFACT_DOWNLOADER.downloadArtifact(groupId, artifactId, version, classifier);
            } catch (ArtifactNotFoundException e) {
                addNotFoundArtifact(artifact);
            } finally {
                MAVEN_INVOCATION_STOPWATCH.off();
            }
        }
        return false;
    }

    private static boolean downloadArtifactProcess(String groupId, String artifactId, String version, String classifier) {
        ProcessCall processCall = new ProcessCall();
        processCall
                .setLogLineFilter(line -> line.startsWith("Downloading"))
                .setErrorLineFilter(line -> {
                    boolean error = line.contains("ERROR");
                    if (error && isNotFoundArtifactError(line))
                        processCall.setLogsError(false);
                    return error;
                });
        int result = invokeMavenGoal("dependency:get -N -U -Dtransitive=false -Dartifact=" + groupId + ":" + artifactId + ":" + version + ":" + classifier, processCall);
        return result == 0;
    }

    public static boolean isNotFoundArtifactError(String error) {
        return error.contains(" was not found in ") || error.contains(" Could not find artifact ");
    }

    public static int invokeMavenGoal(String goal) {
        return invokeMavenGoal(goal, new ProcessCall());
    }

    public static int invokeMavenGoal(String goal, ProcessCall processCall) {
        MAVEN_INVOCATION_STOPWATCH.on();
        boolean gluonPluginCall = goal.contains("gluonfx:");
        Path graalVmHome = gluonPluginCall ? WebFXHiddenFolder.getGraalVmHome() : null;
        processCall.setCommand("mvn " + goal);
        //if (!USE_MAVEN_INVOKER) {
            // Preferred way as it's not necessary to eventually call "mvn -version", so it's quicker
            if (graalVmHome != null)
                if (OperatingSystem.isWindows())
                    processCall.setPowershellCommand("$env:GRAALVM_HOME = " + ProcessCall.toShellLogCommandToken(graalVmHome) + "; mvn " + goal);
                else
                    processCall.setBashCommand("export GRAALVM_HOME=" + ProcessCall.toShellLogCommandToken(graalVmHome) + "; mvn " + goal);
            processCall
                    //.setErrorLineFilter(line -> line.contains("ERROR")) // Commented as it prevents colors
                    .executeAndWait();
            if (processCall.hasErrorLines()) { // Happens only if the caller previously called setErrorLineFilter()
                String firstErrorLine = processCall.getFirstErrorLine();
                if (isNotFoundArtifactError(firstErrorLine))
                    throw new ArtifactNotFoundException(firstErrorLine);
                throw new CliException("Error(s) detected during Maven invocation:\n" + String.join("\n", processCall.getErrorLines()));
            }
        MAVEN_INVOCATION_STOPWATCH.off();
        return processCall.getExitCode();
        }/* else {
            processCall.logCallCommand();
            InvocationRequest request = new DefaultInvocationRequest();
            request.setBaseDirectory(processCall.getWorkingDirectory());
            if (graalVmHome != null) {
                String home = graalVmHome.toString();
                request.addShellEnvironment("GRAALVM_HOME", home);
                //request.addShellEnvironment("JAVA_HOME", home);
            }
            request.setGoals(Collections.singletonList(goal));
            if (MAVEN_INVOKER == null) {
                MAVEN_INVOKER = new DefaultInvoker();
                String mavenHome = System.getProperty("maven.home");
                if (mavenHome == null)
                    // Invoking mvn -version through the shell to get the maven home (takes about 300ms)
                    mavenHome = new ProcessCall("mvn", "-version")
                            .setResultLineFilter(line -> line.startsWith("Maven home:"))
                            .executeAndWait()
                            .getLastResultLine().substring(11).trim();
                MAVEN_INVOKER.setMavenHome(new File(mavenHome));
            }
            try {
                MAVEN_INVOKER.execute(request);
            } catch (MavenInvocationException e) {
                throw new CliException("An error occurred during Maven invocation: " + e.getMessage());
            }
        }
    }*/

    public static int invokeMavenGoalOnPomModule(ProjectModule module, String goal, ProcessCall processCall) {
        Path mavenWorkspace = getMavenModuleWorkspace(module);
        mavenWorkspace.toFile().mkdirs();
        try {
            Files.copy(module.getMavenModuleFile().getModuleFilePath(), mavenWorkspace.resolve("pom.xml"), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new CliException(e.getMessage());
        }
        processCall.setWorkingDirectory(mavenWorkspace);
        return invokeMavenGoal(goal, processCall);
    }

    public static Path getMavenModuleWorkspace(GavApi module) {
        return WebFXHiddenFolder.getMavenWorkspace()
                .resolve(module.getGroupId()).resolve(module.getArtifactId()).resolve(module.getVersion());
    }
}
