package dev.webfx.buildtool.modulefiles;

import dev.webfx.buildtool.util.xml.XmlUtil;
import dev.webfx.tools.util.reusablestream.ReusableStream;
import org.w3c.dom.Node;

/**
 * @author Bruno Salmon
 */
public interface MavenPomModuleFile extends XmlModuleFile {

    default String getGroupId() {
        return lookupNodeTextContent("groupId");
    }

    default String getArtifactId() {
        return lookupNodeTextContent("artifactId");
    }

    default String getVersion() {
        return lookupNodeTextContent("version");
    }

    default String getParentGroupId() {
        return lookupNodeTextContent("parent/groupId");
    }

    default String getParentVersion() {
        return lookupNodeTextContent("parent/version");
    }

    default boolean isAggregate() {
        return lookupNode("modules") != null;
    }

    default ReusableStream<String> getChildrenModuleNames() {
        return XmlUtil.nodeListToReusableStream(lookupNodeList("modules//module"), node -> node.getTextContent());
    }

}
