package dev.webfx.buildtool.modulefiles;

import dev.webfx.buildtool.M2ProjectModule;
import dev.webfx.buildtool.modulefiles.abstr.PathBasedXmlModuleFileImpl;
import dev.webfx.buildtool.modulefiles.abstr.WebFxModuleFile;

/**
 * @author Bruno Salmon
 */
public final class M2WebFxModuleFile extends PathBasedXmlModuleFileImpl implements WebFxModuleFile {

    public M2WebFxModuleFile(M2ProjectModule module) {
        super(module, module.getM2ArtifactSubPath("-webfx.xml"));
    }

    @Override
    public boolean isAggregate() {
        return false;
    }
}
