package dev.webfx.buildtool.modulefiles;

import dev.webfx.buildtool.ImportedProjectModule;
import org.w3c.dom.Element;

/**
 * @author Bruno Salmon
 */
public final class ImportedWebFxModuleFile extends XmlModuleFileImpl implements WebFxModuleFile {

    public ImportedWebFxModuleFile(ImportedProjectModule module, Element projectElement) {
        super(module, projectElement);
    }
}
