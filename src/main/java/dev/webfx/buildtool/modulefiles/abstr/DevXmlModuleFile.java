package dev.webfx.buildtool.modulefiles.abstr;

import org.w3c.dom.Document;

public interface DevXmlModuleFile extends PathBasedXmlModuleFile, DevModuleFile {

    default void updateAndWrite() {
        if (getModuleFilePath() == null)
            return;
        boolean recreate = recreateOnUpdateAndWrite();
        if (recreate)
            createDocument(); // The document is created AND UPDATED (so no need to call updateDocument() a second time)
        Document document = getDocument();
        if (document != null && (recreate || updateDocument(document)))
            writeFile();
    }

    default boolean recreateOnUpdateAndWrite() {
        return false;
    }

}
