package dev.webfx.cli.util.xml;

import dev.webfx.cli.util.stopwatch.StopWatch;
import dev.webfx.cli.util.textfile.TextFileReaderWriter;
import dev.webfx.lib.reusablestream.ReusableStream;
import org.dom4j.*;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.XMLWriter;
import org.dom4j.xpath.DefaultXPath;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.function.Function;

/**
 * @author Bruno Salmon
 */
public final class XmlUtil {

    public static final StopWatch XML_PARSING_STOPWATCH = StopWatch.createSystemNanoStopWatch();

    public static final StopWatch XML_FORMATING_STOPWATCH = StopWatch.createSystemNanoStopWatch();

    public static final StopWatch XPATH_EVALUATING_STOPWATCH = StopWatch.createSystemNanoStopWatch();


    public static Document newDocument() {
        return DocumentHelper.createDocument();
    }

    public static Document parseXmlFile(File xmlFile) {
        return parseXmlString(TextFileReaderWriter.readInputTextFile(xmlFile.toPath()));
    }

    public static Document parseXmlString(String xmlString) {
        if (xmlString == null)
            return null;
        XML_PARSING_STOPWATCH.on();
        xmlString = xmlString.replaceAll("xmlns\\s*=\\s*\"[^\"]*\"", "");
        xmlString = xmlString.replaceAll("xmlns:xsi\\s*=\\s*\"[^\"]*\"", "");
        xmlString = xmlString.replaceAll("xsi:schemaLocation\\s*=\\s*\"[^\"]*\"", "");
        try {
            return DocumentHelper.parseText(xmlString);
        } catch (DocumentException e) {
            throw new RuntimeException(e);
        } finally {
            XML_PARSING_STOPWATCH.off();
        }
    }

    public static String formatXmlText(Node node) {
        XML_FORMATING_STOPWATCH.on();
        String xmlText = node.asXML();
        if (xmlText.contains("?>\n<!--")) // xml declaration followed by a comment
            xmlText = xmlText.replace("--><", "-->\n<"); // Adding linefeed after the end of comment if missing
        XML_FORMATING_STOPWATCH.off();
        return xmlText;
    }

    private final static OutputFormat HTML_FORMAT = OutputFormat.createPrettyPrint();
    static {
        HTML_FORMAT.setExpandEmptyElements(true);
        HTML_FORMAT.setIndent(false);
        HTML_FORMAT.setNewlines(false);
        HTML_FORMAT.setTrimText(false);
    }

