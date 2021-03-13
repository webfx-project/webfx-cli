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

    public String getArtifactId() {
        return XmlUtil.lookupNodeTextContent(moduleNode, "artifactId");
    }

    public String getGroupId() {
        return XmlUtil.lookupNodeTextContent(moduleNode, "groupId");
    }

    public String getVersion() {
        return XmlUtil.lookupNodeTextContent(moduleNode, "version");
    }

}
