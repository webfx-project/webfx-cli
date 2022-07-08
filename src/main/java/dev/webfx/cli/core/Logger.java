package dev.webfx.cli.core;

import java.util.function.Consumer;

/**
 * @author Bruno Salmon
 */
public final class Logger {

    private static Consumer<Object> logConsumer = System.out::println;

    public static void setLogConsumer(Consumer<Object> logConsumer) {
        Logger.logConsumer = logConsumer;
    }

    public static void log(Object message) {
        logConsumer.accept(message);
    }

    static void warning(Object message) {
        log("WARNING: " + message);
    }

    static void verbose(Object message) {
        log("VERBOSE: " + message);
    }
}
