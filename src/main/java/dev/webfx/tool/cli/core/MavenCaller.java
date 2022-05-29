package dev.webfx.tool.cli.core;

import dev.webfx.tool.cli.util.process.ProcessCall;
import org.apache.maven.shared.invoker.*;

import java.io.File;
import java.nio.file.Path;
import java.util.Collections;
import java.util.function.Predicate;

/**
 * @author Bruno Salmon
 */
public final class MavenCaller {
    private final static boolean USE_MAVEN_INVOKER = true; // if false, just using shell invocation
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
        invokeMavenGoal(goal, new ProcessCall().setLogLineFilter(line -> line.startsWith("Downloading") || line.startsWith("[ERROR]")));
    }

    public static void invokeMavenGoal(String goal, ProcessCall processCall) {
        processCall.setCommand("mvn " + goal);
        if (!USE_MAVEN_INVOKER) {
            processCall.executeAndWait();
        } else {
            processCall.logCallCommand();
            InvocationRequest request = new DefaultInvocationRequest().setBaseDirectory(processCall.getWorkingDirectory());
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
                throw new RuntimeException(e);
            }
        }
    }
}
