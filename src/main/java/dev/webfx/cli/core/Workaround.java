package dev.webfx.cli.core;

import dev.webfx.lib.reusablestream.ReusableStream;

/**
 * @author Bruno Salmon
 */
public final class Workaround {

    public static <T> void fixTerminalReusableStream(ReusableStream<T> reusableStream) {
        // Calling a terminal operation - here count() - otherwise the next stream may not provide a complete list
        // (although it's ended with a terminal operation) for any strange reason.
        // TODO Investigate why and provide a better fix
        reusableStream.count();
    }

}
