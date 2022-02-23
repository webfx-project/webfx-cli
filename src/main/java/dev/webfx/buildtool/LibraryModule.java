package dev.webfx.buildtool;

import dev.webfx.buildtool.util.xml.XmlUtil;
import dev.webfx.tools.util.reusablestream.ReusableStream;
import org.w3c.dom.Node;

/**
 * @author Bruno Salmon
 */
public class LibraryModule extends ModuleImpl {

    private final Node moduleNode;

    public LibraryModule(Node moduleNode) {
        super(getModuleName(moduleNode));
        this.moduleNode = moduleNode;
    }

    private static String getModuleName(Node moduleNode) {
        return XmlUtil.getAttributeValue(moduleNode, "name");
    }

    ReusableStream<String> getJavaPackages() {
        return XmlUtil.nodeListToReusableStream(XmlUtil.lookupNodeList(moduleNode, "packages//package"), Node::getTextContent);
    }

    @Override
    public String getGroupId() {
        if (groupId == null)
            groupId = getTagContent("groupId", true);
        return groupId;
    }

    @Override
    public String getArtifactId() {
        if (artifactId == null)
            artifactId = getTagContent("artifactId", false);
        return artifactId;
    }

    @Override
    public String getVersion() {
        if (version == null)
            version = getTagContent("version", true);
        return version;
    }

    @Override
    public String getType() {
        if (type == null)
            type = getTagContent("type", true);
        return type;
    }

    private String getTagContent(String tagName, boolean lookInGroupIfNull) {
        String tagContent = XmlUtil.lookupNodeTextContent(moduleNode, tagName);
        if (tagContent == null && lookInGroupIfNull)
            tagContent = XmlUtil.lookupNodeTextContent(moduleNode.getParentNode(), tagName);
        return tagContent;
    }

}
