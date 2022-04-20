package dev.webfx.buildtool.modulefiles.abstr;

import dev.webfx.buildtool.Module;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * @author Bruno Salmon
 */
public abstract class XmlModuleFileImpl extends ModuleFileImpl implements XmlModuleFile {

    protected Document document;
    protected Element moduleElement;

    XmlModuleFileImpl(Module module) {
        super(module);
    }

    public XmlModuleFileImpl(Module module, Document document) {
        this(module);
        this.document = document;
        moduleElement = document == null ? null : document.getDocumentElement();
    }

    public XmlModuleFileImpl(Module module, Element moduleElement) {
        this(module);
        this.moduleElement = moduleElement;
        this.document = moduleElement == null ? null : moduleElement.getOwnerDocument();
    }

    public Document getDocument() {
        return document;
    }

    @Override
    public void setDocument(Document document) {
        this.document = document;
    }

    public Element getXmlNode() {
        return moduleElement != null ? moduleElement : getDocument() == null ? null : document.getDocumentElement();
    }

}
