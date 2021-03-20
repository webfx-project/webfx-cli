package dev.webfx.buildtool.cli;

import dev.webfx.buildtool.Logger;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

/**
 * @author Bruno Salmon
 */
@Command(name = "rename", description = "Rename module(s).")
final class Rename extends CommonSubcommand implements Runnable {

    @Parameters(description = "Original name of the module.")
    private String name;

    @Parameters(paramLabel = "to")
    private Keywords.ToKeyword to;

    @Parameters(description = "New name of the module.")
    private String newName;

    @Override
    public void run() {
        Logger.log("Renaming " + name + " to " + newName);
    }
}
