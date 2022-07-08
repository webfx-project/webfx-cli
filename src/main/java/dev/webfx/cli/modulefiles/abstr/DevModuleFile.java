package dev.webfx.cli.modulefiles.abstr;

import dev.webfx.cli.core.DevProjectModule;

/**
 * @author Bruno Salmon
 */
public interface DevModuleFile extends PathBasedModuleFile {

    @Override
    default DevProjectModule getProjectModule() {
        return (DevProjectModule) getModule();
    }

}
