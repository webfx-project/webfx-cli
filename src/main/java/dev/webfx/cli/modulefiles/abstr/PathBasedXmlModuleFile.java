package dev.webfx.cli.modulefiles.abstr;

import dev.webfx.cli.util.textfile.TextFileReaderWriter;
import dev.webfx.cli.util.xml.XmlUtil;
import org.dom4j.Document;

import java.io.File;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * @author Bruno Salmon
 */
public interface PathBasedXmlModuleFile extends PathBasedModuleFile, XmlModuleFile {

    // We cache the parsed xml documents. This is for webfx-libraries that may have many modules exported in the same
    // webfx.xml root file. Instead of parsing this same file many times, we cache it.
    Map<File, Document> DOCUMENTS_CACHE = new WeakHashMap<>();

    default void readFile() {
        File moduleFile = getModuleFile();
        if (moduleFile != null) {
            Document document = DOCUMENTS_CACHE.get(moduleFile);
            if (document == null)
                DOCUMENTS_CACHE.put(moduleFile, document = XmlUtil.parseXmlFile(moduleFile));
            setDocument(document);
        }
    }

    default boolean writeFile() {
        String xmlContent = getXmlContent();
        if (xmlContent != null)
            TextFileReaderWriter.writeTextFileIfNewOrModified(xmlContent, getModuleFilePath());
        return xmlContent != null;
    }

}