    public static String formatHtmlText(Node node) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            XMLWriter writer = new XMLWriter(baos, HTML_FORMAT) {
                @Override
                protected void writeNodeText(Node node) throws IOException {
                    super.writeNodeText(node);
                }
            };
            writer.write(node);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return baos.toString();
    }

    private static final Map<String, XPath> XPATH_CACHE = new HashMap<>();

    public static List<Node> lookupNodeList(Node node, String xpathExpression) {
        if (node == null)
            return Collections.emptyList();
        XPath xPath = getOrCreateXPath(xpathExpression);
        return xPath.selectNodes(node);
    }

    private static XPath getOrCreateXPath(String xpathExpression) {
        XPath xPath = XPATH_CACHE.get(xpathExpression);
        if (xPath == null)
            XPATH_CACHE.put(xpathExpression, xPath = new DefaultXPath(xpathExpression));
        return xPath;
    }

    public static List<Element> lookupElementList(Node node, String xpathExpression) {
        return (List<Element>) (List) lookupNodeList(node, xpathExpression);
    }

    public static Node lookupNode(Element parent, String xpathExpression) {
        if (parent == null)
            return null;
        XPath xPath = getOrCreateXPath(xpathExpression);
        return xPath.selectSingleNode(parent);
    }

    public static Element lookupElement(Element parent, String xpathExpression) {
        return (Element) lookupNode(parent, xpathExpression);
    }

    public static String lookupNodeTextContent(Element parent, String xpathExpression) {
        Node node = lookupNode(parent, xpathExpression);
        return node == null ? null : node.getText();
    }

    public static Node lookupNodeWithTextContent(Element parent, String xpath, String text) {
        if (text.indexOf('\'') == -1) // General case when the text doesn't contain single quotes
            return lookupNode(parent, xpath + "[text() = '" + text + "']");
        // When the text contains single quotes, we need to escape them
        return lookupNode(parent, xpath + "[text() = concat('" + text.replace("'", "',\"'\",'") + "', '')]");
    }

    public static Element lookupElementWithAttributeValue(Element parent, String xpath, String attribute, String value) {
        return (Element) lookupNode(parent, xpath + "[@" + attribute + "='" + value + "']");
    }

    public static ReusableStream<Element> nodeListToNodeReusableStream(List<Element> nodeList) {
        return nodeListToReusableStream(nodeList, node -> node);
    }

    public static ReusableStream<String> nodeListToTextContentReusableStream(List<Element> nodeList) {
        return nodeListToReusableStream(nodeList, Node::getText);
    }

    public static ReusableStream<String> nodeListToAttributeValueReusableStream(List<Element> nodeList, String attribute) {
        return nodeListToReusableStream(nodeList, n -> XmlUtil.getAttributeValue(n, attribute));
    }

    public static <T, N extends Node> ReusableStream<T> nodeListToReusableStream(List<N> nodeList, Function<N, T> transformer) {
        if (nodeList == null)
            return ReusableStream.empty();
        List<T> tList = dev.webfx.platform.util.collection.Collections.map(nodeList, transformer::apply);
        return ReusableStream.fromIterable(tList);
    }

    public static List<String> nodeListToTextContentList(List<Node> nodeList) {
        return nodeListToList(nodeList, Node::getText);
    }

    public static List<String> nodeListToAttributeValueList(List<Element> nodeList, String attribute) {
        return nodeListToList(nodeList, n -> XmlUtil.getAttributeValue(n, attribute));
    }

    public static <T, N extends Node> List<T> nodeListToList(List<N> nodeList, Function<N, ? extends T> transformer) {
        if (nodeList == null)
            return Collections.emptyList();
        int n = nodeList.size();
        List<T> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++)
            list.add(transformer.apply(nodeList.get(i)));
        return list;
    }

    public static String getAttributeValue(Element node, String name) {
        if (node == null)
            return null;
        return node.attributeValue(name);
    }

    public static boolean getBooleanAttributeValue(Element node, String name) {
        return "true".equalsIgnoreCase(getAttributeValue(node, name));
    }

    public static String getNodeOrAttributeTagContent(Element node, String tagName) {
        String tagContent = lookupNodeTextContent(node, tagName);
        if (tagContent == null)
            tagContent = getAttributeValue(node, tagName);
        return tagContent;
    }

    public static Element lookupOrCreateNode(Element parent, String xpath) {
        Element node = (Element) lookupNode(parent, xpath);
        if (node == null)
            node = createElement(xpath, parent);
        return node;
    }

    public static Element createElement(String tagName, Element parentNode) {
        if (parentNode == null)
            return DocumentHelper.createElement(tagName);
        QName qName = QName.get(tagName, parentNode.getNamespace());
        return DocumentHelper.createElement(qName);
    }

    public static Element lookupOrCreateAndAppendNode(Element parent, String xpath, boolean... lineFeeds) {
        Node node = lookupNode(parent, xpath);
        if (node == null)
            node = createAndAppendElement(parent, xpath, lineFeeds);
        return (Element) node;
    }

    public static Element createAndAppendElement(Element parentElement, String xpath, boolean... lineFeeds) {
        return createAndAddElement(parentElement, xpath, true, lineFeeds);
    }

    public static Element createAndAddElement(Element parentElement, String xpath, boolean append, boolean... lineFeeds) {
        int p = xpath.lastIndexOf('/');
        Element element = createElement(xpath.substring(p + 1), parentElement);
        Element parentNode = p <= 1 ? parentElement : lookupOrCreateAndAppendNode(parentElement, xpath.substring(0, p), lineFeeds);
        int lineFeedIndex = 0;
        for (Node n = parentNode; n != null && n != parentElement; n = n.getParent())
            lineFeedIndex++;
        boolean linefeed = lineFeedIndex < lineFeeds.length && lineFeeds[lineFeedIndex];
        if (append)
            appendIndentNode(element, parentNode, linefeed);
        else
            prependIndentNode(element, parentNode, linefeed);
        return element;
    }

    public static Element appendElementWithTextContent(Element parentNode, String xpath, String text, boolean... lineFeeds) {
        Element element = createAndAppendElement(parentNode, xpath, lineFeeds);
        element.setText(text);
        return element;
    }

    public static Element appendElementWithTextContentIfNotAlreadyExists(Element parentNode, String xpath, String text, boolean... linefeeds) {
        Element element = (Element) lookupNodeWithTextContent(parentNode, xpath, text);
        if (element == null)
            element = appendElementWithTextContent(parentNode, xpath, text, linefeeds);
        return element;
    }

    public static Element createAndAppendElementWithAttribute(Element parentNode, String xpath, String attribute, String value, boolean... lineFeeds) {
        Element element = createAndAppendElement(parentNode, xpath, lineFeeds);
        element.addAttribute(attribute, value);
        return element;
    }

    public static Element appendElementWithAttributeIfNotAlreadyExists(Element parentNode, String xpath, String attribute, String value, boolean... lineFeeds) {
        Element element = lookupElementWithAttributeValue(parentNode, xpath, attribute, value);
        if (element == null)
            element = createAndAppendElementWithAttribute(parentNode, xpath, attribute, value, lineFeeds);
        return element;
    }

    public static Element prependElementWithTextContentIfNotAlreadyExists(Element parentNode, String xpath, String text, boolean... lineFeeds) {
        Element element = (Element) lookupNodeWithTextContent(parentNode, xpath, text);
        if (element == null)
            element = prependElementWithTextContent(parentNode, xpath, text, lineFeeds);
        return element;
    }

    public static Element createAndPrependElement(Element parentElement, String xpath, boolean... lineFeeds) {
        return createAndAddElement(parentElement, xpath, false, lineFeeds);
    }

    public static Element prependElementWithTextContent(Element parentNode, String xpath, String text, boolean... lineFeeds) {
        Element element = XmlUtil.createAndPrependElement(parentNode, xpath, lineFeeds);
        element.setText(text);
        return element;
    }

    private static Node getFirstChild(Element parentNode) {
        return dev.webfx.platform.util.collection.Collections.first(parentNode.content());
    }

    private static Node getLastChild(Element parentNode) {
        return dev.webfx.platform.util.collection.Collections.last(parentNode.content());
    }

    public static void prependNode(Node node, Element parentNode) {
        Node firstChild = getFirstChild(parentNode);
        insertBefore(node, parentNode, firstChild);
    }

    public static void insertBefore(Node node, Element parentNode, Node before) {
        if (before == null)
            parentNode.add(node);
        else
            parentNode.content().add(parentNode.indexOf(before), node);
    }

    public static void removeNode(Node node) {
        Element parentNode = node == null ? null : node.getParent();
        if (parentNode != null)
            parentNode.remove(node);
    }

    public static void removeChildren(Branch node) {
        node.clearContent();
    }

    public static void appendChildren(Element parentSource, Element parentDestination) {
        // Move each child element from the source parent to the destination parent
        for (Element child : parentSource.elements()) {
            // Detach the child element from the source parent
            parentSource.remove(child);
            // Add the detached child element to the destination parent
            parentDestination.add(child);
        }
    }

    private final static int INDENT_SPACES = 4; // 4 spaces indent per depth level

    public static <T extends Node> T appendIndentNode(T node, Element parentNode, boolean linefeed) {
        int existingLineFeedsBefore = countLineFeedsBefore(getLastChild(parentNode));
        int requiredLineFeedsBefore = linefeed ? 2 : 1;
        //Document document = parentNode.getOwnerDocument();
        if (requiredLineFeedsBefore > existingLineFeedsBefore)
            parentNode.add(createIndentText(requiredLineFeedsBefore - existingLineFeedsBefore));
        parentNode.add(node);
        parentNode.add(createIndentText(requiredLineFeedsBefore));
        indentNode(parentNode, true);
        return node;
    }

    public static void prependIndentNode(Node node, Element parentNode, boolean linefeed) {
        int existingLineFeedsAfter = countLineFeedsAfter(getFirstChild(parentNode));
        int requiredLineFeedsAfter = linefeed ? 2 : 1;
        //Document document = parentNode.getOwnerDocument();
        if (requiredLineFeedsAfter > existingLineFeedsAfter)
            prependNode(createIndentText(requiredLineFeedsAfter - existingLineFeedsAfter), parentNode);
        prependNode(node, parentNode);
        prependNode(createIndentText(requiredLineFeedsAfter), parentNode);
        indentNode(parentNode, true);
    }

    public static int getNodeDepthLevel(Node node) {
        int depthLevel = 0; // level 0 refers to the document element, -1 to the document
        for (Node p = node.getParent(); p != null; p = p.getParent())
            depthLevel++;
        return depthLevel;
    }

    public static void indentNode(Branch node, boolean recursively) {
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
            Element element = node instanceof Element ? (Element) node : null;
            List<Node> childNodes = element != null ? element.content() : null;
            int n = childNodes != null ? childNodes.size() : 0;
            if (n > 0) {
                for (int i = 0; i < n; i++) {
                    Node childNode = childNodes.get(i);
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
                        String textContent = text.getText();
                        if (!textContent.isBlank()) {
                            textContent = textContent.replace("\n", "\n" + " ".repeat(deltaSpacesBefore));
                            text.setText(textContent);
                        }
                    }
                }
                if (n > 1) {
                    Node lastChild = getLastChild(element);
                    if (lastChild instanceof Text) {
                        String lastContent = lastChild.getText();
                        if (!lastContent.contains("\n"))
                            lastChild.setText(lastContent + "\n");
                    } else
                        element.add(lastChild = DocumentHelper.createText("\n"));
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
                insertBefore(textToAddSpacesTo = DocumentHelper.createText(""), node.getParent(), node);
            String textContent = textToAddSpacesTo.getText();
            if (spacesCount > 0) {
                String extraSpaces = " ".repeat(spacesCount);
                textContent = textToAddSpacesTo == node && !textContent.contains("\n") ? extraSpaces + textContent : textContent + extraSpaces;
            } else
                while (spacesCount++ < 0 && textContent.endsWith(" "))
                    textContent = textContent.substring(0, textContent.length() - 2);
            textToAddSpacesTo.setText(textContent);
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
        if (node != null && !(node instanceof Text)) {
            node = getPreviousSibling(node);
        }
        return node;
    }

    public static Node getPreviousSibling(Node node) {
        return getSibling(node, -1);
    }

    public static Node getNextSibling(Node node) {
        return getSibling(node, +1);
    }

    private static Node getSibling(Node node, int delta) {
        Element parent = node.getParent();
        if (parent == null)
            return null;
        List<Node> siblings = parent.content();
        // getting previous sibling
        return dev.webfx.platform.util.collection.Collections.get(siblings, siblings.indexOf(node) + delta);
    }

    private static Node getNextSiblingIfNotText(Node node) {
        if (node != null && !(node instanceof Text)) {
            node = getNextSibling(node);
        }
        return node;
    }

    private static int countTextWhitespaces(Node fromNode, boolean lineFeeds, boolean forward) {
        int count = 0;
        loop: while (fromNode instanceof Text) {
            String textContent = fromNode.getText();
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
                fromNode = getNextSibling(fromNode);
            else
                fromNode = getPreviousSibling(fromNode);
        }
        return count;
    }

    private static Text createIndentText(int linefeedBefore) {
        StringBuilder sb = new StringBuilder();
        if (linefeedBefore > 0)
            sb.append("\n".repeat(linefeedBefore));
        return DocumentHelper.createText(sb.toString());
    }
}
