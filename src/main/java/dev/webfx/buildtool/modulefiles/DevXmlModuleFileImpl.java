package dev.webfx.buildtool.modulefiles;

import dev.webfx.buildtool.Module;
import org.w3c.dom.Document;

import java.nio.file.Path;

/**
 * @author Bruno Salmon
 */
public class DevXmlModuleFileImpl extends XmlModuleFileImpl implements DevXmlModuleFile {

    private final Path moduleFilePath;
    private boolean readFileIfExists;


    DevXmlModuleFileImpl(Module module, Path moduleFilePath, boolean readFileIfExists) {
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
