package dev.webfx.cli.modulefiles.abstr;

import dev.webfx.cli.core.ModuleDependency;
import dev.webfx.cli.core.Target;
import dev.webfx.cli.core.TargetTag;
import dev.webfx.cli.util.xml.XmlDocumentApi;
import dev.webfx.cli.util.xml.XmlUtil;
import dev.webfx.lib.reusablestream.ReusableStream;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * @author Bruno Salmon
 */
public interface XmlModuleFile extends ModuleFile, XmlDocumentApi {

    default Element getXmlNode() {
        return getModuleElement();
    }

    default Element getModuleElement() {
        Document document = getDocument();
        return document == null ? null : document.getDocumentElement();
    }

    default boolean updateDocument(Document document) {
        XmlUtil.removeChildren(document);
        document.appendChild(document.createElement("project"));
        return true;
    }

    default ReusableStream<ModuleDependency> lookupDependencies(String xPathExpression, ModuleDependency.Type type, String defaultScope) {
        return XmlUtil.nodeListToReusableStream(lookupNodeList(xPathExpression), node ->
                new ModuleDependency(
                        getModule(),
                        getProjectModule().getRootModule().searchRegisteredModule(node.getTextContent()),
                        type,
                        XmlUtil.getBooleanAttributeValue(node, "optional"),
                        XmlUtil.getBooleanAttributeValue(node, "transitive"),
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
        return stringValue == null ? null : new Target(TargetTag.parseTags(stringValue, false));
    }

}
