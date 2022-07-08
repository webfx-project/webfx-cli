package dev.webfx.cli.modulefiles.abstr;

import dev.webfx.cli.core.DevProjectModule;

import java.nio.file.Path;

/**
 * @author Bruno Salmon
 */
public abstract class DevModuleFileImpl extends PathBasedModuleFileImpl implements DevModuleFile {

    public DevModuleFileImpl(DevProjectModule module, Path moduleFilePath) {
        super(module, moduleFilePath);
    }

    @Override
    public DevProjectModule getModule() {
        return (DevProjectModule) super.getModule();
    }

    @Override
    public void readFile() {
    }

}
