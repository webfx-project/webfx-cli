package dev.webfx.tool.buildtool.modulefiles.abstr;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * @author Bruno Salmon
 */
public interface PathBasedModuleFile extends ModuleFile {

    Path getModuleFilePath();

    default File getModuleFile() {
        Path moduleFilePath = getModuleFilePath();
        return moduleFilePath == null ? null : moduleFilePath.toFile();
    }

    @Override
    default boolean fileExists() {
        Path moduleFilePath = getModuleFilePath();
        return moduleFilePath != null && Files.exists(moduleFilePath);
    }

    void readFile();

    void writeFile();

}
