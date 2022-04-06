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

    default boolean updateDocument(Document document) {
        XmlUtil.removeChildren(document);
        document.appendChild(document.createElement("project"));
        return true;
    }

    Element getModuleElement();

    default Element getOrCreateModuleElement() {
        Element moduleElement = getModuleElement();
        return moduleElement != null ? moduleElement : getOrCreateDocument().getDocumentElement();
    }

    default String getXmlContent() {
        return XmlUtil.formatXmlText(getOrCreateDocument());
    }

    default NodeList lookupNodeList(String xpathExpression) {
        return XmlUtil.lookupNodeList(getModuleElement(), xpathExpression);
    }

    default Node lookupNode(String xpathExpression) {
        return XmlUtil.lookupNode(getModuleElement(), xpathExpression);
    }

    default Node lookupOrCreateNode(String xpath) {
        return XmlUtil.lookupOrCreateNode(getOrCreateModuleElement(), xpath);
    }

    default Node lookupOrCreateAndAppendNode(String xpath, boolean... linefeeds) {
        return XmlUtil.lookupOrCreateAndAppendNode(getOrCreateModuleElement(), xpath, linefeeds);
    }

    default Element createAndAppendElement(String xpath, boolean... linefeeds) {
        return XmlUtil.createAndAppendElement(getOrCreateModuleElement(), xpath, linefeeds);
    }

    default void appendTextNodeIfNotAlreadyExists(String xpath, String text, boolean... linefeeds) {
        if (lookupTextNode(xpath, text) == null)
            appendTextElement(xpath, text, linefeeds);
    }

    default Node lookupTextNode(String xpath, String text) {
        return XmlUtil.lookupTextNode(getModuleElement(), xpath, text);
    }

    default void appendIndentNode(Node node, boolean linefeed) {
        XmlUtil.appendIndentNode(node, getOrCreateModuleElement(), linefeed);
    }

    default Element appendTextElement(String xpath, String text, boolean... linefeeds) {
        return XmlUtil.appendTextElement(getOrCreateModuleElement(), xpath, text, linefeeds);
    }

    default Element prependTextElement(String xpath, String text, boolean... linefeeds) {
        return XmlUtil.prependTextElement(getOrCreateModuleElement(), xpath, text, linefeeds);
    }

    default Element createAndPrependElement(String xpath, boolean... linefeeds) {
        return XmlUtil.createAndPrependElement(getOrCreateModuleElement(), xpath, linefeeds);
    }

    default String lookupNodeTextContent(String xpathExpression) {
        return XmlUtil.lookupNodeTextContent(getModuleElement(), xpathExpression);
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
