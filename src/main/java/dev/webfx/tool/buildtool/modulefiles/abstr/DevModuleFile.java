package dev.webfx.tool.buildtool.modulefiles.abstr;

import dev.webfx.tool.buildtool.DevProjectModule;

/**
 * @author Bruno Salmon
 */
public interface DevModuleFile extends PathBasedModuleFile {

    @Override
    default DevProjectModule getProjectModule() {
        return (DevProjectModule) getModule();
    }

}
