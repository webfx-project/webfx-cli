package dev.webfx.buildtool.modulefiles.abstr;

import dev.webfx.buildtool.util.xml.XmlNodeApi;

/**
 * @author Bruno Salmon
 */
public interface XmlGavApi extends XmlNodeApi, GavApi {

    default String getGroupId() {
        return lookupGroupId();
    }

    default String getArtifactId() {
        return lookupArtifactId();
    }

    default String getVersion() {
        return lookupVersion();
    }

    default String lookupGroupId() {
        return XmlGavUtil.lookupGroupId(getXmlNode());
    }

    default String lookupArtifactId() {
        return XmlGavUtil.lookupArtifactId(getXmlNode());
    }

    default String lookupVersion() {
        return XmlGavUtil.lookupVersion(getXmlNode());
    }

    default String lookupType() {
        return XmlGavUtil.lookupType(getXmlNode());
    }

    default String lookupParentGroupId() {
        return XmlGavUtil.lookupParentGroupId(getXmlNode());
    }

    default String lookupParentVersion() {
        return XmlGavUtil.lookupParentVersion(getXmlNode());
    }

    default String lookupParentName() {
        return XmlGavUtil.lookupParentName(getXmlNode());
    }

}
