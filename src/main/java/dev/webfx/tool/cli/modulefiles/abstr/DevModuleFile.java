package dev.webfx.tool.cli.modulefiles.abstr;

import dev.webfx.tool.cli.core.DevProjectModule;

/**
 * @author Bruno Salmon
 */
public interface DevModuleFile extends PathBasedModuleFile {

    @Override
    default DevProjectModule getProjectModule() {
        return (DevProjectModule) getModule();
    }

}
