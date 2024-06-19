package dev.webfx.cli.util.xml;

import dev.webfx.lib.reusablestream.ReusableStream;
import org.dom4j.Element;
import org.dom4j.Node;

import java.util.List;

public interface XmlNodeApi {

    Element getXmlNode();

    default Element getOrCreateXmlNode() {
        return getXmlNode();
    }

    default String getXmlContent() {
        return XmlUtil.formatXmlText(getOrCreateXmlNode());
    }

    default List<Node> lookupNodeList(String xpathExpression) {
        return XmlUtil.lookupNodeList(getXmlNode(), xpathExpression);
    }

    default List<Element> lookupElementList(String xpathExpression) {
        return (List<Element>) (List) XmlUtil.lookupNodeList(getXmlNode(), xpathExpression);
    }

    default Node lookupNode(String xpathExpression) {
        return XmlUtil.lookupNode(getXmlNode(), xpathExpression);
    }

    default Element lookupElement(String xpathExpression) {
        return (Element) XmlUtil.lookupNode(getXmlNode(), xpathExpression);
    }

    default Element lookupOrCreateNode(String xpath) {
        return XmlUtil.lookupOrCreateNode(getOrCreateXmlNode(), xpath);
    }

    default Node lookupOrCreateAndAppendNode(String xpath, boolean... linefeeds) {
        return XmlUtil.lookupOrCreateAndAppendNode(getOrCreateXmlNode(), xpath, linefeeds);
    }

    default Element createAndAppendElement(String xpath, boolean... linefeeds) {
        return XmlUtil.createAndAppendElement(getOrCreateXmlNode(), xpath, linefeeds);
    }

    default Element appendElementWithTextContentIfNotAlreadyExists(String xpath, String text, boolean... linefeeds) {
        return XmlUtil.appendElementWithTextContentIfNotAlreadyExists(getOrCreateXmlNode(), xpath, text, linefeeds);
    }

    default Element prependElementWithTextContentIfNotAlreadyExists(String xpath, String text, boolean... linefeeds) {
        return XmlUtil.prependElementWithTextContentIfNotAlreadyExists(getOrCreateXmlNode(), xpath, text, linefeeds);
    }

    default Node lookupNodeWithTextContent(String xpath, String text) {
        return XmlUtil.lookupNodeWithTextContent(getXmlNode(), xpath, text);
    }

    default void appendIndentNode(Node node, boolean linefeed) {
        XmlUtil.appendIndentNode(node, getOrCreateXmlNode(), linefeed);
    }

    default void prependIndentNode(Node node, boolean linefeed) {
        XmlUtil.prependIndentNode(node, getOrCreateXmlNode(), linefeed);
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
        return XmlUtil.nodeListToTextContentReusableStream(lookupElementList(xPathExpression));
    }

}
