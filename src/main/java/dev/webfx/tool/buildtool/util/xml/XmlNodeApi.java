package dev.webfx.tool.buildtool.util.xml;

import dev.webfx.lib.reusablestream.ReusableStream;
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

    default Element appendElementWithTextContentIfNotAlreadyExists(String xpath, String text, boolean... linefeeds) {
        return XmlUtil.appendElementWithTextContentIfNotAlreadyExists(getXmlNode(), xpath, text, linefeeds);
    }

    default Element prependElementWithTextContentIfNotAlreadyExists(String xpath, String text, boolean... linefeeds) {
        return XmlUtil.prependElementWithTextContentIfNotAlreadyExists(getXmlNode(), xpath, text, linefeeds);
    }

    default Node lookupNodeWithTextContent(String xpath, String text) {
        return XmlUtil.lookupNodeWithTextContent(getXmlNode(), xpath, text);
    }

    default void appendIndentNode(Node node, boolean linefeed) {
        XmlUtil.appendIndentNode(node, getOrCreateXmlNode(), linefeed);
    }

    default Element appendElementWithTextContent(String xpath, String text, boolean... linefeeds) {
        return XmlUtil.appendElementWithTextContent(getOrCreateXmlNode(), xpath, text, linefeeds);
    }

    default Element prependElementWithTextContent(String xpath, String text, boolean... linefeeds) {
        return XmlUtil.prependElementWithTextContent(getOrCreateXmlNode(), xpath, text, linefeeds);
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

}
