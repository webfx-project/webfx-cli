package dev.webfx.buildtool.modulefiles;

import dev.webfx.buildtool.Module;
import dev.webfx.buildtool.ProjectModule;

import java.io.File;
import java.nio.file.Path;

/**
 * @author Bruno Salmon
 */
abstract class ModuleFile {

    private final Module module;

    ModuleFile(Module module) {
        this.module = module;
    }

    public Module getModule() {
        return module;
    }

    public ProjectModule getProjectModule() {
        return (ProjectModule) module;
    }

    Path resolveFromModuleHomeDirectory(String relativePath) {
        return getProjectModule().getHomeDirectory().resolve(relativePath);
    }

    abstract Path getModulePath();

    File getModuleFile() {
        return getModulePath().toFile();
    }

    abstract void readFile();

    abstract void writeFile();

}
