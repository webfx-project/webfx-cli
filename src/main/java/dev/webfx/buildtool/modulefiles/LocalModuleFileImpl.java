package dev.webfx.buildtool.modulefiles;

import dev.webfx.buildtool.LocalProjectModule;

import java.nio.file.Path;

/**
 * @author Bruno Salmon
 */
abstract class LocalModuleFileImpl implements LocalModuleFile {

    private final LocalProjectModule module;
    private final Path moduleFilePath;


    public LocalModuleFileImpl(LocalProjectModule module, Path moduleFilePath) {
        this.module = module;
        this.moduleFilePath = moduleFilePath;
    }

    @Override
    public LocalProjectModule getModule() {
        return module;
    }


    @Override
    public Path getModuleFilePath() {
        return moduleFilePath;
    }

    @Override
    public void readFile() {
    }

}
