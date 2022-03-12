package dev.webfx.buildtool.modulefiles;

import dev.webfx.buildtool.util.textfile.TextFileReaderWriter;
import dev.webfx.buildtool.util.xml.XmlUtil;

import java.io.File;

public interface LocalXmlModuleFile extends XmlModuleFile, LocalModuleFile {

    default void readFile() {
        File moduleFile = getModuleFile();
        if (moduleFile != null)
            setDocument(XmlUtil.parseXmlFile(moduleFile));
    }

    default void updateAndWrite() {
        updateDocument(getOrCreateDocument());
        writeFile();
    }

    default void writeFile() {
        TextFileReaderWriter.writeTextFileIfNewOrModified(getXmlContent(), getModuleFilePath());
    }

}
