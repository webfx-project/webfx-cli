package dev.webfx.cli.util.xml;

import org.dom4j.Document;
import org.dom4j.Element;

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

    default Element getOrCreateXmlNode() {
        Element moduleElement = getXmlNode();
        return moduleElement != null ? moduleElement : getOrCreateDocument().getRootElement();
    }

    default boolean updateDocument(Document document) {
        return false;
    }

    default String getXmlContent() {
        return XmlUtil.formatXmlText(getOrCreateDocument());
    }

}
