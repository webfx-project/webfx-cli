package dev.webfx.buildtool.modulefiles;

import dev.webfx.buildtool.ModuleDependency;
import dev.webfx.buildtool.Target;
import dev.webfx.buildtool.TargetTag;
import dev.webfx.buildtool.util.xml.XmlUtil;
import dev.webfx.tools.util.reusablestream.ReusableStream;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * @author Bruno Salmon
 */
public interface XmlModuleFile extends ModuleFile {

    Document getDocument();

    void setDocument(Document document);

    default Document getOrCreateDocument() {
        if (getDocument() == null)
            createDocument();
        return getDocument();
    }

    default void createDocument() {
        Document document = createInitialDocument();
        setDocument(document);
        updateDocument(document);
    }

    default Document createInitialDocument() {
        return XmlUtil.newDocument();
    }

    default void updateDocument(Document document) {
        clearDocument(document);
        document.appendChild(document.createElement("module"));
    }

    default void clearDocument(Document document) {
        clearNodeChildren(document);
    }

    Element getModuleElement();

    default Element getOrCreateModuleElement() {
        Element moduleElement = getModuleElement();
        return moduleElement != null ? moduleElement : getOrCreateDocument().getDocumentElement();
    }

    default void clearNodeChildren(Node node) {
        Node firstChild;
        while ((firstChild = node.getFirstChild()) != null)
            node.removeChild(firstChild);
    }

    default String getXmlContent() {
        return XmlUtil.formatXmlText(getOrCreateDocument());
    }

    default NodeList lookupNodeList(String xpathExpression) {
        return XmlUtil.lookupNodeList(getModuleElement(), xpathExpression);
    }

    default Node lookupNode(String xpathExpression) {
        return lookupNode(getModuleElement(), xpathExpression);
    }

    static Node lookupNode(Object parent, String xpathExpression) {
        return XmlUtil.lookupNode(parent, xpathExpression);
    }

    default Node lookupOrCreateNode(String xpath) {
        return lookupOrCreateNode(getOrCreateModuleElement(), xpath);
    }

    static Node lookupOrCreateNode(Node parent, String xpath) {
        Node node = lookupNode(parent, xpath);
        if (node == null)
            node = createNode(parent, xpath);
        return node;
    }

    default Node createNode(String xpath) {
        return createNode(getOrCreateModuleElement(), xpath);
    }

    static Node createNode(Node parentElement, String xpath) {
        Document document = parentElement.getOwnerDocument();
        int p = xpath.lastIndexOf('/');
        Node parentNode = p <= 1 ? parentElement : lookupOrCreateNode(parentElement, xpath.substring(0, p));
        Node node = document.createElement(xpath.substring(p + 1));
        XmlUtil.appendIndentNode(node, parentNode);
/*
        if (parentNode == parentElement)
            parentNode.appendChild(document.createTextNode("\n"));
*/
        return node;
    }

    default void appendTextNodeIfNotAlreadyExists(String xpath, String text) {
        if (lookupTextNode(xpath, text) == null)
            appendTextNode(xpath, text);
    }

    default Node lookupTextNode(String xpath, String text) {
        return lookupTextNode(getModuleElement(), xpath, text);
    }

    default Node lookupTextNode(Object parent, String xpath, String text) {
        return lookupNode(parent, xpath + "[text() = '" + text + "']");
    }

    default Node appendTextNode(String xpath, String text) {
        return appendTextNode(getOrCreateModuleElement(), xpath, text);
    }

    default Node appendTextNode(Node parentNode, String xpath, String text) {
        Node node = createNode(parentNode, xpath);
        node.setTextContent(text);
        return node;
    }

    default String lookupNodeTextContent(String xpathExpression) {
        return XmlUtil.lookupNodeTextContent(getDocument(), xpathExpression);
    }

    default ReusableStream<String> lookupNodeListTextContent(String xPathExpression) {
        return XmlUtil.nodeListToReusableStream(lookupNodeList(xPathExpression), Node::getTextContent);
    }

    default ReusableStream<String> lookupNodeListAttribute(String xPathExpression, String attribute) {
        return XmlUtil.nodeListToReusableStream(lookupNodeList(xPathExpression), node -> XmlUtil.getAttributeValue(node, attribute));
    }

    default ReusableStream<ModuleDependency> lookupDependencies(String xPathExpression, ModuleDependency.Type type, String defaultScope) {
        return XmlUtil.nodeListToReusableStream(lookupNodeList(xPathExpression), node ->
                new ModuleDependency(
                        getModule(),
                        getProjectModule().getRootModule().findModule(node.getTextContent()),
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

    private Target getTargetAttributeValue(Node node, String name) {
        String stringValue = XmlUtil.getAttributeValue(node, name);
        return stringValue == null ? null : new Target(TargetTag.parseTags(stringValue));
    }

}
