package dev.webfx.buildtool.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * @author Bruno Salmon
 */
@Command(name = "run", description = "Run a WebFX application.")
final class Run extends CommonSubcommand implements Runnable {

    @Option(names= {"-b", "--build"}, description = "Build before running")
    boolean build;

    @Option(names= {"-c", "--class"}, description = "JavaFX application class to run.")
    String javaFxClass;

    @Option(names= {"-p", "--port"}, description = "Port of the web server.")
    int port;

    @Option(names= {"-j", "--openjfx"}, description = "Run the OpenJFX version.")
    boolean openJfx;

    @Override
    public void run() {
    }
}
