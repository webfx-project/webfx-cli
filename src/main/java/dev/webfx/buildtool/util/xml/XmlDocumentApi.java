package dev.webfx.buildtool.util.xml;

import org.w3c.dom.Document;
import org.w3c.dom.Node;

public interface XmlDocumentApi extends XmlNodeApi {

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

    default Node getOrCreateXmlNode() {
        Node moduleElement = getXmlNode();
        return moduleElement != null ? moduleElement : getOrCreateDocument().getDocumentElement();
    }

    default boolean updateDocument(Document document) {
        return false;
    }

    default String getXmlContent() {
        return XmlUtil.formatXmlText(getOrCreateDocument());
    }

}
