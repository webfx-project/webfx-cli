package dev.webfx.buildtool.modulefiles.abstr;

import dev.webfx.buildtool.DevProjectModule;

/**
 * @author Bruno Salmon
 */
public interface DevModuleFile extends PathBasedModuleFile {

    @Override
    default DevProjectModule getProjectModule() {
        return (DevProjectModule) getModule();
    }

}
