package dev.webfx.tool.cli.modulefiles;

import dev.webfx.tool.cli.core.Module;
import dev.webfx.tool.cli.modulefiles.abstr.WebFxModuleFile;
import dev.webfx.tool.cli.modulefiles.abstr.XmlModuleFileImpl;
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
