package dev.webfx.buildtool.modulefiles;

import dev.webfx.buildtool.M2ProjectModule;
import dev.webfx.buildtool.util.xml.XmlUtil;

/**
 * @author Bruno Salmon
 */
public final class M2WebFxModuleFile extends XmlModuleFileImpl implements WebFxModuleFile {

    public M2WebFxModuleFile(M2ProjectModule module) {
        super(module, XmlUtil.parseXmlFile(module.getM2ArtifactSubPath("-webfx.xml").toFile()));
    }

    @Override
    public boolean isAggregate() {
        return false;
    }
}
