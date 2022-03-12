package dev.webfx.buildtool.modulefiles;

import dev.webfx.buildtool.LocalProjectModule;

import java.io.File;
import java.nio.file.Path;

/**
 * @author Bruno Salmon
 */
interface LocalModuleFile extends ModuleFile {

    @Override
    default LocalProjectModule getProjectModule() {
        return (LocalProjectModule) getModule();
    }

    Path getModuleFilePath();

    default File getModuleFile() {
        return getModuleFilePath().toFile();
    }

    void readFile();

    void writeFile();
}
