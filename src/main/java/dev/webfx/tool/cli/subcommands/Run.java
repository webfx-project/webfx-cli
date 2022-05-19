package dev.webfx.tool.cli.subcommands;

import dev.webfx.tool.cli.core.DevProjectModule;
import dev.webfx.tool.cli.core.Platform;
import dev.webfx.tool.cli.core.RunException;
import dev.webfx.tool.cli.util.process.ProcessUtil;
import picocli.CommandLine.Command;

import java.io.IOException;

/**
 * @author Bruno Salmon
 */
@Command(name = "run", description = "Run a WebFX application.")
public final class Run extends CommonSubcommand implements Runnable {

    /*@Option(names= {"-b", "--build"}, description = "Build before running")
    boolean build;

    @Option(names= {"-p", "--port"}, description = "Port of the web server.")
    int port;*/

    @Override
    public void run() {
        DevProjectModule module = getWorkingDevProjectModule();
        if (!module.isExecutable())
            throw new RunException(module.getName() + " is not an executable module. Please specify a openjfx or gwt executable application module to run.");
        if (module.isExecutable(Platform.JRE))
            ProcessUtil.execute("java -jar " + module.getHomeDirectory().resolve("target/" +  module.getName() + "-" + module.getVersion() + "-fat.jar"));
        else if (module.isExecutable(Platform.GWT)) {
            try {
                java.awt.Desktop.getDesktop().browse(module.getHomeDirectory().resolve("target/" + module.getName() + "-" + module.getVersion() + "/" + module.getName().replace('-', '_') + "/index.html").toUri());
            } catch (IOException e) {
                throw new RunException(e.getMessage());
            }
        }
    }
}
