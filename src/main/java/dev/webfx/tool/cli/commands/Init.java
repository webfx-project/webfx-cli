package dev.webfx.tool.cli.commands;

import dev.webfx.tool.cli.core.DevRootModule;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.util.concurrent.Callable;

/**
 * @author Bruno Salmon
 */
@Command(name = "init", description = "Initialize a WebFX repository.")
public final class Init extends CommonSubcommand implements Callable<Void> {

    /*@Option(names = {"-i", "--inline"}, description = "Inline the WebFX parent pom instead of referencing it.")
    private boolean inline;*/

    @Parameters(paramLabel = "artifact", description = "The project artifact expressed as groupId:artifactId:version")
    private String artifact;

    @Override
    public Void call() {
        Path projectDirectoryPath = getProjectDirectoryPath();
        setWorkspaceDirectoryPath(projectDirectoryPath.getParent());
        String[] split = artifact.split(":");
        int i = 0, n = split.length;
        String groupId = split[i++];
        String artifactId = n >= 3 ? split[i++] : null;
        String version = split[i];
        if (artifactId == null)
            artifactId = projectDirectoryPath.getFileName().toString();
        DevRootModule module = (DevRootModule) getModuleRegistry().getOrCreateDevProjectModule(projectDirectoryPath);
        module.setGroupId(groupId);
        module.setArtifactId(artifactId);
        module.setVersion(version);
        //module.setInlineWebFxParent(inline);
        module.getWebFxModuleFile().writeFile();
        module.getMavenModuleFile().writeFile();
        module.getMavenModuleFile().updateAndWrite();
        return null;
    }

}
