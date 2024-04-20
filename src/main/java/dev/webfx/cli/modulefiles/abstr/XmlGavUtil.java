package dev.webfx.cli.modulefiles.abstr;

import dev.webfx.cli.util.xml.XmlUtil;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * @author Bruno Salmon
 */
public class XmlGavUtil {

    public static String lookupGroupId(Node node) {
        return lookupGavTagContent("groupId", true, 0, node);
    }

    public static String lookupArtifactId(Node node) {
        return lookupGavTagContent("artifactId", false, 1, node);
    }

    public static String lookupVersion(Node node) {
        return lookupGavTagContent("version", true, 2, node);
    }

    public static String lookupType(Node node) {
        return lookupGavTagContent("type", true, 3, node);
    }

    public static String lookupName(Node node) {
        String name = XmlUtil.getNodeOrAttributeTagContent(node, "name");
        return name != null ? name : lookupArtifactId(node);
    }

    public static String lookupParentGroupId(Node node) {
        return XmlGavUtil.lookupGavTagContent("groupId", false, 0, lookupParentNode(node));
    }

    public static String lookupParentVersion(Node node) {
        return XmlGavUtil.lookupGavTagContent("version", false, 0, lookupParentNode(node));
    }

    public static String lookupParentName(Node node) {
        return lookupName(lookupParentNode(node));
    }

    private static Node lookupParentNode(Node node) {
        return XmlUtil.lookupNode(node, "parent[1]");
    }

    private static String lookupGavTagContent(String tagName, boolean lookInGroupIfNull, int artifactTokenIndex, Node node) {
        String tagContent = XmlUtil.getNodeOrAttributeTagContent(node, tagName);
        if (tagContent == null && artifactTokenIndex >= 0) {
            String artifact = XmlUtil.getNodeOrAttributeTagContent(node, "artifact");
            if (artifact != null) {
                String[] split = artifact.split(":");
                if (artifactTokenIndex < split.length)
                    return split[artifactTokenIndex];
            }
        }
        if (tagContent == null && lookInGroupIfNull && node != null) {
            Node parentNode = node.getParentNode();
            if (parentNode instanceof Element && "group".equals(((Element) parentNode).getTagName()))
                tagContent = lookupGavTagContent(tagName, false, artifactTokenIndex, parentNode);
        }
        return tagContent;
    }
}
