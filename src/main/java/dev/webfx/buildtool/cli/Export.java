package dev.webfx.buildtool.cli;

import picocli.CommandLine;

/**
 * @author Bruno Salmon
 */
@CommandLine.Command(name = "export", description = "Export a module into a webfx-export.xml file")
public class Export extends CommonSubcommand implements Runnable {

    @Override
    public void run() {
        getWorkingProjectModule().getExportedWebFxModuleFile().updateAndWrite();
    }
}
