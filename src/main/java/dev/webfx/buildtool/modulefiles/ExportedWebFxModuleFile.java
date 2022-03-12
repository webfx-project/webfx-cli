package dev.webfx.buildtool.modulefiles;

import dev.webfx.buildtool.LocalProjectModule;
import org.w3c.dom.Document;

/**
 * @author Bruno Salmon
 */
public class ExportedWebFxModuleFile extends LocalXmlModuleFileImpl {

    public ExportedWebFxModuleFile(LocalProjectModule module) {
        super(module, module.getHomeDirectory().resolve("webfx-export.xml"), false);
    }

    public void updateDocument(Document document) {
        clearDocument(document);
        getProjectModule().getRootModule().exportModules(document);
    }

}
