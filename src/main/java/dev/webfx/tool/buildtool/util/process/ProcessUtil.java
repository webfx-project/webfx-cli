package dev.webfx.tool.buildtool.util.process;

import dev.webfx.tool.buildtool.Logger;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * @author Bruno Salmon
 */
public class ProcessUtil {

    public static int executeAndConsume(String command, Consumer<String> outputConsumer) {
        Logger.log(command);
        long t0 = System.currentTimeMillis();
        try {
            Process process = new ProcessBuilder()
                    .command(command.split(" "))
                    .start();
            Executors.newSingleThreadExecutor().submit(new StreamGobbler(process.getInputStream(), outputConsumer));
            int exitCode = process.waitFor();
            Logger.log("Executed in " + (System.currentTimeMillis() - t0) + "ms");
            return exitCode;
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public static int execute(String command) {
        return execute(command, null);
    }

    public static int execute(String command, Predicate<String> lineFilter) {
        return executeAndConsume(command, line -> {
            if (lineFilter == null || lineFilter.test(line))
                Logger.log(line);
        });
    }

    public static String executeAndReturnLastMatchingLine(String command, Predicate<String> lineMatcher) {
        String[] lastMatchingLineHolder = new String[1];
        executeAndConsume(command, line -> {
            if (lineMatcher.test(line))
                lastMatchingLineHolder[0] = line;
        });
        return lastMatchingLineHolder[0];
    }

    public static String executeAndReturnLastOutputLine(String command) {
        return executeAndReturnLastMatchingLine(command, line -> true);
    }

}
