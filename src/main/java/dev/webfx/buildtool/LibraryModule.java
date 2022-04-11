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
        groupId = getTagContent("groupId", true, 0, moduleNode);
        artifactId = getTagContent("artifactId", false, 1, moduleNode);
        version = getTagContent("version", true, 2, moduleNode);
        type = getTagContent("type", true, 3, moduleNode);
    }

    public Node getModuleNode() {
        return moduleNode;
    }

    private static String getModuleName(Node moduleNode) {
        return getTagContent("name", false, 1, moduleNode);
    }

    public boolean isMavenLibrary() {
        return XmlUtil.getBooleanAttributeValue(moduleNode, "m2");
    }

    public ReusableStream<String> getExportedPackages() {
        return XmlUtil.nodeListToTextContentReusableStream(XmlUtil.lookupNodeList(moduleNode, "exported-packages//package"));
    }

    private static String getTagContent(String tagName, boolean lookInGroupIfNull, int artifactTokenIndex, Node node) {
        String tagContent = getNodeOrAttributeTagContent(tagName, node);
        if (tagContent == null && artifactTokenIndex >= 0) {
            String artifact = getNodeOrAttributeTagContent("artifact", node);
            if (artifact != null) {
                String[] split = artifact.split(":");
                if (artifactTokenIndex < split.length)
                    return split[artifactTokenIndex];
            }
        }
        if (tagContent == null && lookInGroupIfNull)
            tagContent = getNodeOrAttributeTagContent(tagName, node.getParentNode());
        return tagContent;
    }

    private static String getNodeOrAttributeTagContent(String tagName, Node node) {
        String tagContent = XmlUtil.lookupNodeTextContent(node, tagName);
        if (tagContent == null)
            tagContent = XmlUtil.getAttributeValue(node, tagName);
        return tagContent;
    }

}
