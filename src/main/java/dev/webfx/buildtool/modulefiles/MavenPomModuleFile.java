package dev.webfx.buildtool.modulefiles;

import dev.webfx.buildtool.util.xml.XmlUtil;
import dev.webfx.tools.util.reusablestream.ReusableStream;
import org.w3c.dom.Node;

/**
 * @author Bruno Salmon
 */
public interface MavenPomModuleFile extends XmlModuleFile {

    default String getGroupId() {
        Node node = lookupNode("groupId");
        return node == null ? null : node.getTextContent();
    }

    default String getArtifactId() {
        Node node = lookupNode("artifactId");
        return node == null ? null : node.getTextContent();
    }

    default String getVersion() {
        Node node = lookupNode("version");
        return node == null ? null : node.getTextContent();
    }

    default String getParentGroupId() {
        Node node = lookupNode("parent/groupId");
        return node == null ? null : node.getTextContent();
    }

    default String getParentVersion() {
        Node node = lookupNode("parent/version");
        return node == null ? null : node.getTextContent();
    }

    default boolean isAggregate() {
        return lookupNode("modules") != null;
    }

    default ReusableStream<String> getChildrenModuleNames() {
        return XmlUtil.nodeListToReusableStream(lookupNodeList("modules//module"), node -> node.getTextContent());
    }

}
