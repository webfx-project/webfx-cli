package dev.webfx.tool.cli.core;

import dev.webfx.tool.cli.commands.Bump;
import dev.webfx.tool.cli.util.process.ProcessCall;
import org.apache.maven.shared.invoker.*;

import java.io.File;
import java.nio.file.Path;
import java.util.Collections;

/**
 * @author Bruno Salmon
 */
public final class MavenCaller {
    private final static boolean USE_MAVEN_INVOKER = false; // if false, just using shell invocation
    private final static boolean ASK_MAVEN_LOCAL_REPOSITORY = false; // if false, we will use the default path: ${user.home}/.m2/repository
    final static Path M2_LOCAL_REPOSITORY = ASK_MAVEN_LOCAL_REPOSITORY ?
            // Maven invocation (advantage: returns the correct path 100% sure / disadvantage: takes a few seconds to execute)
            Path.of(new ProcessCall("mvn -N help:evaluate -Dexpression=settings.localRepository -q -DforceStdout").getLastResultLine())
            // Otherwise, getting the standard path  (advantage: immediate / disadvantage: not 100% sure (the developer may have changed the default Maven settings)
            : Path.of(System.getProperty("user.home"), ".m2", "repository");
    private static Invoker MAVEN_INVOKER; // Will be initialised later if needed

    public static void invokeMavenGoal(String goal) {
        invokeMavenGoal(goal, new ProcessCall());
    }

    public static void invokeDownloadMavenGoal(String goal) {
        invokeMavenGoal(goal, new ProcessCall().setLogLineFilter(line -> line.startsWith("Downloading")));
    }

    public static void invokeMavenGoal(String goal, ProcessCall processCall) {
        processCall.setCommand("mvn " + goal);
        boolean directoryChanged = processCall.getWorkingDirectory() != null && !processCall.getWorkingDirectory().getAbsolutePath().equals(System.getProperty("user.dir"));
        boolean gluonPluginCall = goal.contains("gluonfx:");
        if (!USE_MAVEN_INVOKER && !directoryChanged && !gluonPluginCall) { // We don't call mvn this way from another directory due to a bug (ex: activating "gluon-desktop" profile from another directory doesn't set the target to "host" -> stays to "TBD" which makes the Gluon plugin fail)
            // Preferred way as it's not necessary to eventually call "mvn -version", so it's quicker
            processCall.setErrorLineFilter(line -> line.contains("ERROR"))
                .executeAndWait();
            if (processCall.getLastErrorLine() != null)
                throw new WebFxCliException("An error was detected during Maven invocation:\n" + processCall.getLastErrorLine());
        } else {
            processCall.logCallCommand();
            InvocationRequest request = new DefaultInvocationRequest();
            request.setBaseDirectory(processCall.getWorkingDirectory());
            if (gluonPluginCall) {
                Path graalVmHome = Bump.getGraalVmHome();
                if (graalVmHome != null) {
                    String home = graalVmHome.toString();
                    request.addShellEnvironment("GRAALVM_HOME", home);
                    //request.addShellEnvironment("JAVA_HOME", home);
                }
            }
            request.setGoals(Collections.singletonList(goal));
            if (MAVEN_INVOKER == null) {
                MAVEN_INVOKER = new DefaultInvoker();
                String mavenHome = System.getProperty("maven.home");
                if (mavenHome == null)
                    // Invoking mvn -version through the shell to get the maven home (takes about 300ms)
                    mavenHome = new ProcessCall("mvn -version")
                            .setResultLineFilter(line -> line.startsWith("Maven home:"))
                            .executeAndWait()
                            .getLastResultLine().substring(11).trim();
                MAVEN_INVOKER.setMavenHome(new File(mavenHome));
            }
            try {
                MAVEN_INVOKER.execute(request);
            } catch (MavenInvocationException e) {
                throw new WebFxCliException("An error occurred during Maven invocation: " + e.getMessage());
            }
        }
    }
}
