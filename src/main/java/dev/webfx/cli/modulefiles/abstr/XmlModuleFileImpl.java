package dev.webfx.cli.modulefiles.abstr;

import dev.webfx.cli.core.Module;
import org.dom4j.Document;
import org.dom4j.Element;

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
        moduleElement = document == null ? null : document.getRootElement();
    }

    public XmlModuleFileImpl(Module module, Element moduleElement) {
        this(module);
        this.moduleElement = moduleElement;
        this.document = moduleElement == null ? null : moduleElement.getDocument();
    }

    public Document getDocument() {
        return document;
    }

    @Override
    public void setDocument(Document document) {
        this.document = document;
    }

    @Override
    public Element getModuleElement() {
        return moduleElement != null ? moduleElement : XmlModuleFile.super.getModuleElement();
    }

}
