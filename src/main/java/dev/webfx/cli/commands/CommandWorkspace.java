package dev.webfx.cli.commands;

import dev.webfx.cli.core.*;
import dev.webfx.cli.core.Module;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * @author Bruno Salmon
 */
public final class CommandWorkspace {

    private final String projectDirectory;
    private final String moduleName;

    private ModuleRegistry moduleRegistry;
    private Path projectDirectoryPath;
    private Path topRootDirectoryPath;
    private Path workspaceDirectoryPath;
    private Module workingModule;

    private DevProjectModule topRootModule;

    public CommandWorkspace(String projectDirectory) {
        this(projectDirectory, null);
    }

    public CommandWorkspace(String projectDirectory, String moduleName) {
        this.projectDirectory = projectDirectory;
        this.moduleName = moduleName;
    }

    public CommandWorkspace(CommandWorkspace workspace) {
        this.projectDirectory = workspace.projectDirectory;
        this.moduleName = workspace.moduleName;
    }

    public Path getProjectDirectoryPath() {
        if (projectDirectoryPath == null)
            projectDirectoryPath = Path.of(projectDirectory).toAbsolutePath();
        return projectDirectoryPath;
    }

    public Path getTopRootDirectoryPath() {
        if (topRootDirectoryPath == null)
            topRootDirectoryPath = getTopRootDirectory(getProjectDirectoryPath());
        return topRootDirectoryPath;
    }

    public Path getWorkspaceDirectoryPath() {
        if (workspaceDirectoryPath == null)
            workspaceDirectoryPath = getTopRootDirectoryPath() == null ? getProjectDirectoryPath() : getTopRootDirectoryPath().getParent();
        return workspaceDirectoryPath;
    }

    public void setWorkspaceDirectoryPath(Path workspaceDirectoryPath) {
        this.workspaceDirectoryPath = workspaceDirectoryPath;
    }

    ModuleRegistry getModuleRegistry() {
        if (moduleRegistry == null)
            moduleRegistry = new ModuleRegistry(getWorkspaceDirectoryPath());
        return moduleRegistry;
    }

    private static Path getTopRootDirectory(Path projectDirectory) {
        if (!hasProjectFile(projectDirectory))
            return null;
        Path topRootDirectory = projectDirectory;
        while (true) {
            Path parent = topRootDirectory.getParent();
            if (!hasProjectFile(parent))
                return topRootDirectory;
            topRootDirectory = parent;
        }
    }

    private static boolean hasProjectFile(Path projectDirectory) {
        return Files.exists(projectDirectory.resolve("webfx.xml")) || Files.exists(projectDirectory.resolve("pom.xml"));
    }

    private Module getWorkingModule() {
        if (workingModule == null) {
            if (getTopRootDirectoryPath() == null)
                throw new CliException("Not a WebFX repository (or any of the parent directories): no webfx.xml");
            if (moduleName == null)
                return getModuleRegistry().getOrCreateDevProjectModule(projectDirectoryPath);
            DevProjectModule topProjectModule = getTopRootModule();
            if (moduleName.equals("top") || moduleName.equals(topProjectModule.getName()) )
                return topProjectModule;
            workingModule = topProjectModule.searchRegisteredModule(moduleName, false);
        }
        return workingModule;
    }

    public DevProjectModule getWorkingDevProjectModule() {
        Module workingModule = getWorkingModule();
        if (workingModule instanceof DevProjectModule)
            return (DevProjectModule) workingModule;
        throw new CliException(workingModule.getName() + " is not a project module.");
    }

    DevProjectModule getTopRootModule() {
        if (topRootModule == null)
            topRootModule = getModuleRegistry().getOrCreateDevProjectModule(topRootDirectoryPath);
        return topRootModule;
    }

}
