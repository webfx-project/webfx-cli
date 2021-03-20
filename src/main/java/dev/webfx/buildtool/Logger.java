package dev.webfx.buildtool;

import java.util.function.Consumer;

/**
 * @author Bruno Salmon
 */
public final class Logger {

    private static Consumer<String> logConsumer = System.out::println;

    public static void setLogConsumer(Consumer<String> logConsumer) {
        Logger.logConsumer = logConsumer;
    }

    public static void log(String message) {
        logConsumer.accept(message);
    }

}
