package dev.webfx.buildtool.modulefiles.abstr;

import dev.webfx.buildtool.util.textfile.TextFileReaderWriter;
import dev.webfx.buildtool.util.xml.XmlUtil;

import java.io.File;

/**
 * @author Bruno Salmon
 */
public interface PathBasedXmlModuleFile extends PathBasedModuleFile, XmlModuleFile {

    default void readFile() {
        File moduleFile = getModuleFile();
        if (moduleFile != null)
            setDocument(XmlUtil.parseXmlFile(moduleFile));
    }

    default void writeFile() {
        TextFileReaderWriter.writeTextFileIfNewOrModified(getXmlContent(), getModuleFilePath());
    }

}
