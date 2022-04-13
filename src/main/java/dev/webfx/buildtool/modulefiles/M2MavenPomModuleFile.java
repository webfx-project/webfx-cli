package dev.webfx.buildtool.modulefiles;

import dev.webfx.buildtool.M2ProjectModule;
import dev.webfx.buildtool.modulefiles.abstr.MavenPomModuleFile;
import dev.webfx.buildtool.modulefiles.abstr.PathBasedXmlModuleFileImpl;

/**
 * @author Bruno Salmon
 */
public final class M2MavenPomModuleFile extends PathBasedXmlModuleFileImpl implements MavenPomModuleFile {

    public M2MavenPomModuleFile(M2ProjectModule module) {
        super(module, module.getM2ArtifactSubPath(".pom"));
    }

}
