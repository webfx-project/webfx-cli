package dev.webfx.cli.modulefiles.abstr;

import dev.webfx.cli.util.xml.XmlUtil;
import org.dom4j.Element;

/**
 * @author Bruno Salmon
 */
public class XmlGavUtil {

    public static String lookupGroupId(Element node) {
        return lookupGavTagContent("groupId", true, 0, node);
    }

    public static String lookupArtifactId(Element node) {
        return lookupGavTagContent("artifactId", false, 1, node);
    }

    public static String lookupVersion(Element node) {
        return lookupGavTagContent("version", true, 2, node);
    }

    public static String lookupType(Element node) {
        return lookupGavTagContent("type", true, 3, node);
    }

    public static String lookupName(Element node) {
        String name = XmlUtil.getNodeOrAttributeTagContent(node, "name");
        return name != null ? name : lookupArtifactId(node);
    }

    public static String lookupParentGroupId(Element node) {
        return XmlGavUtil.lookupGavTagContent("groupId", false, 0, lookupParentNode(node));
    }

    public static String lookupParentVersion(Element node) {
        return XmlGavUtil.lookupGavTagContent("version", false, 0, lookupParentNode(node));
    }

    public static String lookupParentName(Element node) {
        return lookupName(lookupParentNode(node));
    }

    private static Element lookupParentNode(Element node) {
        return XmlUtil.lookupElement(node, "parent[1]");
    }

    private static String lookupGavTagContent(String tagName, boolean lookInGroupIfNull, int artifactTokenIndex, Element node) {
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
            Element parentNode = node.getParent();
            if (parentNode != null && "group".equals(parentNode.getName()))
                tagContent = lookupGavTagContent(tagName, false, artifactTokenIndex, parentNode);
        }
        return tagContent;
    }
}
