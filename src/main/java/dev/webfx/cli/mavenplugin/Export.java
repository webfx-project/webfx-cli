package dev.webfx.cli.mavenplugin;

import java.nio.file.Path;

/**
 * @author Bruno Salmon
 */
public class Export { // Temporary dummy Export class for backward compatibility

    // This method is called by the WebFX Maven plugin
    public static int export(String projectDirectory, String targetDirectory) {
        Path artifactPath = Path.of(targetDirectory, "webfx-artifact", "webfx.xml");
        return ExportGoal.export(projectDirectory, artifactPath.toString());
    }

}

