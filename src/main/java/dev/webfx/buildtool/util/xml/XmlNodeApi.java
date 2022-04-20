package dev.webfx.buildtool.util.xml;

import dev.webfx.tools.util.reusablestream.ReusableStream;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public interface XmlNodeApi {

    Node getXmlNode();

    default Node getOrCreateXmlNode() {
        return getXmlNode();
    }

    default String getXmlContent() {
        return XmlUtil.formatXmlText(getOrCreateXmlNode());
    }

    default NodeList lookupNodeList(String xpathExpression) {
        return XmlUtil.lookupNodeList(getXmlNode(), xpathExpression);
    }

    default Node lookupNode(String xpathExpression) {
        return XmlUtil.lookupNode(getXmlNode(), xpathExpression);
    }

    default Node lookupOrCreateNode(String xpath) {
        return XmlUtil.lookupOrCreateNode(getOrCreateXmlNode(), xpath);
    }

    default Node lookupOrCreateAndAppendNode(String xpath, boolean... linefeeds) {
        return XmlUtil.lookupOrCreateAndAppendNode(getOrCreateXmlNode(), xpath, linefeeds);
    }

    default Element createAndAppendElement(String xpath, boolean... linefeeds) {
        return XmlUtil.createAndAppendElement(getOrCreateXmlNode(), xpath, linefeeds);
    }

    default void appendTextNodeIfNotAlreadyExists(String xpath, String text, boolean... linefeeds) {
        XmlUtil.appendTextNodeIfNotAlreadyExists(getXmlNode(), xpath, text, linefeeds);
    }

    default void prependTextNodeIfNotAlreadyExists(String xpath, String text, boolean... linefeeds) {
        XmlUtil.prependTextNodeIfNotAlreadyExists(getXmlNode(), xpath, text, linefeeds);
    }

    default Node lookupTextNode(String xpath, String text) {
        return XmlUtil.lookupTextNode(getXmlNode(), xpath, text);
    }

    default void appendIndentNode(Node node, boolean linefeed) {
        XmlUtil.appendIndentNode(node, getOrCreateXmlNode(), linefeed);
    }

    default Element appendTextElement(String xpath, String text, boolean... linefeeds) {
        return XmlUtil.appendTextElement(getOrCreateXmlNode(), xpath, text, linefeeds);
    }

    default Element prependTextElement(String xpath, String text, boolean... linefeeds) {
        return XmlUtil.prependTextElement(getOrCreateXmlNode(), xpath, text, linefeeds);
    }

    default Element createAndPrependElement(String xpath, boolean... linefeeds) {
        return XmlUtil.createAndPrependElement(getOrCreateXmlNode(), xpath, linefeeds);
    }

    default String lookupNodeTextContent(String xpathExpression) {
        return XmlUtil.lookupNodeTextContent(getXmlNode(), xpathExpression);
    }

    default ReusableStream<String> lookupNodeListTextContent(String xPathExpression) {
        return XmlUtil.nodeListToTextContentReusableStream(lookupNodeList(xPathExpression));
    }

    default ReusableStream<String> lookupNodeListAttribute(String xPathExpression, String attribute) {
        return XmlUtil.nodeListToReusableStream(lookupNodeList(xPathExpression), node -> XmlUtil.getAttributeValue(node, attribute));
    }

    default boolean getBooleanAttributeValue(String name) {
        return XmlUtil.getBooleanAttributeValue(getXmlNode(), name);
    }

}
