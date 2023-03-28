package dev.webfx.cli.mavenplugin;

import dev.webfx.cli.commands.CommandWorkspace;
import dev.webfx.cli.commands.Init;
import dev.webfx.cli.core.Logger;

/**
 * @author Bruno Salmon
 */
public class InitGoal {

    // This method is called by the WebFX Maven plugin
    public static int init(String projectDirectory, String artifact) {
        try {
            CommandWorkspace workspace = new CommandWorkspace(projectDirectory);
            Init.execute(artifact, workspace);
            return 0;
        } catch (Exception e) {
            Logger.log("ERROR: " + e.getMessage());
            return -1;
        }
    }

}
