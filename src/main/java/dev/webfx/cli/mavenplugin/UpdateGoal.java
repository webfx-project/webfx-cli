package dev.webfx.cli.mavenplugin;

import dev.webfx.cli.commands.CommandWorkspace;
import dev.webfx.cli.commands.Update;
import dev.webfx.cli.core.Logger;

/**
 * @author Bruno Salmon
 */
public class UpdateGoal {

    public static int update(String projectDirectory) {
        try {
            CommandWorkspace workspace = new CommandWorkspace(projectDirectory);
            Update.execute(null, null, false, workspace);
            return 0;
        } catch (Exception e) {
            Logger.log("ERROR: " + e.getMessage());
            return -1;
        }
    }

}
