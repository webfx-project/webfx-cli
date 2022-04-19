package dev.webfx.buildtool.util.process;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * @author Bruno Salmon
 */
public class ProcessUtil {

    public static int executeAndConsume(String command, Consumer<String> outputConsumer) {
        System.out.println(command);
        try {
            Process process = new ProcessBuilder()
                    .command(command.split(" "))
                    .start();
            Executors.newSingleThreadExecutor().submit(new StreamGobbler(process.getInputStream(), outputConsumer));
            return process.waitFor();
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
                System.out.println(line);
        });
    }

    public static String executeAndReturnLastOutputLine(String command) {
        String[] lineHolder = new String[1];
        executeAndConsume(command, line -> lineHolder[0] = line);
        return lineHolder[0];
    }

}
