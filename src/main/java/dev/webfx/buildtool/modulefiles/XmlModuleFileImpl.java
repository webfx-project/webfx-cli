package dev.webfx.buildtool.modulefiles;

import dev.webfx.buildtool.Module;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * @author Bruno Salmon
 */
public abstract class XmlModuleFileImpl implements XmlModuleFile {

    private final Module module;
    protected Document document;
    protected Element moduleElement;

    XmlModuleFileImpl(Module module) {
        this(module, null);
    }

    XmlModuleFileImpl(Module module, Element moduleElement) {
        this.module = module;
        this.moduleElement = moduleElement;
        this.document = moduleElement == null ? null : moduleElement.getOwnerDocument();
    }

    @Override
    public Module getModule() {
        return module;
    }

    public Document getDocument() {
        return document;
    }

    @Override
    public void setDocument(Document document) {
        this.document = document;
    }

    public Element getModuleElement() {
        return moduleElement != null ? moduleElement : getDocument() == null ? null : document.getDocumentElement();
    }

}
