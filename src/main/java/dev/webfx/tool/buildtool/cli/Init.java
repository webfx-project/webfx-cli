package dev.webfx.tool.buildtool.cli;

import dev.webfx.tool.buildtool.DevRootModule;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.util.concurrent.Callable;

/**
 * @author Bruno Salmon
 */
@Command(name = "init", description = "Initialize a WebFX repository.")
final class Init extends CommonSubcommand implements Callable<Void> {

    @Option(names = {"-i", "--inline"}, description = "Inline the WebFX parent pom instead of referencing it.")
    private boolean inline;

    @Parameters(paramLabel = "groupId", description = "GroupId of the project artifact.")
    private String groupId;

    @Parameters(paramLabel = "artifactId", description = "ArtifactId of the project artifact.")
    private String artifactId;

    @Parameters(paramLabel = "version", description = "Version of the project artifact.")
    private String version;

    @Override
    public Void call() {
        Path projectDirectoryPath = getProjectDirectoryPath();
        setWorkspaceDirectoryPath(projectDirectoryPath.getParent());
        if (artifactId == null)
            artifactId = projectDirectoryPath.getFileName().toString();
        DevRootModule module = (DevRootModule) getModuleRegistry().getOrCreateDevProjectModule(projectDirectoryPath);
        module.setGroupId(groupId);
        module.setArtifactId(artifactId);
        module.setVersion(version);
        module.setInlineWebFxParent(inline);
        module.getWebFxModuleFile().writeFile();
        module.getMavenModuleFile().writeFile();
        module.getMavenModuleFile().updateAndWrite();
        return null;
    }

}
