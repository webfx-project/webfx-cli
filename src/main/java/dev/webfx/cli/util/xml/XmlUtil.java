package dev.webfx.cli.util.xml;

import org.w3c.dom.*;
import org.xml.sax.InputSource;
import dev.webfx.lib.reusablestream.ReusableStream;

import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.*;
import java.io.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * @author Bruno Salmon
 */
public final class XmlUtil {

    public static Document newDocument() {
        try {
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            return dBuilder.newDocument();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static Document parseXmlFile(File xmlFile) {
        //System.out.println("Parsing file " + xmlFile);
        return parseXmlSource(new InputSource(xmlFile.toURI().toASCIIString()));
    }

    public static Document parseXmlString(String xmlString) {
        InputSource is = new InputSource();
        is.setCharacterStream(new StringReader(xmlString));
        return parseXmlSource(is);
    }

    public static Document parseXmlSource(InputSource is) {
        try {
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            try {
                return dBuilder.parse(is);
            } catch (FileNotFoundException ie) {
                return null;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static String formatXmlText(Node node) {
        return formatText(node, "xml");
    }

    public static String formatHtmlText(Node node) {
        return formatText(node, "html");
    }

    private static String formatText(Node node, String method) {
        try {
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            if (node instanceof Document)
                ((Document) node).setXmlStandalone(true);
            else
                transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            transformer.setOutputProperty(OutputKeys.METHOD, method);
            //transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
            //node.normalize();
            DOMSource source = new DOMSource(node);
            StringWriter sw = new StringWriter();
            StreamResult result = new StreamResult(sw);
            transformer.transform(source, result);
            String xmlText = sw.toString()
                    .replace("?><", "?>\n<") // Adding a linefeed if missing after xml declaration
                    .replace("\" xmlns:xsi=\"", "\"\n         xmlns:xsi=\"") // Adding a linefeed before xmlns:xsi
                    .replace("\" xsi:schemaLocation=\"", "\"\n         xsi:schemaLocation=\""); // Adding a linefeed before xsi:schemaLocation
            if (xmlText.contains("?>\n<!--")) // xml declaration followed by a comment
                xmlText = xmlText.replace("--><", "-->\n<"); // Adding linefeed after the end of comment if missing

            return xmlText;
        } catch (TransformerException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static NodeList lookupNodeList(Object item, String xpathExpression) {
        return lookup(item, xpathExpression, XPathConstants.NODESET);
    }

    public static Node lookupNode(Object item, String xpathExpression) {
        return lookup(item, xpathExpression, XPathConstants.NODE);
    }

    public static String lookupNodeTextContent(Object item, String xpathExpression) {
        Node node = lookupNode(item, xpathExpression);
        return node == null ? null : node.getTextContent();
    }

    public static Node lookupNodeWithTextContent(Object item, String xpath, String text) {
        if (text.indexOf('\'') == -1) // General case when the text doesn't contain single quotes
            return lookupNode(item, xpath + "[text() = '" + text + "']");
        // When the text contains single quotes, we need to escape them
        return lookupNode(item, xpath + "[text() = concat('" + text.replace("'", "',\"'\",'") + "', '')]");
    }

    public static Element lookupElementWithAttributeValue(Object item, String xpath, String attribute, String value) {
        return (Element) lookupNode(item, xpath + "[@" + attribute + "='" + value + "']");
    }

    private final static XPathFactory X_PATH_FACTORY = XPathFactory.newInstance();
    private final static XPath X_PATH = X_PATH_FACTORY.newXPath();
    private final static Map<String, XPathExpression> X_PATH_EXPRESSION_CACHE = new HashMap<>();

    private static <T> T lookup(Object item, String xpathExpression, QName returnType) {
        if (item == null)
            return null;
        try {
            XPathExpression expression = X_PATH_EXPRESSION_CACHE.get(xpathExpression);
            if (expression == null) {
                expression = X_PATH.compile(xpathExpression);
                X_PATH_EXPRESSION_CACHE.put(xpathExpression, expression);
            }
            return (T) expression.evaluate(item, returnType);
        } catch (XPathExpressionException e) {
            throw new RuntimeException(e);
        }
    }

    public static ReusableStream<Node> nodeListToNodeReusableStream(NodeList nodeList) {
        return nodeListToReusableStream(nodeList, node -> node);
    }

    public static ReusableStream<String> nodeListToTextContentReusableStream(NodeList nodeList) {
        return nodeListToReusableStream(nodeList, Node::getTextContent);
    }

    public static ReusableStream<String> nodeListToAttributeValueReusableStream(NodeList nodeList, String attribute) {
        return nodeListToReusableStream(nodeList, n -> XmlUtil.getAttributeValue(n, attribute));
    }

    public static <T> ReusableStream<T> nodeListToReusableStream(NodeList nodeList, Function<Node, ? extends T> transformer) {
        if (nodeList == null)
            return ReusableStream.empty();
        return ReusableStream.create(() -> new Spliterators.AbstractSpliterator<>(nodeList.getLength(), Spliterator.SIZED) {
            private int index = 0;
            @Override
            public boolean tryAdvance(Consumer<? super T> action) {
                if (index >= nodeList.getLength())
                    return false;
                Node moduleNode = nodeList.item(index++);
                action.accept(transformer.apply(moduleNode));
                return true;
            }
        });
    }

    public static List<String> nodeListToTextContentList(NodeList nodeList) {
        return nodeListToList(nodeList, Node::getTextContent);
    }

    public static List<String> nodeListToAttributeValueList(NodeList nodeList, String attribute) {
        return nodeListToList(nodeList, n -> XmlUtil.getAttributeValue(n, attribute));
    }

    public static <T> List<T> nodeListToList(NodeList nodeList, Function<Node, ? extends T> transformer) {
        if (nodeList == null)
            return Collections.emptyList();
        int n = nodeList.getLength();
        List<T> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++)
            list.add(transformer.apply(nodeList.item(i)));
        return list;
    }

    public static String getAttributeValue(Node node, String name) {
        NamedNodeMap attributes = node == null ? null : node.getAttributes();
        Node namedItem = attributes == null ? null : attributes.getNamedItem(name);
        return namedItem == null ? null : namedItem.getNodeValue();
    }

    public static boolean getBooleanAttributeValue(Node node, String name) {
        return "true".equalsIgnoreCase(getAttributeValue(node, name));
    }

    public static String getNodeOrAttributeTagContent(Node node, String tagName) {
        String tagContent = lookupNodeTextContent(node, tagName);
        if (tagContent == null)
            tagContent = getAttributeValue(node, tagName);
        return tagContent;
    }

    public static Node lookupOrCreateNode(Node parent, String xpath) {
        Node node = lookupNode(parent, xpath);
        if (node == null)
            node = createElement(parent, xpath);
        return node;
    }

    public static Element createElement(Node parent, String tagName) {
        return createElement(parent.getOwnerDocument(), tagName);
    }

    public static Element createElement(Document document, String tagName) {
        return document.createElement(tagName);
    }


    public static Node lookupOrCreateAndAppendNode(Node parent, String xpath, boolean... lineFeeds) {
        Node node = lookupNode(parent, xpath);
        if (node == null)
            node = createAndAppendElement(parent, xpath, lineFeeds);
        return node;
    }

    public static Element createAndAppendElement(Node parentElement, String xpath, boolean... lineFeeds) {
        return createAndAddElement(parentElement, xpath, true, lineFeeds);
    }

    public static Element createAndAddElement(Node parentElement, String xpath, boolean append, boolean... lineFeeds) {
        int p = xpath.lastIndexOf('/');
        Element element = createElement(parentElement, xpath.substring(p + 1));
        Node parentNode = p <= 1 ? parentElement : lookupOrCreateAndAppendNode(parentElement, xpath.substring(0, p), lineFeeds);
        int lineFeedIndex = 0;
        for (Node n = parentNode; n != null && n != parentElement; n = n.getParentNode())
            lineFeedIndex++;
        boolean linefeed = lineFeedIndex < lineFeeds.length && lineFeeds[lineFeedIndex];
        if (append)
            appendIndentNode(element, parentNode, linefeed);
        else
            prependIndentNode(element, parentNode, linefeed);
        return element;
    }

    public static Element appendElementWithTextContent(Node parentNode, String xpath, String text, boolean... lineFeeds) {
        Element element = createAndAppendElement(parentNode, xpath, lineFeeds);
        element.setTextContent(text);
        return element;
    }

    public static Element appendElementWithTextContentIfNotAlreadyExists(Node parentNode, String xpath, String text, boolean... linefeeds) {
        Element element = (Element) lookupNodeWithTextContent(parentNode, xpath, text);
        if (element == null)
            element = appendElementWithTextContent(parentNode, xpath, text, linefeeds);
        return element;
    }

    public static Element createAndAppendElementWithAttribute(Node parentNode, String xpath, String attribute, String value, boolean... lineFeeds) {
        Element element = createAndAppendElement(parentNode, xpath, lineFeeds);
        element.setAttribute(attribute, value);
        return element;
    }

    public static Element appendElementWithAttributeIfNotAlreadyExists(Node parentNode, String xpath, String attribute, String value, boolean... lineFeeds) {
        Element element = lookupElementWithAttributeValue(parentNode, xpath, attribute, value);
        if (element == null)
            element = createAndAppendElementWithAttribute(parentNode, xpath, attribute, value, lineFeeds);
        return element;
    }

    public static Element prependElementWithTextContentIfNotAlreadyExists(Node parentNode, String xpath, String text, boolean... lineFeeds) {
        Element element = (Element) lookupNodeWithTextContent(parentNode, xpath, text);
        if (element == null)
            element = prependElementWithTextContent(parentNode, xpath, text, lineFeeds);
        return element;
    }

    public static Element createAndPrependElement(Node parentElement, String xpath, boolean... lineFeeds) {
        return createAndAddElement(parentElement, xpath, false, lineFeeds);
    }

    public static Element prependElementWithTextContent(Node parentNode, String xpath, String text, boolean... lineFeeds) {
        Element element = XmlUtil.createAndPrependElement(parentNode, xpath, lineFeeds);
        element.setTextContent(text);
        return element;
    }

    public static void prependNode(Node node, Node parentNode) {
        Node firstChild = parentNode.getFirstChild();
        if (firstChild == null)
            parentNode.appendChild(node);
        else
            parentNode.insertBefore(node, firstChild);
    }

    public static void removeNode(Node node) {
        Node parentNode = node == null ? null : node.getParentNode();
        if (parentNode != null)
            parentNode.removeChild(node);
    }

    public static void removeChildren(Node node) {
        while (node.hasChildNodes())
            node.removeChild(node.getFirstChild());
    }

    public static void appendChildren(Node parentSource, Node parentDestination) {
        while (parentSource.hasChildNodes())
            parentDestination.appendChild(parentSource.getFirstChild());
    }

    private final static int INDENT_SPACES = 4; // 4 spaces indent per depth level

    public static <T extends Node> T appendIndentNode(T node, Node parentNode, boolean linefeed) {
        int existingLineFeedsBefore = countLineFeedsBefore(parentNode.getLastChild());
        int requiredLineFeedsBefore = linefeed ? 2 : 1;
        Document document = parentNode.getOwnerDocument();
        parentNode.appendChild(createIndentText(requiredLineFeedsBefore - existingLineFeedsBefore, document));
        parentNode.appendChild(node);
        parentNode.appendChild(createIndentText(requiredLineFeedsBefore, document));
        indentNode(parentNode, true);
        return node;
    }

    public static void prependIndentNode(Node node, Node parentNode, boolean linefeed) {
        int existingLineFeedsAfter = countLineFeedsAfter(parentNode.getFirstChild());
        int requiredLineFeedsAfter = linefeed ? 2 : 1;
        Document document = parentNode.getOwnerDocument();
        prependNode(createIndentText(requiredLineFeedsAfter - existingLineFeedsAfter, document), parentNode);
        prependNode(node, parentNode);
        prependNode(createIndentText(requiredLineFeedsAfter, document), parentNode);
        indentNode(parentNode, true);
    }

    public static int getNodeDepthLevel(Node node) {
        int depthLevel = -1; // level 0 refers to the document element, -1 to the document
        for (Node p = node.getParentNode(); p != null; p = p.getParentNode())
            depthLevel++;
        return depthLevel;
    }

    public static void indentNode(Node node, boolean recursively) {
        indentNode(node, getNodeDepthLevel(node), recursively);
    }

    public static void indentNode(Node node, int level, boolean recursively) {
        if (level < 0)
            return;
        int existingSpacesBefore = countSpacesBefore(node);
        int requiredSpacesBefore = level * INDENT_SPACES;
        int deltaSpacesBefore = requiredSpacesBefore - existingSpacesBefore;
        if (deltaSpacesBefore != 0 || recursively) {
            addSpacesBefore(node, deltaSpacesBefore);
            NodeList childNodes = node.getChildNodes();
            int n = childNodes.getLength();
            if (n > 0) {
                for (int i = 0; i < n; i++) {
                    Node childNode = childNodes.item(i);
                    if (!recursively)
                        addSpacesBefore(childNode, deltaSpacesBefore);
                    else if (!(childNode instanceof Text))
                        indentNode(childNode, level + 1, true);
                    else if (deltaSpacesBefore > 0) {
                        // Shifting all lines of non-blank texts (such as html, GraalVM json) to keep a homogeneous
                        // indent along the text block. Without doing that, `webfx update` may generate a different
                        // indent when generating index.html or reflection.json depending on if the info is taken from
                        // the local webfx.xml or the exported webfx.xml.
                        Text text = (Text) childNode;
                        String textContent = text.getTextContent();
                        if (!textContent.isBlank()) {
                            textContent = textContent.replace("\n", "\n" + " ".repeat(deltaSpacesBefore));
                            text.setTextContent(textContent);
                        }
                    }
                }
                if (n > 1) {
                    Node lastChild = node.getLastChild();
                    if (lastChild instanceof Text) {
                        String lastContent = lastChild.getTextContent();
                        if (!lastContent.contains("\n"))
                            lastChild.setTextContent(lastContent + "\n");
                    } else
                        node.appendChild(lastChild = node.getOwnerDocument().createTextNode("\n"));
                    indentNode(lastChild, level, false);
                }
            }
        }
    }

    private static void addSpacesBefore(Node node, int spacesCount) {
        if (spacesCount != 0) {
            Text textToAddSpacesTo;
            Node previousNode = getPreviousSiblingIfNotText(node);
            if (previousNode instanceof Text)
                textToAddSpacesTo = (Text) previousNode;
            else
                node.getParentNode().insertBefore(textToAddSpacesTo = node.getOwnerDocument().createTextNode(""), node);
            String textContent = textToAddSpacesTo.getTextContent();
            if (spacesCount > 0) {
                String extraSpaces = " ".repeat(spacesCount);
                textContent = textToAddSpacesTo == node && !textContent.contains("\n") ? extraSpaces + textContent : textContent + extraSpaces;
            } else
                while (spacesCount++ < 0 && textContent.endsWith(" "))
                    textContent = textContent.substring(0, textContent.length() - 2);
            textToAddSpacesTo.setTextContent(textContent);
        }
    }

    private static int countSpacesBefore(Node node) {
        return countTextWhitespaces(getPreviousSiblingIfNotText(node), false, false);
    }

    private static int countLineFeedsBefore(Node node) {
        return countTextWhitespaces(getPreviousSiblingIfNotText(node), true, false);
    }

    private static int countLineFeedsAfter(Node node) {
        return countTextWhitespaces(getNextSiblingIfNotText(node), true, true);
    }

    private static Node getPreviousSiblingIfNotText(Node node) {
        if (node != null && !(node instanceof Text))
            node = node.getPreviousSibling();
        return node;
    }

    private static Node getNextSiblingIfNotText(Node node) {
        if (node != null && !(node instanceof Text))
            node = node.getNextSibling();
        return node;
    }

    private static int countTextWhitespaces(Node fromNode, boolean lineFeeds, boolean forward) {
        int count = 0;
        loop: while (fromNode instanceof Text) {
            String textContent = fromNode.getTextContent();
            for (int j = textContent.length() - 1; j >= 0; j--) {
                char c = textContent.charAt(j);
                if (c == (lineFeeds ? '\n' : ' '))
                    count++;
                else if (!lineFeeds)
                    break loop;
                else if (!Character.isWhitespace(c))
                    if (forward)
                        break loop;
                    else
                        count = 0;
            }
            if (forward)
                fromNode = fromNode.getNextSibling();
            else
                fromNode = fromNode.getPreviousSibling();
        }
        return count;
    }

    private static Text createIndentText(int linefeedBefore, Document document) {
        StringBuilder sb = new StringBuilder();
        if (linefeedBefore > 0)
            sb.append("\n".repeat(linefeedBefore));
        return document.createTextNode(sb.toString());
    }
}
