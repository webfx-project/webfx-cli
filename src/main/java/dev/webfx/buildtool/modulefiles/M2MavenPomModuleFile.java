package dev.webfx.buildtool.modulefiles;

import dev.webfx.buildtool.M2ProjectModule;
import dev.webfx.buildtool.util.xml.XmlUtil;

/**
 * @author Bruno Salmon
 */
public class M2MavenPomModuleFile extends XmlModuleFileImpl implements MavenPomModuleFile {

    public M2MavenPomModuleFile(M2ProjectModule module) {
        super(module, XmlUtil.parseXmlFile(module.getM2ArtifactSubPath(".pom").toFile()));
    }
}
