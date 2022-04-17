package dev.webfx.buildtool.modulefiles.abstr;

public interface DevXmlModuleFile extends PathBasedXmlModuleFile, DevModuleFile {

    default void updateAndWrite() {
        if (getModuleFilePath() == null)
            return;
        boolean recreate = recreateOnUpdateAndWrite() || getDocument() == null;
        if (recreate)
            createDocument(); // The document is created AND UPDATED (so no need to call updateDocument() a second time)
        if (recreate || updateDocument(getDocument()))
            writeFile();
    }

    default boolean recreateOnUpdateAndWrite() {
        return false;
    }

}
