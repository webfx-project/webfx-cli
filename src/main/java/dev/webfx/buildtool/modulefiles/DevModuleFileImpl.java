package dev.webfx.buildtool.modulefiles;

import dev.webfx.buildtool.DevProjectModule;

import java.nio.file.Path;

/**
 * @author Bruno Salmon
 */
abstract class DevModuleFileImpl implements DevModuleFile {

    private final DevProjectModule module;
    private final Path moduleFilePath;


    public DevModuleFileImpl(DevProjectModule module, Path moduleFilePath) {
        this.module = module;
        this.moduleFilePath = moduleFilePath;
    }

    @Override
    public DevProjectModule getModule() {
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
