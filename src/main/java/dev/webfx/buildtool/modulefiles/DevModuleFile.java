package dev.webfx.buildtool.modulefiles;

import dev.webfx.buildtool.DevProjectModule;

import java.io.File;
import java.nio.file.Path;

/**
 * @author Bruno Salmon
 */
interface DevModuleFile extends ModuleFile {

    @Override
    default DevProjectModule getProjectModule() {
        return (DevProjectModule) getModule();
    }

    Path getModuleFilePath();

    default File getModuleFile() {
        return getModuleFilePath().toFile();
    }

    void readFile();

    void writeFile();
}
