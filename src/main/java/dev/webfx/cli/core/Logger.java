package dev.webfx.cli.core;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * @author Bruno Salmon
 */
public final class Logger {

    private static Consumer<Object> logConsumer = System.out::println;
    private static Function<Object, Object> logTransformer = msg -> msg;

    public static void setLogConsumer(Consumer<Object> logConsumer) {
        Logger.logConsumer = logConsumer;
    }

    public static void setLogTransformer(Function<Object, Object> logTransformer) {
        Logger.logTransformer = logTransformer;
    }

    public static void log(Object message) {
        logConsumer.accept(logTransformer.apply(message));
    }

    public static void warning(Object message) {
        log("WARNING: " + message);
    }

    public static void verbose(Object message) {
        log("VERBOSE: " + message);
    }
}
