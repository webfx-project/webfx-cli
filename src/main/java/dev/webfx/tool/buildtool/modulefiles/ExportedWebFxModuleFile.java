package dev.webfx.tool.buildtool.modulefiles;

import dev.webfx.tool.buildtool.Module;
import dev.webfx.tool.buildtool.modulefiles.abstr.WebFxModuleFile;
import dev.webfx.tool.buildtool.modulefiles.abstr.XmlModuleFileImpl;
import org.w3c.dom.Element;

/**
 * WebFx module file read from resources (only used for reading the JDK modules when initializing the ModuleRegistry)
 *
 * @author Bruno Salmon
 */
public final class ExportedWebFxModuleFile extends XmlModuleFileImpl implements WebFxModuleFile {

    public ExportedWebFxModuleFile(Module module, Element exportElement) {
        super(module, exportElement);
    }

    @Override
    public boolean fileExists() {
        return true;
    }
}
