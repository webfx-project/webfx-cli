package dev.webfx.buildtool.modulefiles;

import dev.webfx.buildtool.DevProjectModule;

/**
 * @author Bruno Salmon
 */
public final class DevWebFxModuleFile extends DevXmlModuleFileImpl implements WebFxModuleFile {

    public DevWebFxModuleFile(DevProjectModule module) {
        super(module, module.getHomeDirectory().resolve("webfx.xml"), true);
    }

}
