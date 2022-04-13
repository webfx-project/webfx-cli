package dev.webfx.buildtool.modulefiles.abstr;

import dev.webfx.buildtool.Module;
import org.w3c.dom.Document;

import java.nio.file.Path;

/**
 * @author Bruno Salmon
 */
public abstract class PathBasedXmlModuleFileImpl extends XmlModuleFileImpl implements PathBasedXmlModuleFile {

    private final Path moduleFilePath;
    private boolean readFileIfExists;


    public PathBasedXmlModuleFileImpl(Module module, Path moduleFilePath) {
        this(module, moduleFilePath, true);
    }

    public PathBasedXmlModuleFileImpl(Module module, Path moduleFilePath, boolean readFileIfExists) {
        super(module);
        this.moduleFilePath = moduleFilePath;
        this.readFileIfExists = readFileIfExists;
    }

    @Override
    public Document getDocument() {
        if (document == null & readFileIfExists)
            readFile();
        return document;
    }

    @Override
    public void setDocument(Document document) {
        super.setDocument(document);
        readFileIfExists = false;
    }

    @Override
    public Path getModuleFilePath() {
        return moduleFilePath;
    }
}
