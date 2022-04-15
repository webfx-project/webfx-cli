package dev.webfx.buildtool.modulefiles.abstr;

import dev.webfx.buildtool.ModuleDependency;
import dev.webfx.buildtool.Target;
import dev.webfx.buildtool.TargetTag;
import dev.webfx.buildtool.util.xml.XmlDocumentApi;
import dev.webfx.buildtool.util.xml.XmlUtil;
import dev.webfx.tools.util.reusablestream.ReusableStream;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

/**
 * @author Bruno Salmon
 */
public interface XmlModuleFile extends ModuleFile, XmlDocumentApi {

    default boolean updateDocument(Document document) {
        XmlUtil.removeChildren(document);
        document.appendChild(document.createElement("project"));
        return true;
    }

    default ReusableStream<ModuleDependency> lookupDependencies(String xPathExpression, ModuleDependency.Type type, String defaultScope) {
        return XmlUtil.nodeListToReusableStream(lookupNodeList(xPathExpression), node ->
                new ModuleDependency(
                        getModule(),
                        getProjectModule().getRootModule().searchModule(node.getTextContent()),
                        type,
                        XmlUtil.getBooleanAttributeValue(node, "optional"),
                        coalesce(XmlUtil.getAttributeValue(node, "scope"), defaultScope),
                        XmlUtil.getAttributeValue(node, "classifier"),
                        getTargetAttributeValue(node, "executable-target")
                ));
    }

    private String coalesce(String s1, String s2) {
        return s1 != null ? s1 : s2;
    }

    private Target getTargetAttributeValue(Node node, String attribute) {
        String stringValue = XmlUtil.getAttributeValue(node, attribute);
        return stringValue == null ? null : new Target(TargetTag.parseTags(stringValue));
    }

}
