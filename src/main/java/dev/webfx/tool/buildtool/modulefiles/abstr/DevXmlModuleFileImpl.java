package dev.webfx.tool.buildtool.modulefiles.abstr;

import dev.webfx.tool.buildtool.Module;

import java.nio.file.Path;

/**
 * @author Bruno Salmon
 */
public class DevXmlModuleFileImpl extends PathBasedXmlModuleFileImpl implements DevXmlModuleFile {

    public DevXmlModuleFileImpl(Module module, Path moduleFilePath) {
        super(module, moduleFilePath);
    }

    public DevXmlModuleFileImpl(Module module, Path moduleFilePath, boolean readFileIfExists) {
        super(module, moduleFilePath, readFileIfExists);
    }
}
