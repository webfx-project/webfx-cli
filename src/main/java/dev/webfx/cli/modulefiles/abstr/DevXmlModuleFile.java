package dev.webfx.cli.modulefiles.abstr;

import org.dom4j.Document;

public interface DevXmlModuleFile extends PathBasedXmlModuleFile, DevModuleFile {

    default boolean updateAndWrite() {
        if (getModuleFilePath() == null)
            return false;
        boolean recreate = recreateOnUpdateAndWrite();
        if (recreate)
            createDocument(); // The document is created AND UPDATED (so no need to call updateDocument() a second time)
        Document document = getDocument();
        if (document != null && (recreate || updateDocument(document)))
            return writeFile();
        return false;
    }

    default boolean recreateOnUpdateAndWrite() {
        return false;
    }

}
