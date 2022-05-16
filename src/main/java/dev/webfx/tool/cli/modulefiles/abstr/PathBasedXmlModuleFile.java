package dev.webfx.tool.cli.modulefiles.abstr;

import dev.webfx.tool.cli.util.textfile.TextFileReaderWriter;
import dev.webfx.tool.cli.util.xml.XmlUtil;

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
