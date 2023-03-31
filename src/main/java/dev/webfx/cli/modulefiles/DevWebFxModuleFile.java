package dev.webfx.cli.modulefiles;

import dev.webfx.cli.core.DevProjectModule;
import dev.webfx.cli.modulefiles.abstr.DevXmlModuleFileImpl;
import dev.webfx.cli.modulefiles.abstr.WebFxModuleFile;

/**
 * @author Bruno Salmon
 */
public final class DevWebFxModuleFile extends DevXmlModuleFileImpl implements WebFxModuleFile {

    public DevWebFxModuleFile(DevProjectModule module) {
        super(module, module.getHomeDirectory().resolve("webfx.xml"));
    }

}
