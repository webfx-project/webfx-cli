package dev.webfx.buildtool.cli;

import dev.webfx.buildtool.Logger;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * @author Bruno Salmon
 */
@Command(name = "create", description = "Create WebFX module(s).")
final class Create extends CommonSubcommand implements Runnable {

    @Option(names = {"-g", "--gluon"}, description = "Also create the gluon module.")
    private boolean gluon;

    @Option(names = {"-p", "--prefix"}, description = "Prefix of the modules that will be created.")
    private String prefix;

    @Parameters(paramLabel="<class>", description = "Fully qualified JavaFX Application class name.")
    private String javaFxApplication;

    @Option(names = {"-w", "--helloWorld"}, description = "Use hello world code template.")
    private boolean helloWorld;

    @Override
    public void run() {
        Logger.log("skipGluon = " + gluon);
        Logger.log("javaFxApplication = " + javaFxApplication);
    }
}
