package dev.webfx.buildtool.modulefiles.abstr;

import dev.webfx.buildtool.Module;

import java.nio.file.Path;

/**
 * @author Bruno Salmon
 */
public abstract class PathBasedModuleFileImpl extends ModuleFileImpl implements PathBasedModuleFile {

    private final Path moduleFilePath;

    PathBasedModuleFileImpl(Module module, Path moduleFilePath) {
        super(module);
        this.moduleFilePath = moduleFilePath;
    }

    @Override
    public Path getModuleFilePath() {
        return moduleFilePath;
    }
}
