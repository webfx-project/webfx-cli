package dev.webfx.buildtool.modulefiles;

import dev.webfx.buildtool.util.textfile.TextFileReaderWriter;
import dev.webfx.buildtool.util.xml.XmlUtil;

import java.io.File;

public interface DevXmlModuleFile extends XmlModuleFile, DevModuleFile {

    default void readFile() {
        File moduleFile = getModuleFile();
        if (moduleFile != null)
            setDocument(XmlUtil.parseXmlFile(moduleFile));
    }

    default void updateAndWrite() {
        boolean recreate = recreateOnUpdateAndWrite() || getDocument() == null;
        if (recreate)
            createDocument(); // The document is created AND UPDATED (so no need to call updateDocument() a second time)
        if (recreate || updateDocument(getDocument()))
            writeFile();
    }

    default boolean recreateOnUpdateAndWrite() {
        return false;
    }

    default void writeFile() {
        TextFileReaderWriter.writeTextFileIfNewOrModified(getXmlContent(), getModuleFilePath());
    }

}
