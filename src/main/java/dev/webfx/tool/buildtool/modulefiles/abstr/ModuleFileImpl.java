package dev.webfx.tool.buildtool.modulefiles.abstr;

import dev.webfx.tool.buildtool.Module;

/**
 * @author Bruno Salmon
 */
public abstract class ModuleFileImpl implements ModuleFile {

    private final Module module;

    ModuleFileImpl(Module module) {
        this.module = module;
    }

    @Override
    public Module getModule() {
        return module;
    }

}
