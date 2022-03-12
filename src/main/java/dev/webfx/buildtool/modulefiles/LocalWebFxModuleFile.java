package dev.webfx.buildtool.modulefiles;

import dev.webfx.buildtool.LocalProjectModule;

/**
 * @author Bruno Salmon
 */
public final class LocalWebFxModuleFile extends LocalXmlModuleFileImpl implements WebFxModuleFile {

    public LocalWebFxModuleFile(LocalProjectModule module) {
        super(module, module.getHomeDirectory().resolve("webfx.xml"), true);
    }

}
