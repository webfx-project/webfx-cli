package dev.webfx.buildtool.modulefiles;

import dev.webfx.buildtool.Module;
import dev.webfx.buildtool.ModuleDependency;
import dev.webfx.buildtool.Target;
import dev.webfx.buildtool.TargetTag;
import dev.webfx.buildtool.util.textfile.TextFileReaderWriter;
import dev.webfx.buildtool.util.xml.XmlUtil;
import dev.webfx.tools.util.reusablestream.ReusableStream;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

/**
 * @author Bruno Salmon
 */
abstract class XmlModuleFile extends ModuleFile {

    private Document document;
    private final boolean readFileIfExists;

    XmlModuleFile(Module module, boolean readFileIfExists) {
        super(module);
        this.readFileIfExists = readFileIfExists;
    }

    public Document getDocument() {
        if (document == null & readFileIfExists)
            readFile();
        return document;
    }

    Document getOrCreateDocument() {
        if (getDocument() == null)
            createDocument();
        return document;
    }

    void createDocument() {
        document = createInitialDocument();
        updateDocument(document);
    }

    Document createInitialDocument() {
        try {
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            return dBuilder.newDocument();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    void updateDocument(Document document) {
        clearDocument(document);
        document.appendChild(document.createElement("module"));
    }

    void clearDocument(Document document) {
        clearNodeChildren(document);
    }

    Element getDocumentElement() {
        return getDocument() == null ? null : document.getDocumentElement();
    }

    Element getOrCreateDocumentElement() {
        return getOrCreateDocument().getDocumentElement();
    }

    void clearNodeChildren(Node node) {
        Node firstChild;
        while ((firstChild = node.getFirstChild()) != null)
            node.removeChild(firstChild);
    }

    @Override
    public void readFile() {
        document = XmlUtil.parseXmlFile(getModuleFile());
    }

    public void updateAndWrite() {
        updateDocument(getOrCreateDocument());
        writeFile();
    }

    @Override
    public void writeFile() {
        TextFileReaderWriter.writeTextFileIfNewOrModified(getXmlContent(), getModuleFilePath());
    }

    public String getXmlContent() {
        return XmlUtil.formatXmlText(getOrCreateDocument());
    }

    NodeList lookupNodeList(String xpathExpression) {
        return XmlUtil.lookupNodeList(getDocument(), xpathExpression);
    }

    Node lookupNode(String xpathExpression) {
        return lookupNode(getDocument(), xpathExpression);
    }

    static Node lookupNode(Object parent, String xpathExpression) {
        return XmlUtil.lookupNode(parent, xpathExpression);
    }

    Node lookupOrCreateNode(String xpath) {
        return lookupOrCreateNode(getOrCreateDocumentElement(), xpath);
    }

    static Node lookupOrCreateNode(Node parent, String xpath) {
        Node node = lookupNode(parent, xpath);
        if (node == null)
            node = createNode(parent, xpath);
        return node;
    }

    Node createNode(String xpath) {
        return createNode(getOrCreateDocumentElement(), xpath);
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

    void appendTextNodeIfNotAlreadyExists(String xpath, String text) {
        if (lookupTextNode(xpath, text) == null)
            appendTextNode(xpath, text);
    }

    Node lookupTextNode(String xpath, String text) {
        return lookupTextNode(getDocumentElement(), xpath, text);
    }

    Node lookupTextNode(Object parent, String xpath, String text) {
        return lookupNode(parent, xpath + "[text() = '" + text + "']");
    }

    Node appendTextNode(String xpath, String text) {
        return appendTextNode(getOrCreateDocumentElement(), xpath, text);
    }

    Node appendTextNode(Node parentNode, String xpath, String text) {
        Node node = createNode(parentNode, xpath);
        node.setTextContent(text);
        return node;
    }

    String lookupNodeTextContent(String xpathExpression) {
        return XmlUtil.lookupNodeTextContent(getDocument(), xpathExpression);
    }

    ReusableStream<String> lookupNodeListTextContent(String xPathExpression) {
        return XmlUtil.nodeListToReusableStream(lookupNodeList(xPathExpression), Node::getTextContent);
    }

    ReusableStream<String> lookupNodeListAttribute(String xPathExpression, String attribute) {
        return XmlUtil.nodeListToReusableStream(lookupNodeList(xPathExpression), node -> XmlUtil.getAttributeValue(node, attribute));
    }

    ReusableStream<ModuleDependency> lookupDependencies(String xPathExpression, ModuleDependency.Type type, String defaultScope) {
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
